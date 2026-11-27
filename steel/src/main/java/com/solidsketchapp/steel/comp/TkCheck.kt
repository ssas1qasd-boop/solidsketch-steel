package com.solidsketchapp.steel.comp

import com.solidsketchapp.core.api.BodyId
import com.solidsketchapp.core.api.BooleanEngine
import com.solidsketchapp.core.api.DocumentApi
import com.solidsketchapp.core.api.Mesh
import com.solidsketchapp.steel.conn.Joints
import com.solidsketchapp.steel.conn.StConn
import com.solidsketchapp.steel.data.ProfileCatalog
import com.solidsketchapp.steel.geom.D3
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * v69 (v58 L16017–16110) rule checks per component and the symbol place: `tkStatus` (bolt edge distance ≥ 1.5·d in the
 * plates a bolt passes through, bolt spacing ≥ 2.2·d0, bolts outside every plate, weld a ≤ 0.7·t of the first plate,
 * a plate overlapping a joined part by more than 3 % of its volume), the status cache `TK_ST` (cleared when the
 * document changes), and `stSymPos`.
 */
class TkCheck(private val comps: Components, private val doc: DocumentApi, private val booleans: BooleanEngine?, private val profiles: ProfileCatalog) {
    /** v69 TK_RULE. */
    object Rule { const val EDGE = 1.5; const val PITCH = 2.2; const val WELD = 0.7; const val OVERLAP = 0.03 }
    class Status(val s: Int, val msgs: List<String>)

    private val cache = HashMap<String, Status>()
    private val listener = DocumentApi.Listener { synchronized(cache) { cache.clear() } }
    init { doc.addListener(listener) }
    fun cached(id: String): Status? = synchronized(cache) { cache[id] }

    private class Part(val id: BodyId, val st: JsonObject, val mesh: Mesh?)
    private fun part(id: BodyId): Part? { val st = doc.bodyJson(id)?.let { runCatching { Json.parseToJsonElement(it).jsonObject["st"] as? JsonObject }.getOrNull() } ?: return null; return Part(id, st, doc.bodyMesh(id)) }
    private fun s(o: JsonObject, k: String) = Js.str(o[k]).let { if (o[k] == null) "" else it }
    private fun vec(e: Any?) = (e as JsonArray).let { D3(Js.num(it[0]), Js.num(it[1]), Js.num(it[2])) }
    private fun jsRound(x: Double) = floor(x + 0.5).toLong()

