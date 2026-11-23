package com.solidsketchapp.steel.model

import com.solidsketchapp.core.api.BodyId
import com.solidsketchapp.core.api.DocumentApi
import com.solidsketchapp.core.api.JsNumber
import com.solidsketchapp.core.api.Mesh
import com.solidsketchapp.core.api.UndoApi
import com.solidsketchapp.steel.data.SteelData
import com.solidsketchapp.steel.geom.StBuild
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/** A number as JavaScript writes it in JSON (7500, not 7500.0). */
@OptIn(ExperimentalSerializationApi::class)
fun jsNum(d: Double): JsonElement = JsonUnquotedLiteral(JsNumber.format(d))
fun jsArr(vararg d: Double): JsonArray = JsonArray(d.map(::jsNum))

/**
 * Steel parts in the document: v69 `stRecord`, `stAdd`, `stModify` (v32 L13627). A part is a body
 * `{name: "<st.name> <id>", color: <class colour>, hidden: false, type: stColumn|stBeam|…, st: {…}}` with its solid.
 */
class StParts(private val data: SteelData, private val doc: DocumentApi, private val undo: UndoApi?, private val build: StBuild,
              /** S6: cuts and connection parts are rebuilt after a modify (v69 stModify → stRegen). */
              private val regen: com.solidsketchapp.steel.conn.StRegen? = null) {

    /** v69: ST_TYPE by name, else plate / BRAC / PURLIN / GIRT / COL / beam. */
    fun typeOf(st: JsonObject): String {
        val nm = (st["name"] as? JsonPrimitive)?.contentOrNull ?: ""
        data.types[nm]?.let { return it }
        return when {
            (st["kind"] as? JsonPrimitive)?.contentOrNull == "plate" -> "stPlate"
            "BRAC" in nm -> "stBrace"; "PURLIN" in nm -> "stPurlin"; "GIRT" in nm -> "stGirt"; "COL" in nm -> "stColumn"
            else -> "stBeam"
        }
    }

    /** v69: `ST_CLASS[st.cls] ?? 0x8e8e93`. */
    fun colorOf(st: JsonObject): Int {
        val c = StBuild.jsNum(st["cls"]).let { if (it == Math.rint(it)) it.toInt() else Int.MIN_VALUE }
        return data.classes[c] ?: 0x8E8E93
    }

    fun mesh(st: JsonObject): Mesh = build.build(st)

    private fun bodyJson(name: String, st: JsonObject, old: JsonObject?): String {
        val m = LinkedHashMap<String, JsonElement>()
        if (old != null) m.putAll(old)
        m["name"] = JsonPrimitive(name); m["color"] = JsonPrimitive(colorOf(st))
        if (!m.containsKey("hidden")) m["hidden"] = JsonPrimitive(false)
        m["type"] = JsonPrimitive(typeOf(st)); m["st"] = st
        return JsonObject(m).toString()
    }

    /** v69 stAdd: builds the solid first (an error leaves the document unchanged), adds the part as one undo step. */
    fun add(st: JsonObject, label: String): BodyId {
        val mesh = mesh(st)
        val nm = (st["name"] as? JsonPrimitive)?.contentOrNull ?: ""
        val run = {
            val id = doc.addBody(bodyJson(nm, st, null), mesh)
            doc.replaceBody(id, bodyJson("$nm ${id.value}", st, null), mesh)   // v69: name "<st.name> <id>"
            ensureFolder(typeOf(st)); id
        }
        return undo?.group(label, run) ?: run()
    }

    /** v69 stModify: the parts' st with [patch] applied, rebuilt; plates drop p1/p2. One undo step. */
    fun modify(ids: Collection<BodyId>, patch: Map<String, JsonElement>, label: String? = null) {
        val out = LinkedHashMap<BodyId, Pair<String, Mesh>>()
        for (id in ids) {
            val old = doc.bodyJson(id)?.let { Json.parseToJsonElement(it).jsonObject } ?: continue
            val st0 = old["st"] as? JsonObject ?: continue
            val m = LinkedHashMap<String, JsonElement>(st0); m.putAll(patch)
            if ((st0["kind"] as? JsonPrimitive)?.contentOrNull == "plate") { m.remove("p1"); m.remove("p2") }
            val st = JsonObject(m)
            val nm = (st["name"] as? JsonPrimitive)?.contentOrNull ?: ""
            out[id] = bodyJson("$nm ${id.value}", st, old) to mesh(st)
        }
        if (out.isEmpty()) return
        val run = { for ((id, v) in out) { doc.replaceBody(id, v.first, v.second); ensureFolder(typeOf(Json.parseToJsonElement(v.first).jsonObject["st"]!!.jsonObject)) }; regen?.regen(doc, out.keys); Unit }
        undo?.group(label ?: "Modify ${out.size} part${if (out.size == 1) "" else "s"}", run) ?: run()
    }

    /** v69 ensureFolder28: one folder per type, `{id: Date.now() + count, name, type, open: false}`. */
    fun ensureFolder(type: String) {
        val name = FOLDER_NAMES[type] ?: return
        val folders = doc.field("folders")?.let { Json.parseToJsonElement(it).jsonArray } ?: JsonArray(emptyList())
        if (folders.any { (it as? JsonObject)?.get("type")?.let { t -> (t as? JsonPrimitive)?.contentOrNull } == type }) return
        val f = JsonObject(linkedMapOf("id" to jsNum((System.currentTimeMillis() + folders.size).toDouble()), "name" to JsonPrimitive(name), "type" to JsonPrimitive(type), "open" to JsonPrimitive(false)))
        doc.setField("folders", JsonArray(folders + f).toString())
    }

    companion object {
        /** v69 TYPES28 names of the steel types. */
        val FOLDER_NAMES = mapOf("stColumn" to "Steel column", "stBeam" to "Steel beam", "stPurlin" to "Steel purlin", "stGirt" to "Steel girt",
            "stBrace" to "Steel bracing", "stPlate" to "Steel plate", "stBolt" to "Bolts")
    }
}
