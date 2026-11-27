package com.solidsketchapp.steel.conn

import com.solidsketchapp.core.api.Mesh
import com.solidsketchapp.steel.geom.D3
import com.solidsketchapp.steel.geom.Prism
import com.solidsketchapp.steel.geom.Region
import com.solidsketchapp.steel.geom.StBuild
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** v69 `stCyl` (v34 L13898): a low-poly cylinder (or hexagon, seg 6) from [p] along [axis], radius [r], length [len]. */
fun stCyl(p: D3, axis: D3, r: Double, len: Double, seg: Int = 12): Mesh {
    val n = axis.normalize(); val t = if (abs(n.z) < 0.9) D3.Z else D3(1.0, 0.0, 0.0)
    val u = (t cross n).normalize(); val v = n cross u
    val poly = List(seg) { i -> doubleArrayOf(r * cos((i + 0.5) * 2 * PI / seg), r * sin((i + 0.5) * 2 * PI / seg)) }
    return Prism.extrude(Region(poly), len, Prism.Frame(p, u, v, n))
}

/**
 * A bolt group: v69 `stBolts` as wrapped by v58 (L15956), with every Tekla bolt option of the group's settings [q]:
 * `bdel` (delete bolts by number), `stag` (1/2 every other bolt, 3/4 shift odd/even rows by half a spacing), `bdir` 2
 * (bolts from the other side), `blen` (extra length), `tol` (hole tolerance, default 2 up to M24, else 3), `htype`
 * (Slotted with `sl1`/`sl2` and `srot`, Oversized, Tapped, No hole), `ba_b` / `ba_w` / `ba_n` (bolt / washer / nut on).
 * Solids are kept as separate pieces; the caller unions them (`BooleanEngine`) as v69 does (`stUnion`).
 */
class BoltGroup(val pieces: List<Mesh>, val holes: List<Mesh>, val pts: List<D3>, val ax: D3, val up: D3, val rt: D3, val d: Double, val d0: Double, val sv: Double, val sh: Double, val grip: Double)

object StBolts {
    /** JavaScript `tkNum(v, d)`: '' / null / missing → d; a finite number (or numeric string) → it; else d. */
    fun tkNum(e: JsonElement?, d: Double): Double {
        val p = e as? JsonPrimitive ?: return d
        if (p.isString && p.content == "") return d
        val v = if (p.isString) com.solidsketchapp.steel.model.StGrid.jsNumber(p.content) else (p.content.toDoubleOrNull() ?: if (p.content == "true") 1.0 else if (p.content == "false") 0.0 else Double.NaN)
        return if (v.isFinite()) v else d
    }
    /** JavaScript `q.k !== 0 && q.k !== '0'` (off only for 0 or "0"). */
    private fun on(e: JsonElement?): Boolean { val p = e as? JsonPrimitive ?: return true; return !(if (p.isString) p.content == "0" else p.content.toDoubleOrNull() == 0.0) }

