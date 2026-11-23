package com.solidsketchapp.steel.conn

import com.solidsketchapp.core.api.BodyId
import com.solidsketchapp.core.api.BooleanEngine
import com.solidsketchapp.core.api.DocumentApi
import com.solidsketchapp.core.api.Mesh
import com.solidsketchapp.steel.geom.D3
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
class StRegen(private val build: StBuild, private val booleans: BooleanEngine, private val profiles: com.solidsketchapp.steel.data.ProfileCatalog? = null, private val tk: com.solidsketchapp.steel.data.TkCatalog? = null) {
    /** Parts' st by body id, for connection types that need their frames (fitting). Set by [regen] / the caller. */
    var stOf: (String) -> JsonObject? = { null }
    private val geomCache = HashMap<String, ConnGeom?>()
    /** S7: the live CONN of v69 (family and def after every chapter: e.g. T102t / T103t → GU, Ttc → WF); set when tk_ui.json is loaded. */
    var ui: com.solidsketchapp.steel.comp.TkUi? = null

    fun conns(doc: DocumentApi): List<StConn> = doc.field("stConns")?.let { runCatching { Json.parseToJsonElement(it).jsonArray.map { e -> StConn(e.jsonObject) } }.getOrNull() } ?: emptyList()

    /** The family of a catalog component (T<n>) whose generator is ported, else null. */
    fun famOf(type: String): String? = (ui?.conn?.get(type)?.fam ?: tk?.byId(type)?.family)?.takeIf { it in Fam.PORTED }
    /** v69 CONN def: v40 FAM_DEF[fam] + the component's own options (TK `o`) (+ v58 EP additions, in Fam.DEF). */
    fun defOf(type: String): JsonObject = ui?.conn?.get(type)?.def ?: tk?.byId(type)?.let { c -> JsonObject(LinkedHashMap(Fam.DEF[c.family] ?: JsonObject(emptyMap())).apply { putAll(c.options) }) } ?: DEFS[type] ?: JsonObject(emptyMap())
    fun ported(type: String) = type in PORTED || famOf(type) != null