    /** v69 tkThick: PL / FLT thickness, or an angle's leg thickness. */
    fun thick(st: JsonObject): Double { val p = s(st, "prof")
        val m = Regex("""^(?:PL|FLT)\s*(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE).find(p) ?: Regex("""^L\d+\*\d+\*(\d+)""", RegexOption.IGNORE_CASE).find(p) ?: Regex("""^L\d+\*(\d+)$""", RegexOption.IGNORE_CASE).find(p)
        return m?.groupValues?.get(1)?.toDouble() ?: 0.0 }
    private val skip = Regex("WASHER|NUT|GROUT|CAST PLATE|SHIM|PACK|FILL|SLEEVE|TREAD|RAIL|STUD|TURNBUCKLE")
    private fun skipPlate(st: JsonObject) = skip.containsMatchIn(s(st, "role").ifEmpty { s(st, "name") })

    /** v69 tkPierce: where the line P0 + s·ax passes through b's hole region (vertices within hr + 1.2 of the line): [smin, smax]. */
    private fun pierce(m: Mesh?, p0: D3, ax: D3, hr: Double): DoubleArray? {
        m ?: return null; var s0 = Double.POSITIVE_INFINITY; var s1 = Double.NEGATIVE_INFINITY; val lim = (hr + 1.2) * (hr + 1.2); val v = m.positions
        var i = 0; while (i < v.size) { val dx = v[i] - p0.x; val dy = v[i + 1] - p0.y; val dz = v[i + 2] - p0.z; val sx = dx * ax.x + dy * ax.y + dz * ax.z; val r2 = dx * dx + dy * dy + dz * dz - sx * sx
            if (r2 < lim) { if (sx < s0) s0 = sx; if (sx > s1) s1 = sx }; i += 3 }
        return if (s1 > s0 + 0.5) doubleArrayOf(s0, s1) else null
    }
    /** v69 tkSlice: b's outline in the plane (O, n) as segments. */
    private fun slice(m: Mesh?, o: D3, n: D3): List<Pair<D3, D3>> {
        m ?: return emptyList(); val v = m.positions; val ix = m.indices; val out = ArrayList<Pair<D3, D3>>()
        var t = 0; while (t < ix.size) {
            val p = Array(3) { k -> val i = ix[t + k] * 3; D3(v[i].toDouble(), v[i + 1].toDouble(), v[i + 2].toDouble()) }; val sd = DoubleArray(3) { (p[it] - o) dot n }
            val pts = ArrayList<D3>(2); for (k in 0..2) { val a = p[k]; val c = p[(k + 1) % 3]; val sa = sd[k]; val sc = sd[(k + 1) % 3]; if ((sa > 0) != (sc > 0)) { val f = sa / (sa - sc); pts += a.lerp(c, f) } }
            if (pts.size == 2) out += pts[0] to pts[1]; t += 3 }
        return out
    }
    private fun segDist(x: D3, a: D3, b: D3): Double { val ab = b - a; val l2 = (ab dot ab).let { if (it == 0.0) 1e-12 else it }; val t = max(0.0, min(1.0, ((x - a) dot ab) / l2)); return x.distanceTo(a.add(ab, t)) }
    private fun box(m: Mesh): DoubleArray { val lo = DoubleArray(3) { Double.POSITIVE_INFINITY }; val hi = DoubleArray(3) { Double.NEGATIVE_INFINITY }; val v = m.positions
        var i = 0; while (i < v.size) { for (k in 0..2) { val x = v[i + k].toDouble(); if (x < lo[k]) lo[k] = x; if (x > hi[k]) hi[k] = x }; i += 3 }; return lo + hi }

    /** v69 tkStatus(c, deep): 1 OK, 2 warnings, 3 errors; messages prefixed "<Tekla name> #<id>: ". Cached until the document changes. */
    fun status(c: StConn, deep: Boolean = true): Status {
        cached(c.id)?.let { return it }
        val lbl = comps.ui.conn[c.type]?.tekla ?: c.type
        val r = if (!comps.ok(c)) Status(3, listOf("$lbl #${c.id}: the component could not be created on these parts")) else compute(c, deep, lbl)
        synchronized(cache) { cache[c.id] = r }; return r
    }

    private fun compute(c: StConn, deep: Boolean, lbl: String): Status {
        val msgs = ArrayList<String>(); val errs = ArrayList<String>()
        val kids = comps.kids(c).mapNotNull(::part)
        val plates = kids.filter { s(it.st, "kind") == "conn" && !skipPlate(it.st) }
        val bolts = kids.filter { s(it.st, "kind") == "bolts" && ((it.st["pts"] as? JsonArray)?.size ?: 0) > 0 }
        val mems by lazy { c.parts.mapNotNull { part(BodyId(it)) }.filter { it.mesh != null && s(it.st, "kind") == "member" } }
        for (b in bolts) {
            val ax = vec(b.st["ax"]).normalize(); val d = Js.num(b.st["d"]).let { if (it == 0.0 || it.isNaN()) 20.0 else it }
            val d0 = Js.num(b.st["d0"]).let { if (it == 0.0 || it.isNaN()) d + (if (d <= 24) 2 else 3) else it }; val hr = d0 / 2
            val slot = max(Js.num(b.st["sv"]).let { if (it.isNaN()) 0.0 else it }, Js.num(b.st["sh"]).let { if (it.isNaN()) 0.0 else it }) / 2
            val P = b.st["pts"]!!.jsonArray.map(::vec); var nOut = 0
            val edge = LinkedHashMap<String, Pair<MutableList<Int>, DoubleArray>>()
            P.forEachIndexed { i, p0 -> var pierced = 0; var minE = Double.POSITIVE_INFINITY; var minPl: Part? = null
                for (pl in plates) { val sr = pierce(pl.mesh, p0, ax, hr + slot) ?: continue; pierced++
                    val o = p0.add(ax, (sr[0] + sr[1]) / 2)
                    // the bolt's own hole outline: both ends on the hole (web v70/rulecheck.js; a midpoint test depended on the triangulation)
                    for ((a, bb) in slice(pl.mesh, o, ax)) {
                        val own = P.any { q -> val qo = q.add(ax, (o - q) dot ax); qo.distanceTo(a) < hr + slot + 1.5 && qo.distanceTo(bb) < hr + slot + 1.5 }; if (own) continue
                        val dd = segDist(o, a, bb); if (dd < minE) { minE = dd; minPl = pl } } }
                if (pierced == 0) nOut++ else if (minE < Rule.EDGE * d - 0.5) { val k = s(minPl!!.st, "name"); val e = edge.getOrPut(k) { mutableListOf<Int>() to doubleArrayOf(Double.POSITIVE_INFINITY) }; e.first += i + 1; e.second[0] = min(e.second[0], minE) } }
            for ((nm, o) in edge) msgs += "Bolt edge distance of the bolt${if (o.first.size > 1) "s" else ""} ${o.first.joinToString(", ")} (M${Js.str(Js.n(d))}) in $nm is too small: ${jsRound(o.second[0])} mm < ${jsRound(Rule.EDGE * d)} mm (${Rule.EDGE}·d)"
            if (nOut == P.size && plates.isNotEmpty()) { if (P.any { p0 -> mems.any { m -> pierce(m.mesh, p0, ax, hr + slot) != null } }) nOut = 0 }
            if (nOut > 0 && plates.isNotEmpty()) errs += "$nOut bolt${if (nOut > 1) "s are" else " is"} outside the plates (${s(b.st, "name")} M${Js.str(Js.n(d))})"
            var minP = Double.POSITIVE_INFINITY
            for (i in P.indices) for (j in i + 1 until P.size) { var v = P[j] - P[i]; v = v.add(ax, -(v dot ax)); minP = min(minP, v.length()) }
            if (P.size > 1 && minP < Rule.PITCH * d0 - 0.5) msgs += "Bolt spacing is too small: ${jsRound(minP)} mm < ${jsRound(Rule.PITCH * d0)} mm (${Rule.PITCH}·d0, M${Js.str(Js.n(d))})"
        }
        val tmin = plates.map { thick(it.st) }.firstOrNull { it > 0 }
        if (tmin != null) for (w in (c.raw["welds"] as? JsonArray ?: JsonArray(emptyList())).map { it.jsonObject }) {
            if (Js.num(w["a"]) > Rule.WELD * tmin + 0.05) { msgs += "Weld “${Js.str(w["name"])}” a=${Js.str(w["a"])} mm is too big for a ${Js.str(Js.n(tmin))} mm plate (max ${com.solidsketchapp.core.api.Fmt.f("%.1f", Rule.WELD * tmin)})"; break } }
        val be = booleans
        if (deep && be != null) for (pl in plates) { val pm = pl.mesh ?: continue; val bb = box(pm); val vol = abs(runCatching { be.volume(pm) }.getOrDefault(0.0))
            for (m in mems) { val mb = box(m.mesh!!); if ((0..2).any { k -> bb[k] > mb[k + 3] || mb[k] > bb[k + 3] }) continue
                val iv = abs(runCatching { be.volume(be.intersect(pm, m.mesh)) }.getOrDefault(0.0))
                if (iv > max(Rule.OVERLAP * vol, 125.0)) { msgs += "${s(pl.st, "name")} overlaps ${s(m.st, "name")} ${s(m.st, "prof")} (${jsRound(iv / 1000)} cm³)"; break } } }
        val all = (errs + msgs).map { "$lbl #${c.id}: $it" }
        return Status(if (errs.isNotEmpty()) 3 else if (msgs.isNotEmpty()) 2 else 1, all)
    }

    // ---------------- symbols (v58 stSymPos) ----------------
    private fun seg(st: JsonObject): Pair<D3, D3> = Joints.mf(st, profiles).let { it.c1 to it.c2 }
    private fun closest(p: D3, a: D3, b: D3): D3 { val ab = b - a; val t = max(0.0, min(1.0, ((p - a) dot ab) / (ab dot ab).let { if (it == 0.0) 1e-12 else it })); return a.add(ab, t) }

    /** v69 stSymPos: at the joint (the secondary part's end on the main part, halfway when closer than 600 mm); a detail at its part's end. */
    fun symPos(c: StConn): D3? {
        val ps = c.parts.mapNotNull { part(BodyId(it)) }.filter { s(it.st, "kind") == "member" }
        runCatching {
            if (ps.size >= 2) { val (a1, a2) = seg(ps[0].st); val (b1, b2) = seg(ps[1].st)
                val e = if (closest(b1, a1, a2).distanceTo(b1) <= closest(b2, a1, a2).distanceTo(b2)) b1 else b2; val m = closest(e, a1, a2)
                return if (e.distanceTo(m) < 600) e.lerp(m, 0.5) else e }
            if (ps.size == 1) { val (a1, a2) = seg(ps[0].st)
                (c.raw["pt"] as? JsonArray)?.let { return closest(vec(it), a1, a2) }
                val kids = comps.kids(c).mapNotNull(::part).filter { s(it.st, "kind") != "bolts" && it.mesh != null }
                if (kids.isNotEmpty()) { val bs = kids.map { box(it.mesh!!) }; val m = D3((bs.minOf { it[0] } + bs.maxOf { it[3] }) / 2, (bs.minOf { it[1] } + bs.maxOf { it[4] }) / 2, (bs.minOf { it[2] } + bs.maxOf { it[5] }) / 2)
                    return if (m.distanceTo(a1) < m.distanceTo(a2)) a1 else a2 }
                return if (a1.z <= a2.z) a1 else a2 }
        }
        return c.parts.firstOrNull()?.let { part(BodyId(it)) }?.st?.get("p1")?.let { vec(it) }
    }

    /** v69 tkFanDraw: lines from the selected component's symbol to the parts it joins (members: the nearest point, moved 300 mm along when at the symbol). */
    fun fan(c: StConn): List<Pair<D3, D3>> {
        val p = symPos(c) ?: return emptyList(); val out = ArrayList<Pair<D3, D3>>()
        for (id in c.parts) { val b = part(BodyId(id)) ?: continue
            val q = runCatching { if (s(b.st, "kind") == "member") { val (a1, a2) = seg(b.st); var q = closest(p, a1, a2); val l = (a2 - a1).normalize(); if (q.distanceTo(p) < 1) q = q.add(l, 300.0 * (if (q.distanceTo(a2) > q.distanceTo(a1)) 1 else -1)); q }
                else b.mesh?.let { m -> val bx = box(m); D3((bx[0] + bx[3]) / 2, (bx[1] + bx[4]) / 2, (bx[2] + bx[5]) / 2) } }.getOrNull() ?: continue
            out += p to q }
        return out
    }
}