    fun build(list: List<D3>, axis: D3, d: Double, grip: Double, q: JsonObject, u: Double = 1.0): BoltGroup {
        var ax = axis.normalize(); val P = list
        var up = D3.Z.add(ax, -ax.z); if (up.length() < 0.3) up = D3(0.0, 1.0, 0.0).add(ax, -ax.y); up = up.normalize()
        val rt = (up cross ax).normalize(); val tl = 3 * u
        // Rows from the top, then across (v69's sort comparator, a stable sort like V8's).
        val ord = P.indices.sortedWith { i, j -> val du = (P[j] dot up) - (P[i] dot up); if (abs(du) > tl) sign(du) else sign((P[i] dot rt) - (P[j] dot rt)) }
        val rowOf = HashMap<Int, Int>(); val colOf = HashMap<Int, Int>(); var r = -1; var last: Double? = null; var cc = 0
        for (i in ord) { val uu = P[i] dot up; if (last == null || abs(uu - last) > tl) { r++; cc = 0; last = uu }; rowOf[i] = r; colOf[i] = cc++ }
        val del = ((q["bdel"] as? JsonPrimitive)?.contentOrNull ?: "").split(Regex("""[\s,;]+""")).filter { it.isNotEmpty() }.map { com.solidsketchapp.steel.model.StGrid.jsNumber(it) }.toSet()
        val stg = tkNum(q["stag"], 0.0).toInt()
        var keep = ArrayList<Int>()
        ord.forEachIndexed { n, i -> if ((n + 1).toDouble() in del) return@forEachIndexed; val rr = rowOf.getValue(i); val c2 = colOf.getValue(i)
            if (stg == 1 && (rr + c2) % 2 != 0) return@forEachIndexed; if (stg == 2 && (rr + c2) % 2 == 0) return@forEachIndexed; keep += i }
        if (keep.isEmpty()) keep = ArrayList(ord)
        var pts = keep.map { P[it] }.toMutableList()
        if (stg == 3 || stg == 4) {
            val byRow = LinkedHashMap<Int, MutableList<Int>>(); keep.forEachIndexed { n, i -> byRow.getOrPut(rowOf.getValue(i)) { ArrayList() } += n }
            for ((rr, ns) in byRow) {
                if ((rr % 2) != (if (stg == 3) 1 else 0) || ns.isEmpty()) continue
                val xs = ns.map { pts[it] dot rt }; val sp = if (ns.size > 1) (xs.max() - xs.min()) / (ns.size - 1) else 60 * u
                for (n in ns) pts[n] = pts[n].add(rt, sp / 2)
            }
        }
        if (tkNum(q["bdir"], 1.0) == 2.0) { pts = pts.map { it.add(ax, grip) }.toMutableList(); ax = -ax }
        val k = u; val bl = tkNum(q["blen"], 0.0) * u; val tol = tkNum(q["tol"], if (d <= 24) 2.0 else 3.0)
        val ht = (q["htype"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotEmpty() } ?: "Slotted"
        val wB = on(q["ba_b"]); val wW = on(q["ba_w"]); val wN = on(q["ba_n"])
        val pieces = ArrayList<Mesh>(); val holes = ArrayList<Mesh>(); val wt = 0.16 * d * k
        var hr = (if (ht == "Tapped") d / 2 else d / 2 + tol / 2) * k
        val s1 = tkNum(q["sl1"], 0.0) * k; val s2 = tkNum(q["sl2"], 0.0) * k
        if (ht == "Oversized") hr += s2 / 2
        val rot = StBuild.jsNum(q["srot"]) != 0.0
        val dv = if (rot) rt else up; val dh = if (rot) up else rt
        val sv = if (ht == "Slotted") (if (rot) s2 else s1) else 0.0; val sh = if (ht == "Slotted") (if (rot) s1 else s2) else 0.0
        for (p0 in pts) {
            if (wB) { pieces += stCyl(p0.add(ax, -0.65 * d * k), ax, 0.95 * d * k, 0.65 * d * k, 6); pieces += stCyl(p0.add(ax, -0.2 * d * k), ax, d / 2 * k, grip + 1.2 * d * k + bl, 10) }
            if (wW) pieces += stCyl(p0.add(ax, grip), ax, d * k, wt, 16)
            if (wN) pieces += stCyl(p0.add(ax, grip + (if (wW) wt else 0.0)), ax, 0.95 * d * k, 0.8 * d * k, 6)
            if (ht == "No hole") continue
            val nv = if (sv > 0) max(1.0, ceil(sv / (hr * 0.6))).toInt() else 0; val nh = if (sh > 0) max(1.0, ceil(sh / (hr * 0.6))).toInt() else 0
            for (a in 0..nv) for (b in 0..nh) {
                var o = p0.add(ax, -2 * k)
                if (nv != 0) o = o.add(dv, -sv / 2 + sv * a / nv); if (nh != 0) o = o.add(dh, -sh / 2 + sh * b / nh)
                holes += stCyl(o, ax, hr, grip + 4 * k, 12)
            }
        }
        if (pieces.isEmpty()) pieces += stCyl(pts[0].add(ax, grip * 0.5), ax, 0.01 * k, 0.01 * k, 3)
        return BoltGroup(pieces, holes, pts, ax, up, rt, d, 2 * hr / k, sv / k, sh / k, grip / k)
    }

    private fun sign(x: Double) = if (x > 0) 1 else if (x < 0) -1 else 0
}

/** v69 `stConnGeom` result for one connection: its own parts (plates, bolts) and the cuts it makes in the parts it joins. */
class ConnGeom(val parts: List<ConnPart>, val cuts: Map<String, List<Mesh>>, val welds: List<Weld> = emptyList(),
               /** Fields the geometry writes back on the connection (v69 `c.nSteps = n`, v44 L15397); stRegen stores them in its JSON. */ val set: Map<String, JsonElement> = emptyMap(),
               /** v70 X01 (web g.fits): the member ends this component fits to a plane (Tekla's Fitting), by part id. */ val fits: Map<String, List<Fit>> = emptyMap())
/** v70 X01: a member end fitted to a plane: the member is built [ext] longer at [end] (1 = p1, 2 = p2) and cut by the plane through [p], [n] pointing to the side removed. */
class Fit(val end: Int, val p: D3, val n: D3, val ext: Double)
/** A part a connection makes: its solid pieces (unioned), the holes still to subtract ([minus]), profile, bolt info. */
class ConnPart(val name: String, val pieces: List<Mesh>, val bolts: JsonObject? = null, val prof: String? = null, val minus: List<Mesh> = emptyList(), val group: BoltGroup? = null,
               /** v70 C01: an anchor rod made as a part carries its hole (st.hole: pts, ax, d, d0, L, poly) for the gross weight. */ val hole: JsonObject? = null,
               /** v70 C18: the part's material unless the Parts tab sets one (web p.mat70: the treads' Grating). */ val mat: String? = null,
               /** v70 C18: a catalog angle part's length (st.len70: weighed kg/m x length, the stair brackets). */ val len: Double? = null)

/**
 * Tekla-import connection types (v64 L17092): `TKCUT` cuts every part it lists with a member solid (`q.cut`, built
 * with plane / depth Middle and rotation Top unless the cut says otherwise); `TKBOLT` drills the group's holes
 * (hole = size + tolerance: `d + (tol ?? 2) − (d ≤ 24 ? 2 : 3)` handed to stBolts as the bolt size) in every part but
 * the `skip` ones, and makes the BOLTS / ANCHOR BOLTS part unless `holesOnly`.
 */
object TkConns {
    /** [stOf] gives a part's `st` by body id (fitting needs both parts' frames). */
    fun geom(type: String, parts: List<String>, q: JsonObject, build: StBuild, def: JsonObject = JsonObject(emptyMap()), stOf: (String) -> JsonObject? = { null }, profiles: com.solidsketchapp.steel.data.ProfileCatalog? = null, fam: String? = null, pt: D3? = null, conn: StConn? = null, conns: List<StConn> = emptyList(), docField: (String) -> String? = { null }): ConnGeom? = when {
        // v40: catalog components (T<n>) are built by their family generator with q = CONN def + c.q (S10: v69's own types as "C:<type>").
        fam != null -> if (profiles == null) null else Fam.geom(fam, JsonObject(LinkedHashMap(def).apply { putAll(q) }), parts, stOf, profiles, pt, conn, conns, docField)
        else -> geomType(type, parts, q, build, def, stOf, profiles)
    }

