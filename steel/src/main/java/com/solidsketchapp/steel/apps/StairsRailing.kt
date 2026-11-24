package com.solidsketchapp.steel.apps

import com.solidsketchapp.core.api.BodyId
import com.solidsketchapp.core.api.DocumentApi
import com.solidsketchapp.core.api.Mesh
import com.solidsketchapp.core.api.UndoApi
import com.solidsketchapp.steel.comp.Components
import com.solidsketchapp.steel.comp.Js
import com.solidsketchapp.steel.conn.Joints
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
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * S10: v69's stairs and railing applications, the live chain:
 *  - stairs: v44 stStairs (L15399: two stringers by stAddMany, then stConnectMany with stairtreads and, with a top part,
 *    two T144 end plates) wrapped by v50 (L15726: each stringer end found on a support by stSupportAt gets a weldonly when
 *    it bears on top of a horizontal member (stBearing), else a T144);
 *  - railing: v44 stRailing (L15405: stanchions, handrail and knee rail per path segment, weldonly between rails and
 *    posts, T13 to `on`) wrapped by v50 (L15722: without `on`, each stanchion found on a support gets a weldonly).
 * Units mm (stU() = 1). Parts are added like v69 stAddMany (L15305: one step, null for a part that cannot be built),
 * connections like stConnectMany (L15309: q = stCQ(key) + q, unknown keys and missing parts skipped, one regen).
 */