    /** Every component in the document (v69 S.stConns), set by [regen]; families that look at their neighbours read it. */
    var allConns: List<StConn> = emptyList()
    fun geom(c: StConn): ConnGeom? = geomCache.getOrPut(c.id) { runCatching { TkConns.geom(c.type, c.parts, c.q, build, defOf(c.type), stOf, profiles, famOf(c.type), (c.raw["pt"] as? JsonArray)?.takeIf { it.size >= 3 }?.let { D3(StBuild.jsNum(it[0]), StBuild.jsNum(it[1]), StBuild.jsNum(it[2])) }, c, allConns) }.getOrNull() }

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
        val all = conns(doc); allConns = all; val touched = ids.map { it.value }.toSet()
        val cs = all.filter { c -> c.parts.any { it in touched } && ported(c.type) }
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
        val connsOut = LinkedHashMap<String, JsonObject>()
        for (c in cs) {
            val g = geom(c) ?: continue
            // v69 stRegen: the connection keeps its welds (len rounded) for the weld list and drawings.
            val m = LinkedHashMap(c.raw); m["welds"] = JsonArray(g.welds.map { w -> JsonObject(LinkedHashMap<String, JsonElement>().apply {
                put("name", JsonPrimitive(w.name)); put("a", jsNum(w.a)); put("len", jsNum(Math.floor(w.len + 0.5)))
                if (w.loop != null) put("loop", JsonArray(w.loop.map { com.solidsketchapp.steel.model.jsArr(it.x, it.y, it.z) }))
                w.type?.let { put("type", JsonPrimitive(it)) }; w.ws?.let { put("ws", JsonPrimitive(it)) }; w.around?.let { put("around", jsNum(it)) } }) })
            connsOut[c.id] = JsonObject(m)
            var ix = 0
            for (p in g.parts) {
                val piece = if (p.pieces.size == 1) p.pieces[0] else runCatching { booleans.union(p.pieces) }.getOrElse { com.solidsketchapp.steel.geom.Prism.concat(p.pieces) }
                val man = if (p.minus.isEmpty()) piece else runCatching { booleans.subtract(piece, p.minus) }.getOrElse { piece }
                val bolts = p.bolts
                val st: JsonObject; val color: Int; val type: String
                if (bolts != null) {
                    st = JsonObject(linkedMapOf<String, JsonElement>("kind" to JsonPrimitive("bolts"), "conn" to (c.raw["id"] ?: JsonPrimitive(c.id)), "name" to JsonPrimitive(p.name),
                        "prof" to JsonPrimitive("M" + com.solidsketchapp.core.api.JsNumber.format(StBuild.jsNum(bolts["d"])))).apply { putAll(bolts) })
                    color = 0x5A5F66; type = "stBolt"
                } else {
                    st = kidSt(c, p, owner(c, p.name, ix++)); type = "stPlate"
                    val cls = StBolts.tkNum(c.q["cls"], 1.0).toInt()
                    color = if (cls > 1) TK_CLS[cls] ?: 0x9AA0A6 else 0x9AA0A6
                }
                fun body(name: String) = JsonObject(linkedMapOf("name" to JsonPrimitive(name), "color" to JsonPrimitive(color), "hidden" to JsonPrimitive(false), "type" to JsonPrimitive(type), "st" to st)).toString()
                val id = doc.addBody(body(p.name), man); doc.replaceBody(id, body("${p.name} ${id.value}"), man)
            }
        }
        if (connsOut.isNotEmpty()) doc.setField("stConns", JsonArray(all.map { connsOut[it.id] ?: it.raw }).toString())
        return n
    }

    /** v69 tkKidSt (v58 L16010): a component plate's st (Parts tab P: name, mat, pp, ps, fin; General tab cls). */
    private fun kidSt(c: StConn, p: ConnPart, asm: String?): JsonObject {
        val pp = c.q["P"] as? JsonObject; val P = (pp?.get(p.name) ?: pp?.get("ALL")) as? JsonObject ?: JsonObject(emptyMap())
        fun ps(k: String) = (P[k] as? JsonPrimitive)?.contentOrNull ?: ""
        val m = linkedMapOf<String, JsonElement>("kind" to JsonPrimitive("conn"), "conn" to (c.raw["id"] ?: JsonPrimitive(c.id)), "name" to JsonPrimitive(ps("name").ifEmpty { p.name }),
            "role" to JsonPrimitive(p.name), "prof" to JsonPrimitive(p.prof ?: ""), "mat" to JsonPrimitive(ps("mat").ifEmpty { "S275" }),
            "cls" to jsNum(StBolts.tkNum(c.q["cls"], 1.0).toInt().let { if (it == 0) 1 else it }.toDouble()))
        if (asm != null) m["asm"] = asm.toLongOrNull()?.let { jsNum(it.toDouble()) } ?: JsonPrimitive(asm)
        if (ps("pp").isNotEmpty()) m["pp"] = JsonPrimitive(ps("pp")); if (ps("ps").isNotEmpty()) m["ps"] = jsNum(StBuild.jsNum(P["ps"])); if (ps("fin").isNotEmpty()) m["fin"] = JsonPrimitive(ps("fin"))
        return JsonObject(m)
    }

    /** v69 stOwner, the live chain (v40 L15168 family rules, then v49 L15638 TG / CL, v49 L15531 T133 / T135 stubs, v58 L15885 TG gusset): the part a component plate is welded to. */
    private fun owner(c: StConn, name: String, ix: Int): String? {
        val d = ui?.conn?.get(c.type); val fam = d?.fam ?: tk?.byId(c.type)?.family ?: return c.parts.firstOrNull()
        val p = c.parts; val sec = p.getOrNull(1)
        if (fam == "TG" && "GUSSET" in name) return p[0]
        if (fam == "TG") { if (Regex("FIN|CROSSING").containsMatchIn(name)) return p[0]
            if ((d?.def?.get("kind") as? JsonPrimitive)?.contentOrNull == "cross") return p.getOrNull(1 + minOf(1, maxOf(0, ix - 1) shr 1)) ?: p.getOrNull(1); return p.getOrNull(1) }
        if (fam == "CL") return p[0]
        if ((c.type == "T133" || c.type == "T135") && name == "STUB") return p[0]
        return when (fam) {
            "EP", "CA", "BE" -> if (Regex("FRONT|STUB|STIFF|SHIM").containsMatchIn(name)) p[0] else if (p.getOrNull(2) != null && ix >= 0 && name == "END PLATE" && ix > 0) p[2] else sec
            "MF" -> if ("CONTINUITY" in name) p[0] else sec
            else -> p[0]
        }
    }

    private fun stOf(doc: DocumentApi, id: BodyId): JsonObject? = doc.bodyJson(id)?.let { runCatching { Json.parseToJsonElement(it).jsonObject["st"] as? JsonObject }.getOrNull() }
    private fun str(o: JsonObject, k: String) = (o[k] as? JsonPrimitive)?.contentOrNull ?: ""

    companion object {
        /** CONN defaults of the ported types (v64 L17087). */
        val DEFS = mapOf("TKCUT" to JsonObject(emptyMap()), "TKBOLT" to JsonObject(mapOf("d" to jsNum(16.0), "grade" to JsonPrimitive("8.8"))), "fitting" to JsonObject(mapOf("gap" to jsNum(10.0))))
        /** Connection types whose geometry is ported (S6). Others are left as the file has them. */
        val PORTED = setOf("TKCUT", "TKBOLT", "fitting")
        /** v58 TK_CLS: class colours of component plates. */
        val TK_CLS = mapOf(1 to 0x9AA0A6, 2 to 0xD64541, 3 to 0x3AA655, 4 to 0x3B6FD6, 5 to 0x2BB3C8, 6 to 0xE0C341, 7 to 0xC04FC4, 8 to 0x8B6F4E, 9 to 0x24408E, 10 to 0x6FBF73, 11 to 0xE08A2E, 12 to 0x7A7A7A, 13 to 0xB8B8B8, 14 to 0xF2A6C6)
    }
}
