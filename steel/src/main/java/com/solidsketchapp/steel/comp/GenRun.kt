package com.solidsketchapp.steel.comp

import com.solidsketchapp.core.api.BodyId
import com.solidsketchapp.core.api.DocumentApi
import com.solidsketchapp.core.api.Mesh
import com.solidsketchapp.core.api.UndoApi
import com.solidsketchapp.steel.conn.Joints
import com.solidsketchapp.steel.conn.MemberFrame
import com.solidsketchapp.steel.conn.StRegen
import com.solidsketchapp.steel.data.SteelData
import com.solidsketchapp.steel.geom.D3
import com.solidsketchapp.steel.model.StParts
import com.solidsketchapp.steel.model.jsArr
import com.solidsketchapp.steel.model.jsNum
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * The GEN components (applications that make parts from the picked parts): v69 `stGenRun` (v40 L15170). Kinds divide,
 * xbrace / rodcross / tube, roofbrace, purlins / bridging, truss make members (v69 `mem` = stRecord of ST_SETS.BRACING +
 * name / prof / p1 / p2) and add the standard components between them (T1w / T20 / T11 / Tpc: stConnAdd with stCQ, one
 * regen), all in one undo step labelled with the component's label. stairs / rail / portal are the S10 applications
 * ([s10]); any other kind (Tof 'frame') only answers. [run] returns v69's toast text (null when v69 shows none).
 */