    private fun geomType(type: String, parts: List<String>, q: JsonObject, build: StBuild, def: JsonObject, stOf: (String) -> JsonObject?, profiles: com.solidsketchapp.steel.data.ProfileCatalog?): ConnGeom? = when (type) {
        // v38 L14276: Fitting (Tekla 13): the secondary part ends at the main part's face + gap (def gap 10).
        "fitting" -> {
            val a = parts.getOrNull(0)?.let(stOf); val b = parts.getOrNull(1)?.let(stOf)
            if (a == null || b == null || profiles == null) null else {
                val qq = JsonObject(LinkedHashMap(def).apply { putAll(q) })
                val j = Joints.joint(a, b, profiles)
                ConnGeom(emptyList(), mapOf(parts[1] to j.cutEnd(j.xFace + StBuild.jsNum(qq["gap"]))))
            }
        }
        "TKCUT" -> {
            val cut = q["cut"] as? JsonObject
            val st = JsonObject(linkedMapOf<String, JsonElement>("kind" to JsonPrimitive("member"), "plane" to JsonPrimitive("Middle"), "depth" to JsonPrimitive("Middle"), "rot" to JsonPrimitive("Top")).apply { if (cut != null) putAll(cut) })
            runCatching { build.build(st) }.getOrNull()?.let { m -> ConnGeom(emptyList(), parts.associateWith { listOf(m) }) }
        }
        "TKBOLT" -> {
            val pts = (q["pts"] as? JsonArray)?.map { e -> (e as JsonArray).let { D3(StBuild.jsNum(it[0]), StBuild.jsNum(it[1]), StBuild.jsNum(it[2])) } }
            if (pts.isNullOrEmpty()) null else {
                val d = StBuild.jsNum(q["d"]); val tolE = q["tol"]   // v69: q = c.q here (not the defaults)
                val hd = d + (if (tolE == null || tolE is kotlinx.serialization.json.JsonNull) 2.0 else StBuild.jsNum(tolE)) - (if (d <= 24) 2 else 3)
                val ax = (q["ax"] as JsonArray).let { D3(StBuild.jsNum(it[0]), StBuild.jsNum(it[1]), StBuild.jsNum(it[2])) }
                val grip = StBuild.jsNum(q["grip"])
                // v69: this branch (v64) runs outside the v58 wrapper that sets TKB_Q, so stBolts sees no options here:
                // TKBOLT always gets default holes, bolts, washers and nuts (the group's own q.tol only enters through hd).
                val b = StBolts.build(pts, ax, hd, grip, JsonObject(emptyMap()))
                val skip = (q["skip"] as? JsonArray)?.map { StBuild.jsNum(it).toInt() }?.toSet() ?: emptySet()
                val cuts = LinkedHashMap<String, List<Mesh>>(); parts.forEachIndexed { i, id -> if (i !in skip) cuts[id] = b.holes }
                if (StBuild.jsNum(q["holesOnly"]) != 0.0) ConnGeom(emptyList(), cuts) else {
                    val name = if (StBuild.jsNum(q["anchor"]) != 0.0) "ANCHOR BOLTS" else "BOLTS"
                    val info = JsonObject(linkedMapOf("pts" to JsonArray(b.pts.map { com.solidsketchapp.steel.model.jsArr(it.x, it.y, it.z) }), "ax" to com.solidsketchapp.steel.model.jsArr(b.ax.x, b.ax.y, b.ax.z),
                        "d" to com.solidsketchapp.steel.model.jsNum(d), "n" to com.solidsketchapp.steel.model.jsNum(pts.size.toDouble()),
                        "grade" to ((q["grade"] as? JsonPrimitive)?.takeIf { it.content.isNotEmpty() } ?: JsonPrimitive("8.8")), "L" to com.solidsketchapp.steel.model.jsNum(Math.floor(grip + 2 * d + 0.5))))
                    ConnGeom(listOf(ConnPart(name, b.pieces, info)), cuts)
                }
            }
        }
        else -> null
    }
}
