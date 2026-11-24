package com.solidsketchapp.steel.conn

import com.solidsketchapp.core.api.Mesh
import com.solidsketchapp.steel.data.Profile
import com.solidsketchapp.steel.geom.D3
import com.solidsketchapp.steel.geom.Prism
import com.solidsketchapp.steel.geom.Region
import com.solidsketchapp.steel.geom.StBuild.Companion.str
import com.solidsketchapp.steel.geom.StBuild.Companion.vec
import kotlinx.serialization.json.JsonObject
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * S10: v69's v35 connection types (L14042): cleat (purlin cleat), wgusset (bolted gusset), tgusset (tube gusset), tieend
 * (tie end plate). Their geometry is the `stConnGeom3` wrapper L14050–14099; no later chapter changes these branches (the
 * v42 portal step L15295 turns some wgusset into T11 before they are made, which is the portal's business). They run
 * inside the v58 wrapper (L15991), which [Fam.geom] ports. U = 1 (mm).
 */
object ClassicV35 {
    val GEN: Map<String, (FamCtx) -> Unit> = linkedMapOf("cleat" to ::cleat, "wgusset" to ::wgusset, "tgusset" to ::tgusset, "tieend" to ::tieend)

    /** JavaScript `a || b || d` on numbers: the first one that is set and not 0 / NaN. */
    private fun or(vararg v: Double?, d: Double) = v.firstOrNull { it != null && it != 0.0 && !it.isNaN() } ?: d
    /** `Math.sign(v) || 1`. */
    private fun sgn1(v: Double) = if (v > 0) 1.0 else if (v < 0) -1.0 else 1.0
    /** JavaScript Math.round. */
    private fun jsRound(v: Double) = floor(v + 0.5)
    private fun prof(x: FamCtx, st: JsonObject): Profile? = x.profiles.lookup(st.str("prof") ?: "")

    /** v69 stRafterLF (L14049): the rafter's working frame at its first end, the slope tan and the top line z(s). */
    private class RafterLF(val f: LF, val tanp: Double, val z1: Double) { fun zTop(s: Double) = z1 + s * tanp }
    private fun rafterLF(rs: JsonObject): RafterLF {
        val p1 = rs.vec("p1"); val p2 = rs.vec("p2")
        val f = LF.of(D3(p1.x, p1.y, 0.0), D3(p2.x - p1.x, p2.y - p1.y, 0.0))
        val run = hypot(p2.x - p1.x, p2.y - p1.y).let { if (it == 0.0 || it.isNaN()) 1.0 else it }
        return RafterLF(f, (p2.z - p1.z) / run, p1.z)
    }

    /** v69 C.placeInFrame(C.extrudeRegions([{ outer: poly }], h)[0], { origin, u, v, n }). */
    private fun placed(poly: List<DoubleArray>, h: Double, o: D3, u: D3, v: D3, n: D3): Mesh = Prism.extrude(Region(poly), abs(h), Prism.Frame(o, u, v, n))

    /** cleat · purlin cleat (L14054): a plate on the rafter top against the purlin web, two bolts through the web. */
    fun cleat(x: FamCtx) {
        val r = x.a; val pu = x.b; val puId = x.bId; if (pu == null || puId == null) { x.nullResult = true; return }
        val rl = rafterLF(r); val F = rl.f
        val pa = pu.vec("p1"); val dp = pu.vec("p2") - pa; val den = dp dot F.y; if (abs(den) < 1e-9) { x.nullResult = true; return }
        val X = pa.add(dp, -((pa - F.o) dot F.y) / den); val s0 = (X - F.o) dot F.h
        val p = atan(rl.tanp); val e = doubleArrayOf(cos(p), sin(p)); val nn = doubleArrayOf(-sin(p), cos(p)); val pp = prof(x, pu)
        val tw = or(pp?.tw, d = 5.0); val t = x.n("t"); val hc = if (x.t("h")) x.n("h") else jsRound(or(pp?.h, d = 160.0) * 0.6); val w = x.n("w")
        val b0 = doubleArrayOf(s0, rl.zTop(s0))
        fun pt(a: Double, b: Double) = doubleArrayOf(b0[0] + e[0] * a + nn[0] * b, b0[1] + e[1] * a + nn[1] * b)
        val cl = F.prism(listOf(pt(tw / 2, 0.0), pt(tw / 2 + t, 0.0), pt(tw / 2 + t, hc), pt(tw / 2, hc)), -w / 2, w / 2)
        val ax = (F.h * -e[0]).add(F.Z, -e[1])
        val bl = listOf(0.35, 0.75).map { f -> val q2 = pt(tw / 2 + t + 0.1, hc * f); F.lp(q2[0], q2[1], 0.0) }
        val bb = x.group(bl, ax, x.n("bolt"), t + tw + 2)
        for (hc2 in bb.holes) x.cut(puId, hc2)
        x.plate("CLEAT", cl, x.n("t"), minus = bb.holes)
        x.bolts(bb, 2, (x.n("t") + or(pp?.tw, d = 5.0)) + 2 * x.n("bolt"))
        x.weld("Cleat to rafter", x.n("a"), 2 * x.n("w"))
    }

    /** wgusset · bolted gusset (L14068): a gusset plate on the column face, the flat bar bolted to it, packing for a gap. */
    fun wgusset(x: FamCtx) {
        val cs = x.a; val br = x.b; val brId = x.bId; if (br == null || brId == null) { x.nullResult = true; return }
        val cp = prof(x, cs); val rot = cs.str("rot"); val ch = (if (rot == "Front" || rot == "Back") cp?.b else cp?.h) ?: Double.NaN
        val c1 = cs.vec("p1"); fun hd(P: D3) = hypot(P.x - c1.x, P.y - c1.y)
        val bp1 = br.vec("p1"); val bp2 = br.vec("p2"); val (E, far) = if (hd(bp1) <= hd(bp2)) bp1 to bp2 else bp2 to bp1
        val bp = prof(x, br); val tb = or(bp?.b, d = 10.0); val T = x.n("t"); val toCol = sgn1(c1.x - E.x); val xFace = c1.x - toCol * ch / 2
        val d = (far - E).normalize(); val X = D3(1.0, 0.0, 0.0); val vv = (X cross d).normalize(); val x0 = min(xFace, xFace - toCol * T)
        val org = D3(x0, E.y, E.z); val w = x.n("w"); val len = x.n("len")
        val poly = listOf(doubleArrayOf(-40.0, -w / 2), doubleArrayOf(len, -w / 2), doubleArrayOf(len, w / 2), doubleArrayOf(-40.0, w / 2))
        val gp = placed(poly, T, org, d, vv, X)
        val xOut = E.x - toCol * (tb / 2 + 0.1); val bl = listOf(70.0, 170.0).map { a -> D3(xOut, E.y, E.z).add(d, a) }
        val gapP = abs(E.x - xFace) - tb / 2 - T; val grip = abs(E.x - xFace) + tb / 2 + 2
        val bb = x.group(bl, D3(toCol, 0.0, 0.0), x.n("bolt"), grip)
        for (hc2 in bb.holes) x.cut(brId, hc2)
        if (gapP > 0.5) {
            val xp0 = min(xFace - toCol * T, xFace - toCol * (T + gapP))
            val pk = placed(listOf(doubleArrayOf(-20.0, -w / 2 + 20), doubleArrayOf(len - 20, -w / 2 + 20), doubleArrayOf(len - 20, w / 2 - 20), doubleArrayOf(-20.0, w / 2 - 20)), gapP, D3(xp0, E.y, E.z), d, vv, X)
            x.plate("PACKING", pk, jsRound(gapP), minus = bb.holes)
        }
        x.plate("GUSSET", gp, x.n("t"), minus = bb.holes)
        x.bolts(bb, 2, grip + 2 * x.n("bolt"))
        x.weld("Gusset to column", x.n("a"), 2 * x.n("w") + 80)
    }

    /** tgusset · tube gusset (L14081): a plate in the brace's vertical plane, the tube stops short of the rafter web. */
    fun tgusset(x: FamCtx) {
        val r = x.a; val br = x.b; val brId = x.bId; if (br == null || brId == null) { x.nullResult = true; return }
        val F = rafterLF(r).f; val rp = prof(x, r); val tw = or(rp?.tw, d = 8.6)
        fun dy(p: D3) = (p - F.o) dot F.y
        val p1 = br.vec("p1"); val p2 = br.vec("p2"); val first = abs(dy(p1)) <= abs(dy(p2)); val E = if (first) p1 else p2; val far = if (first) p2 else p1
        val d = (far - E).normalize(); val dyd = abs(d dot F.y).let { if (it == 0.0 || it.isNaN()) 0.5 else it }
        val a0 = (tw / 2) / dyd; val gap = x.n("gap")
        val w = (D3.Z cross d).normalize(); val v = (w cross d).normalize(); val t = x.n("t"); val hh = x.n("w")
        val org = E.add(w, -t / 2); val len = (gap + x.n("lap")) / dyd
        val gp = placed(listOf(doubleArrayOf(a0, -hh / 2), doubleArrayOf(a0 + len, -hh / 2), doubleArrayOf(a0 + len, hh / 2), doubleArrayOf(a0, hh / 2)), t, org, d, v, w)
        x.plate("TUBE GUSSET", gp, x.n("t"))
        x.cut(brId, F.box(-50000.0, 50000.0, -50000.0, 50000.0, -(tw / 2 + gap), tw / 2 + gap))   // the tube stops short of the web, over the gusset
        x.weld("Tube to gusset", x.n("a"), 4 * x.n("lap")); x.weld("Gusset to rafter web", x.n("a"), 2 * x.n("w"))
    }

    /** tieend · tie end plate (L14092): a square plate on the column web side, four bolts, the tie welded to it. */
    fun tieend(x: FamCtx) {
        val cs = x.a; val ti = x.b; if (ti == null || x.bId == null) { x.nullResult = true; return }
        val cp = prof(x, cs); val ctw = or(cp?.tw, d = 7.5); val c1 = cs.vec("p1")
        val tp1 = ti.vec("p1"); val tp2 = ti.vec("p2"); val P = D3(c1.x, c1.y, tp1.z)   // v69 stEnd (L14048)
        val (E, far) = if (tp1.distanceTo(P) <= tp2.distanceTo(P)) tp1 to tp2 else tp2 to tp1
        val sgn = sgn1(far.y - c1.y); val t = x.n("t"); val a = x.n("size") / 2; val yF = c1.y + sgn * ctw / 2
        val y0 = min(yF, yF + sgn * t); val y1 = max(yF, yF + sgn * t)
        val F = LF.of(D3(c1.x, 0.0, 0.0), D3(1.0, 0.0, 0.0))
        val ep = F.box(E.x - c1.x - a, E.x - c1.x + a, E.z - a, E.z + a, y0, y1)
        val bl = ArrayList<D3>(); for (sx in listOf(-1, 1)) for (sz in listOf(-1, 1)) bl += D3(E.x + sx * (a - 25), yF + sgn * (t + 0.1), E.z + sz * (a - 25))
        val bb = x.group(bl, D3(0.0, -sgn, 0.0), x.n("bolt"), t + ctw + 2)
        for (hc2 in bb.holes) x.cut(x.aId, hc2)
        x.plate("END PLATE", ep, x.n("t"), minus = bb.holes)
        x.bolts(bb, 4, x.n("t") + or(cp?.tw, d = 7.5) + 2 * x.n("bolt"))
        val tp = prof(x, ti); x.weld("Tie to end plate", x.n("a"), PI * or(tp?.d, d = 114.0))
    }
}