class GenRun(private val data: SteelData, private val doc: DocumentApi, private val undo: UndoApi?, private val parts: StParts,
             private val comps: Components, private val regen: StRegen?) {
    /** The kinds that run S10 applications in v69 (stStairs, stRailing, stPortalSheet): not ported here. */
    fun s10(k: String): Boolean = Js.str(comps.cq(k)["kind"]).let { it == "stairs" || it == "rail" || it == "portal" }

    private fun stOf(id: BodyId): JsonObject? = doc.bodyJson(id)?.let { runCatching { Json.parseToJsonElement(it).jsonObject["st"] as? JsonObject }.getOrNull() }
    private fun bodyJson(name: String, st: JsonObject) = JsonObject(linkedMapOf("name" to JsonPrimitive(name), "color" to JsonPrimitive(parts.colorOf(st)),
        "hidden" to JsonPrimitive(false), "type" to JsonPrimitive(parts.typeOf(st)), "st" to st)).toString()
    /** v69 stRecord + `S.bodies.push`: the body "<st.name> <id>". */
    private fun addBody(st: JsonObject, mesh: Mesh): BodyId { val nm = Js.str(st["name"]).let { if (st["name"] == null) "undefined" else it }
        val id = doc.addBody(bodyJson(nm, st), mesh); doc.replaceBody(id, bodyJson("$nm ${id.value}", st), mesh); return id }
    private fun arr(p: D3) = jsArr(p.x, p.y, p.z)

    /** v69 stGenRun(k, parts, pts). */
    fun run(k: String, picked: List<BodyId>, pts: List<DoubleArray> = emptyList()): String? {
        val cn = comps.ui.conn.getValue(k); val q = LinkedHashMap<String, JsonElement>(cn.def).apply { putAll(comps.cq(k)) }; val kind = Js.str(q["kind"])
        fun qn(key: String, d: Double) = if (Js.truthy(q[key])) Js.num(q[key]) else d
        fun qs(key: String, d: String) = if (Js.truthy(q[key])) Js.str(q[key]) else d
        val aSt = picked.getOrNull(0)?.let(::stOf); val bSt = picked.getOrNull(1)?.let(::stOf)
        if (kind == "portal") return null   // v69 stPortalSheet (S10)
        if (kind == "divide" && aSt != null && Js.str(aSt["kind"]) == "member") {
            val n = maxOf(2.0, qn("n", 2.0)).toInt(); val p1 = vec(aSt["p1"]); val p2 = vec(aSt["p2"])
            val sts = (0 until n).map { i -> JsonObject(LinkedHashMap(aSt).apply { put("p1", arr(p1.lerp(p2, i.toDouble() / n))); put("p2", arr(p1.lerp(p2, (i + 1).toDouble() / n)))
                put("dx1", jsNum(0.0)); put("dx2", jsNum(0.0)); remove("pos"); remove("amark") }) }
            val meshes = sts.map(parts::mesh)
            val run = { doc.removeBody(picked[0]); for (i in sts.indices) addBody(sts[i], meshes[i]); Unit }
            undo?.group("Divide into $n", run) ?: run()
            return "Divided into $n parts"
        }
        if (aSt == null || bSt == null) return "Pick two parts"
        if (kind == "stairs" || kind == "rail") return null   // v69 stStairs / stRailing (S10)
        val A = picked[0].value; val B = picked[1].value
        val MA = Joints.mf(aSt, data.profiles); val MB = Joints.mf(bSt, data.profiles)
        fun lo(m: MemberFrame) = if (m.c1.z <= m.c2.z) m.c1 to m.c2 else m.c2 to m.c1
        val made = ArrayList<Pair<JsonObject, Mesh>>(); val conns = ArrayList<Pair<String, List<String>>>(); var err: String? = null
        // v69 mem: Object.assign({kind:'member'}, ST_SETS.BRACING, {name, prof, p1, p2, depth:'Middle', rot:'Top'}, extra); the index in made, or null
        fun mem(name: String, prof: String, a: D3, b: D3, extra: Map<String, JsonElement> = emptyMap()): Int? {
            val m = LinkedHashMap<String, JsonElement>(); m["kind"] = JsonPrimitive("member"); m.putAll(data.propertySet("BRACING")!!.raw)
            m["name"] = JsonPrimitive(name); m["prof"] = JsonPrimitive(prof); m["p1"] = arr(a); m["p2"] = arr(b); m["depth"] = JsonPrimitive("Middle"); m["rot"] = JsonPrimitive("Top"); m.putAll(extra)
            val st = JsonObject(m)
            return runCatching { parts.mesh(st) }.fold({ made += st to it; made.size - 1 }, { err = it.message ?: it.toString(); null })
        }
        val ref = "#"   // conn parts: "#<i>" = the i-th made member (its body id is known once added)
        when (kind) {
            "xbrace", "rodcross", "tube" -> { val (a0, a1) = lo(MA); val (b0, b1) = lo(MB)
                val prof = qs("prof", if (kind == "rodcross") "D20" else if (kind == "tube") "CHS88.9x3.2" else "PL80*10"); val z0 = 300.0
                val p = listOf(a0.lerp(a1, z0 / a0.distanceTo(a1)), a1.lerp(a0, z0 / a0.distanceTo(a1)), b0.lerp(b1, z0 / b0.distanceTo(b1)), b1.lerp(b0, z0 / b0.distanceTo(b1)))
                val r1 = mem("BRACING", prof, p[0], p[3]); val r2 = if (kind == "tube") null else mem("BRACING", prof, p[2], p[1], mapOf("pOff" to jsNum(0.0)))
                val pk = data.profiles.lookup(prof)?.kind; val gk = if (kind == "rodcross") "T1w" else if (pk == "O" || pk == "R") "T20" else "T11"
                for (r in listOfNotNull(r1, r2)) { conns += gk to listOf(A, "$ref$r"); conns += gk to listOf(B, "$ref$r") } }
            "roofbrace" -> { val prof = qs("prof", "CHS88.9x3.2")
                val r1 = mem("ROOF BRACING", prof, MA.c1.lerp(MA.c2, 0.1), MB.c1.lerp(MB.c2, 0.5)); val r2 = mem("ROOF BRACING", prof, MB.c1.lerp(MB.c2, 0.1), MA.c1.lerp(MA.c2, 0.5))
                for (r in listOfNotNull(r1, r2)) { conns += "T20" to listOf(A, "$ref$r"); conns += "T20" to listOf(B, "$ref$r") } }
            "purlins", "bridging" -> { val br = kind == "bridging"; val s = qn("s", 1500.0); val L = minOf(MA.c2.distanceTo(MA.c1), MB.c2.distanceTo(MB.c1))
                // v69: the PURLIN set comes last and overrides the profile (q.prof is not used for purlins)
                val extra: Map<String, JsonElement> = if (br) emptyMap() else LinkedHashMap(data.propertySet("PURLIN")!!.raw).apply { put("name", JsonPrimitive("PURLIN")) }
                var x = if (br) L / 2 else 150.0
                while (x <= L - 100.0) { val pa = MA.c1.add(MA.a, x); val pb = MB.c1.add(MB.a, x)
                    val r = mem(if (br) "BRIDGING" else "PURLIN", qs("prof", if (br) "D16" else "IPE160"), pa.add(D3.Z, if (br) 0.0 else MA.h / 2), pb.add(D3.Z, if (br) 0.0 else MB.h / 2), extra)
                    if (r != null && !br) { conns += "Tpc" to listOf(A, "$ref$r"); conns += "Tpc" to listOf(B, "$ref$r") }
                    x += if (br) L else s } }
            "truss" -> { val ta = if (MA.c1.z > MA.c2.z) MA.c1 else MA.c2; val tb = if (MB.c1.z > MB.c2.z) MB.c1 else MB.c2; val D = qn("depth", 1500.0)
                val n = maxOf(2.0, qn("n", Math.floor(ta.distanceTo(tb) / 2000.0 + 0.5))).toInt(); val down = D3(0.0, 0.0, -D)
                val top = (0..n).map { ta.lerp(tb, it.toDouble() / n) }; val bot = (0..n).map { ta.lerp(tb, it.toDouble() / n) + down }
                mem("TOP CHORD", qs("prof", "HEA160"), top[0], top[n], mapOf("rot" to JsonPrimitive("Top"), "depth" to JsonPrimitive("Behind")))
                mem("BOTTOM CHORD", "HEA140", bot[0], bot[n], mapOf("rot" to JsonPrimitive("Top"), "depth" to JsonPrimitive("Middle")))
                for (i in 0..n) mem("VERTICAL", "SHS80x5", top[i], bot[i])
                for (i in 0 until n) mem("DIAGONAL", "SHS80x5", if (i < n / 2.0) top[i] else top[i + 1], if (i < n / 2.0) bot[i + 1] else bot[i]) }
            else -> return "${cn.label}: pick the parts it works between"
        }
        if (made.isEmpty()) return err
        val run = {
            val ids = made.map { (st, m) -> addBody(st, m).value }
            parts.ensureFolder("stBrace")
            val touched = LinkedHashSet<String>()
            for ((ck, ps0) in conns) { if (comps.ui.conn[ck] == null) continue
                val ps = ps0.map { if (it.startsWith(ref)) ids[it.substring(1).toInt()] else it }; connAdd(ck, ps, comps.cq(ck)); touched += ps }
            if (touched.isNotEmpty()) regen?.regen(doc, touched.map(::BodyId)); Unit
        }
        undo?.group(cn.label, run) ?: run()
        return "${cn.label}: ${made.size} parts${if (conns.isNotEmpty()) " · ${conns.size} connections" else ""}"
    }

    /** v69 stConnAdd (L13987): `{id: S.stNextConn++, type, parts, q}` appended to S.stConns (no regen). */
    private fun connAdd(k: String, ps: List<String>, q: JsonObject) {
        val next = doc.field("stNextConn")?.toDoubleOrNull()?.toLong()?.takeIf { it > 0 } ?: 1L
        val c = JsonObject(linkedMapOf("id" to jsNum(next.toDouble()), "type" to JsonPrimitive(k), "parts" to JsonArray(ps.map { p -> p.toLongOrNull()?.let { jsNum(it.toDouble()) } ?: JsonPrimitive(p) }), "q" to JsonObject(LinkedHashMap(q))))
        doc.setField("stNextConn", (next + 1).toString())
        val all = doc.field("stConns")?.let { runCatching { Json.parseToJsonElement(it).jsonArray.toList() }.getOrNull() } ?: emptyList()
        doc.setField("stConns", JsonArray(all + c).toString())
    }

    private fun vec(e: JsonElement?): D3 = (e as JsonArray).let { D3(Js.num(it[0]), Js.num(it[1]), Js.num(it[2])) }
}