class StairsRailing(private val data: SteelData, private val doc: DocumentApi, private val undo: UndoApi?, private val parts: StParts,
                    private val comps: Components) {
    /** What an application made: the part ids (null where a part could not be built), the connections added, v69's toast. */
    class Result(val ids: List<BodyId?>, val conns: List<String?>, val toast: String?)

    private fun stOf(id: BodyId): JsonObject? = doc.bodyJson(id)?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }?.get("st") as? JsonObject
    private fun bodyOf(id: BodyId): JsonObject? = doc.bodyJson(id)?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
    private fun v3(e: JsonElement?): D3 = (e as JsonArray).let { D3(Js.num(it[0]), Js.num(it[1]), Js.num(it[2])) }
    private fun arr(p: D3) = jsArr(p.x, p.y, p.z)
    private fun str(e: JsonElement?) = (e as? JsonPrimitive)?.contentOrNull ?: ""
    /** JS `a || d` for a profile dimension. */
    private fun or(v: Double?, d: Double) = if (v == null || v == 0.0 || v.isNaN()) d else v

    /** v69 stAddMany (L15305): every st built first (null where it fails), the built ones added in one step. */
    private fun addMany(list: List<JsonObject>, label: String): List<BodyId?> {
        val built = list.map { st -> runCatching { parts.mesh(st) }.getOrNull()?.let { st to it } }
        if (built.none { it != null }) return built.map { null }
        var ids: List<BodyId?> = emptyList()
        val run = { ids = built.map { b -> b?.let { (st, m: Mesh) -> parts.addBuilt(st, m) } } }
        undo?.group(label, run) ?: run()
        return ids
    }

    private fun known(k: String) = runCatching { comps.defOf(k) }.isSuccess

    /** v69 stConnectMany (L15309): (key, parts, q) → the new connections' ids (null = skipped: unknown key or a missing part). */
    private fun connectMany(list: List<Triple<String, List<BodyId>, JsonObject>>, label: String): List<String?> {
        val ok = list.map { (k, ps, _) -> known(k) && ps.all { doc.bodyJson(it) != null } }
        val go = list.filterIndexed { i, _ -> ok[i] }
        var made: List<String> = emptyList()
        val run = { if (go.isNotEmpty()) made = comps.addMany(go, merge = true).map { it.id } }
        undo?.group(label, run) ?: run()
        var j = 0; return ok.map { if (it) made[j++] else null }
    }

    /** v69 stLinked (L15719): a connection lists both parts. */
    private fun linked(a: BodyId, b: BodyId) = comps.conns().any { a.value in it.parts && b.value in it.parts }

    private fun box(m: Mesh): DoubleArray { val lo = DoubleArray(3) { Double.POSITIVE_INFINITY }; val hi = DoubleArray(3) { Double.NEGATIVE_INFINITY }
        for (i in 0 until m.vertexCount) for (k in 0..2) { val v = m.positions[i * 3 + k].toDouble(); lo[k] = min(lo[k], v); hi[k] = max(hi[k], v) }
        return lo + hi }

    /** v69 stSupportAt (L15712): the nearest visible member (not in [excl]) whose body is within tol of P. */
    fun supportAt(P: D3, excl: Set<BodyId>, tol: Double = 60.0): BodyId? {
        var best: BodyId? = null; var bd = Double.POSITIVE_INFINITY
        for (id in doc.bodyIds()) { val b = bodyOf(id) ?: continue; val st = b["st"] as? JsonObject ?: continue
            if (str(st["kind"]) != "member" || (b["hidden"] as? JsonPrimitive)?.booleanOrNull == true || id in excl) continue
            val x = doc.bodyMesh(id)?.let(::box) ?: continue
            if (P.x < x[0] - tol || P.x > x[3] + tol || P.y < x[1] - tol || P.y > x[4] + tol || P.z < x[2] - tol || P.z > x[5] + tol) continue
            val M = Joints.mf(st, data.profiles); val v = P - M.c1; val L = M.c2.distanceTo(M.c1); val s = max(0.0, min(L, v dot M.a))
            val d = v.add(M.a, -s).length() - max(M.h, M.b) / 2
            if (d <= tol && d < bd) { bd = d; best = id } }
        return best
    }

    /** v69 stBearing (L15718): the end sits on top of a horizontal member (weld it, no end plate). */
    fun bearing(P: D3, sup: BodyId): Boolean {
        val st = stOf(sup) ?: return false; val M = Joints.mf(st, data.profiles); if (abs(M.a.z) > 0.3) return false
        val x = doc.bodyMesh(sup)?.let(::box) ?: return false
        return P.z >= x[5] - 10
    }

    private fun member(set: String, vararg kv: Pair<String, JsonElement>): JsonObject {
        val m = LinkedHashMap<String, JsonElement>(); m["kind"] = JsonPrimitive("member"); m.putAll(data.propertySet(set)!!.raw); for ((k, v) in kv) m[k] = v
        return JsonObject(m)
    }
    private fun s(v: String) = JsonPrimitive(v)

    /**
     * v69 stStairs (L15399) + v50 (L15726). [o]: p0, p1 (top nosing / landing point), width, stringer, tread, riserMax;
     * [topPart]: the part the top ends get T144 end plates to (v69 o.topPart).
     */
    fun stairs(o: JsonObject, topPart: BodyId? = null): Result {
        val q = LinkedHashMap<String, JsonElement>(mapOf("width" to jsNum(1000.0), "stringer" to s("UPN200"), "tread" to jsNum(6.0), "riserMax" to jsNum(180.0))).apply { putAll(o) }
        val P0 = v3(q["p0"]); val P1 = v3(q["p1"]); val dh = (P1 - P0).let { D3(it.x, it.y, 0.0) }.normalize(); val lat = (D3.Z cross dh).normalize()
        val sp = data.profiles.lookup(Js.str(q["stringer"])); val off = Js.num(q["width"]) / 2 + or(sp?.b, 75.0) / 2
        fun mk(sg: Int) = member("BEAM", "name" to s("STRINGER"), "prof" to q.getValue("stringer"), "ap" to s("ST"), "pp" to s("st"), "cls" to jsNum(10.0), "depth" to s("Middle"), "rot" to s("Top"),
            "p1" to arr(P0.add(lat, sg * off)), "p2" to arr(P1.add(lat, sg * off)), "rAng" to jsNum(0.0))
        val ids = addMany(listOf(mk(-1), mk(1)), "Stairs: stringers"); val a = ids[0]; val b = ids[1]
        val conns = ArrayList<String?>(); var toast: String? = null
        if (a != null && b != null) {
            val list = arrayListOf(Triple("stairtreads", listOf(a, b), JsonObject(linkedMapOf("tread" to q.getValue("tread"), "riserMax" to q.getValue("riserMax")))))
            val tq = JsonObject(linkedMapOf("tp" to jsNum(10.0), "rows" to jsNum(2.0), "gauge" to jsNum(60.0), "side" to jsNum(25.0)))
            if (topPart != null) { list += Triple("T144", listOf(topPart, a), tq); list += Triple("T144", listOf(topPart, b), tq) }
            val res = connectMany(list, "Stairs: treads"); conns += res
            val n = res[0]?.let { id -> comps.conn(id)?.raw?.get("nSteps") }?.takeIf { Js.truthy(it) }?.let { Js.str(it) } ?: "?"
            toast = "Stairs: 2 stringers, $n risers"
        }
        // ---- v50 (L15726): stringer ends on supports
        val own = ids.filterNotNull().toSet(); val list = ArrayList<Triple<String, List<BodyId>, JsonObject>>()
        for (id in ids) { val st = id?.let(::stOf) ?: continue
            for (P in listOf(v3(st["p1"]), v3(st["p2"]))) { val sup = supportAt(P, own, 60.0) ?: continue
                if (linked(sup, id) || list.any { it.second[0] == sup && it.second[1] == id }) continue
                list += if (bearing(P, sup)) Triple("weldonly", listOf(sup, id), JsonObject(emptyMap()))
                else Triple("T144", listOf(sup, id), JsonObject(linkedMapOf("tp" to jsNum(10.0), "rows" to jsNum(2.0), "gauge" to jsNum(60.0), "side" to jsNum(30.0)))) } }
        if (list.isNotEmpty()) conns += connectMany(list, "Stairs: stringer ends")
        return Result(ids, conns, toast)
    }

    /**
     * v69 stRailing (L15405) + v50 (L15722). [o]: path (the walking edge, at the surface the posts stand on), h, postS, post,
     * rail, knee; [on]: the part the posts get T13 fittings to (v69 o.on; then no support welds).
     */
    fun railing(o: JsonObject, on: BodyId? = null): Result {
        val q = LinkedHashMap<String, JsonElement>(mapOf("h" to jsNum(1100.0), "postS" to jsNum(1200.0), "post" to s("CHS42.4x3.2"), "rail" to s("CHS42.4x2.6"), "knee" to JsonPrimitive(true))).apply { putAll(o) }
        val P = (q["path"] as JsonArray).map(::v3); val posts = ArrayList<JsonObject>(); val rails = ArrayList<JsonObject>()
        val h = Js.num(q["h"]); val rr = or(data.profiles.lookup(Js.str(q["rail"]))?.d, 42.4)
        for (k in 0 until P.size - 1) { val a = P[k]; val b = P[k + 1]; val L = a.distanceTo(b); val n = max(1.0, ceil(L / Js.num(q["postS"]))).toInt()
            for (i in 0..n) { if (k > 0 && i == 0) continue; val sp = a.lerp(b, i.toDouble() / n)
                posts += member("COLUMN", "name" to s("STANCHION"), "prof" to q.getValue("post"), "ap" to s("HR"), "pp" to s("hr"), "cls" to jsNum(11.0), "rot" to s("Front"),
                    "p1" to arr(sp), "p2" to arr(sp.add(D3.Z, h - rr / 2))) }
            for ((z, nm) in listOf(h - rr / 2 to "HANDRAIL") + (if (Js.truthy(q["knee"])) listOf(h / 2 to "KNEE RAIL") else emptyList()))
                rails += member("BEAM", "name" to s(nm), "prof" to q.getValue("rail"), "ap" to s("HR"), "pp" to s("hr"), "cls" to jsNum(11.0), "depth" to s("Middle"),
                    "p1" to arr(a.add(D3.Z, z)), "p2" to arr(b.add(D3.Z, z))) }
        val ids = addMany(posts + rails, "Railing"); val pid = ids.subList(0, posts.size); val rid = ids.subList(posts.size, ids.size)
        val list = ArrayList<Triple<String, List<BodyId>, JsonObject>>(); val none = JsonObject(emptyMap())
        for (ri in rid) { if (ri == null) continue; val rst = stOf(ri)!!; val R = Joints.mf(rst, data.profiles)
            val ha0 = D3(R.a.x, R.a.y, 0.0); val hl = ha0.length(); val ha = ha0.normalize()
            for (pi in pid) { if (pi == null) continue; val Pp = v3(stOf(pi)!!["p1"]); val v = (Pp - R.c1).let { D3(it.x, it.y, 0.0) }; val sv = v dot ha
                if (sv < -1 || sv > R.c2.distanceTo(R.c1) * hl + 1) continue
                val d = v.add(ha, -sv); if (hypot(d.x, d.y) > 5) continue
                list += if (str(rst["name"]) == "HANDRAIL") Triple("weldonly", listOf(ri, pi), none) else Triple("weldonly", listOf(pi, ri), none) } }
        if (on != null) for (pi in pid) if (pi != null) list += Triple("T13", listOf(on, pi), JsonObject(linkedMapOf("gap" to jsNum(0.0))))
        val conns = ArrayList<String?>(); if (list.isNotEmpty()) conns += connectMany(list, "Railing: welds")
        val toast = "Railing: ${posts.size} posts, ${rails.size} rails"
        // ---- v50 (L15722): posts welded to the supports they stand on (only when `on` is not given)
        if (on == null) { val own = ids.filterNotNull().toSet(); val l2 = ArrayList<Triple<String, List<BodyId>, JsonObject>>()
            for (id in ids) { val st = id?.let(::stOf) ?: continue; if (str(st["name"]) != "STANCHION") continue
                val sup = supportAt(v3(st["p1"]), own, 40.0); if (sup != null && !linked(sup, id)) l2 += Triple("weldonly", listOf(sup, id), none) }
            if (l2.isNotEmpty()) conns += connectMany(l2, "Railing: posts to supports") }
        return Result(ids, conns, toast)
    }
}
