package com.solidsketchapp.steel.conn

import com.solidsketchapp.core.api.BodyId
import com.solidsketchapp.core.api.BooleanEngine
import com.solidsketchapp.core.api.DocumentApi
import com.solidsketchapp.core.api.Mesh
import com.solidsketchapp.steel.geom.StBuild
import com.solidsketchapp.steel.model.jsNum
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/** One `doc.stConns` entry: `{id, type, parts: [body ids], q: {…}}` (raw JSON kept). */
class StConn(val raw: JsonObject) {
    val id: String get() = (raw["id"] as JsonPrimitive).content
    val type: String get() = (raw["type"] as JsonPrimitive).content
    val parts: List<String> get() = (raw["parts"] as? JsonArray)?.map { (it as JsonPrimitive).content } ?: emptyList()
    val q: JsonObject get() = raw["q"] as? JsonObject ?: JsonObject(emptyMap())
}

/**
 * Cuts and connection parts: v69 `stApplyCuts` and `stRegen` (v34 L13969). A part's solid is its `st` solid minus
 * every cut its connections make (one boolean pass, as v69's `man.subtract(stUnion(all))`); a connection's own
 * parts (bolt groups here; component plates come with S7–S9) are made again as bodies `{kind: 'bolts', conn, …}`.
 * S6 ports `TKCUT`, `TKBOLT` (all R10 uses) and `fitting` ([TkConns]); other types are left as they are (S7–S10).
 */
class StRegen(private val build: StBuild, private val booleans: BooleanEngine, private val profiles: com.solidsketchapp.steel.data.ProfileCatalog? = null) {
    /** Parts' st by body id, for connection types that need their frames (fitting). Set by [regen] / the caller. */
    var stOf: (String) -> JsonObject? = { null }
    private val geomCache = HashMap<String, ConnGeom?>()

    fun conns(doc: DocumentApi): List<StConn> = doc.field("stConns")?.let { runCatching { Json.parseToJsonElement(it).jsonArray.map { e -> StConn(e.jsonObject) } }.getOrNull() } ?: emptyList()

    fun geom(c: StConn): ConnGeom? = geomCache.getOrPut(c.id) { runCatching { TkConns.geom(c.type, c.parts, c.q, build, DEFS[c.type] ?: JsonObject(emptyMap()), stOf, profiles) }.getOrNull() }

    /** v69 stApplyCuts: the part's solid with the cuts of every connection that lists it. */
    fun cutPart(id: String, st: JsonObject, conns: List<StConn>): Mesh {
        val m = build.build(st)
        val all = conns.filter { id in it.parts }.flatMap { geom(it)?.cuts?.get(id) ?: emptyList() }
        return if (all.isEmpty()) m else runCatching { booleans.subtract(m, all) }.getOrElse { m }
    }

    /**
     * v69 stRegen: the connections on [ids] are rebuilt: their old bolt bodies go, every part they join is rebuilt with
     * its cuts, and their bolt groups come back as new bodies (name "BOLTS <id>", colour 0x5a5f66, type stBolt).
     * Call it inside one undo group. Returns the number of rebuilt parts.
     */
    fun regen(doc: DocumentApi, ids: Collection<BodyId>): Int {
        geomCache.clear(); stOf = { id -> stOf(doc, BodyId(id)) }
        val all = conns(doc); val touched = ids.map { it.value }.toSet()
        val cs = all.filter { c -> c.parts.any { it in touched } && c.type in PORTED }
        if (cs.isEmpty()) return 0
        val cids = cs.map { it.id }.toSet()
        for (b in doc.bodyIds()) { val st = stOf(doc, b) ?: continue
            if ((str(st, "kind") == "bolts" || str(st, "kind") == "conn") && (st["conn"] as? JsonPrimitive)?.content in cids) doc.removeBody(b) }
        val parents = cs.flatMap { it.parts }.toSet()
        var n = 0
        for (b in doc.bodyIds()) {
            if (b.value !in parents) continue
            val json = doc.bodyJson(b) ?: continue; val st = stOf(doc, b) ?: continue
            val mesh = runCatching { cutPart(b.value, st, all) }.getOrNull() ?: continue
            doc.replaceBody(b, json, mesh); n++
        }
        for (c in cs) for (p in geom(c)?.parts ?: emptyList()) {
            val bolts = p.bolts ?: continue
            val man = if (p.pieces.size == 1) p.pieces[0] else runCatching { booleans.union(p.pieces) }.getOrElse { com.solidsketchapp.steel.geom.Prism.concat(p.pieces) }
            val st = JsonObject(linkedMapOf<String, JsonElement>("kind" to JsonPrimitive("bolts"), "conn" to (c.raw["id"] ?: JsonPrimitive(c.id)), "name" to JsonPrimitive(p.name),
                "prof" to JsonPrimitive("M" + com.solidsketchapp.core.api.JsNumber.format(StBuild.jsNum(bolts["d"])))).apply { putAll(bolts) })
            fun body(name: String) = JsonObject(linkedMapOf("name" to JsonPrimitive(name), "color" to JsonPrimitive(0x5A5F66), "hidden" to JsonPrimitive(false), "type" to JsonPrimitive("stBolt"), "st" to st)).toString()
            val id = doc.addBody(body(p.name), man); doc.replaceBody(id, body("${p.name} ${id.value}"), man)
        }
        return n
    }

    private fun stOf(doc: DocumentApi, id: BodyId): JsonObject? = doc.bodyJson(id)?.let { runCatching { Json.parseToJsonElement(it).jsonObject["st"] as? JsonObject }.getOrNull() }
    private fun str(o: JsonObject, k: String) = (o[k] as? JsonPrimitive)?.contentOrNull ?: ""

    companion object {
        /** CONN defaults of the ported types (v64 L17087). */
        val DEFS = mapOf("TKCUT" to JsonObject(emptyMap()), "TKBOLT" to JsonObject(mapOf("d" to jsNum(16.0), "grade" to JsonPrimitive("8.8"))), "fitting" to JsonObject(mapOf("gap" to jsNum(10.0))))
        /** Connection types whose geometry is ported (S6). Others are left as the file has them. */
        val PORTED = setOf("TKCUT", "TKBOLT", "fitting")
    }
}
