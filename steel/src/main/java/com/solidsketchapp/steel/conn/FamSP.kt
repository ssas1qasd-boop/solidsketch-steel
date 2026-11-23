package com.solidsketchapp.steel.conn

import com.solidsketchapp.core.api.JsNumber
import com.solidsketchapp.core.api.Mesh
import com.solidsketchapp.steel.geom.D3
import com.solidsketchapp.steel.geom.Prism
import com.solidsketchapp.steel.geom.Region
import com.solidsketchapp.steel.geom.Sections
import com.solidsketchapp.steel.geom.StBuild
import com.solidsketchapp.steel.geom.StBuild.Companion.num
import com.solidsketchapp.steel.geom.StBuild.Companion.str
import com.solidsketchapp.steel.geom.StBuild.Companion.vec
import com.solidsketchapp.steel.model.jsArr
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * SP family: splices (end plates 14j / 26 / 124, tube 6, web tab 33t / 12j, cover plates 42 / 45 / 53 / 77 / 125 / 132 through
 * the v38 'splice' type, site weld 137, angles 152 / 175, lap 176, turnbuckle 126, stub 133 / 135). Live chain: v69 FAM.SP
 * stub (L15516) → angles (L15478, FAM_SP17) → base (L15042, FAM_SP15); cover → stConnGeom 'splice' (L14312).
 */
object FamSP {
    const val PORTED = true
    private const val BIG = 60000.0

    private fun d0Of(d: Double) = d + (if (d <= 24) 2 else 3)
    private fun or(v: Double?, d: Double) = if (v == null || v == 0.0 || v.isNaN()) d else v
    /** JavaScript `'' + q.k` of a number / string value. */
    private fun jsStr(e: JsonElement?): String { val p = e as? JsonPrimitive ?: return if (e is JsonNull) "null" else "undefined"
        return if (p.isString) p.content else p.content.toDoubleOrNull()?.let { JsNumber.format(it) } ?: p.content }

    fun gen(x: FamCtx) {
        val b = x.b ?: throw IllegalStateException("not built")
        when (x.s("kind")) { "stub" -> stub(x, b, x.bId!!); "angles" -> angles(x); else -> base(x) }
    }

    /** The common splice frame (O between the nearest ends, X out of A into B, Y = A's eh). */
    private class Sf(val ma: MemberFrame, val o: D3, val xd: D3, val yd: D3) { val f = Joints.Frame3(o, xd, yd) }
    private fun frame(x: FamCtx): Sf {
        val ma = Joints.mf(x.a, x.profiles); val mb = Joints.mf(x.b!!, x.profiles)
        var bd = Double.POSITIVE_INFINITY; var ba = ma.c1; var bb = mb.c1; var aIs1 = true
        for ((ai, a) in listOf(ma.c1, ma.c2).withIndex()) for (b in listOf(mb.c1, mb.c2)) { val d = a.distanceTo(b); if (d < bd) { bd = d; ba = a; bb = b; aIs1 = ai == 0 } }
        val o = (ba + bb) * 0.5; val aFar = if (aIs1) ma.c2 else ma.c1
        return Sf(ma, o, (o - aFar).normalize(), ma.eh)
    }
    private fun cutAt(x: FamCtx, f: Joints.Frame3, ga: Double, gb: Double) {
        x.cut(x.aId, f.box(ga, BIG, -BIG / 10, BIG / 10, -BIG / 10, BIG / 10)); x.cut(x.bId!!, f.box(-BIG, gb, -BIG / 10, BIG / 10, -BIG / 10, BIG / 10))
    }

    /** v69 FAM.SP base (L15042). */
    private fun base(x: FamCtx) {
        val k = x.s("kind")
        if (k == "cover") { cover(x); return }
        val s = frame(x); val ma = s.ma; val f = s.f; val xd = s.xd; val yd = s.yd; val hh = ma.h / 2; val bb = ma.b / 2
        val prof = x.profiles.lookup(x.a.str("prof") ?: ""); val pP = or(prof?.P, 1.0); val bolt = x.n("bolt")
        when (k) {
            "endplates", "tube" -> {
                val tp = x.n("tp"); cutAt(x, f, -tp, tp); val round = x.t("round") || ma.kind == "O"; val ext = x.n("ext"); val d0 = d0Of(bolt)
                val r = max(ma.h, ma.b) / 2 + ext + 2 * d0; val isI = ma.kind == "I"
                fun mk(x0: Double, x1: Double): Mesh = if (round) Prism.extrude(Region(List(40) { i -> doubleArrayOf(r * cos(i * PI / 20), r * sin(i * PI / 20)) }), abs(x1 - x0), Prism.Frame(f.p(x0, 0.0, 0.0), yd, f.z, xd))
                    else f.box(x0, x1, -hh - ext - (if (isI) 0.0 else 2 * d0), hh + ext + (if (isI) 0.0 else 2 * d0), -bb - ext, bb + ext)
                val p1 = mk(-tp, 0.0); val p2 = mk(0.0, tp); val pts = ArrayList<DoubleArray>()
                if (round || !isI) { val nb = max(4, jsInt(x.n("n"))); val rb = max(ma.h, ma.b) / 2 + d0 * 1.2
                    for (i in 0 until nb) { val a = (i + 0.5) * 2 * PI / nb; pts += doubleArrayOf(rb * cos(a), rb * sin(a)) } }
                else { val nr = max(2, Fam.tkRows(ma.h)); val g = min(90.0, ma.b - 40) / 2
                    for (i in 0 until nr) { val y = (hh - (ma.tf + 40)) - i * ((2 * hh - 2 * (ma.tf + 40)) / max(1, nr - 1)); for (z in listOf(-g, g)) pts += doubleArrayOf(y, z) } }
                val bl = pts.map { f.p(-tp - 0.1, it[0], it[1]) }; val bg = x.group(bl, xd, bolt, 2 * tp + 2)
                x.plate("END PLATE", p1, tp, minus = bg.holes); x.plate("END PLATE", p2, tp, minus = bg.holes)
                x.bolts(bg, bl.size, 2 * tp + 2 * bolt); x.weld("Ends to plates", 6.0, 2 * pP * 1000)
            }
            "tab" -> {
                val gap = x.n("gap"); val e = x.n("e"); cutAt(x, f, -gap / 2, gap / 2); val tw = ma.tw / 2; val t = x.n("twp"); val hw = hh - (ma.tf + or(ma.r, 10.0) + 5)
                val tp = f.box(-gap / 2 - 10, gap / 2 + e * 2 + x.n("pitch"), -hw, hw, tw, tw + t)
                val bl = listOf(-hw + e, 0.0, hw - e).map { y -> f.p(gap / 2 + e, y, tw + t + 0.1) }
                val bg = x.group(bl, -f.z, bolt, t + 2 * tw + 2); for (hc in bg.holes) x.cut(x.bId!!, hc)
                x.plate("TAB PLATE", tp, t, minus = bg.holes); x.bolts(bg, 3, t + ma.tw + 2 * bolt); x.weld("Tab to main web", 6.0, 2 * hw * 2)
            }
            "angles", "weld" -> {   // 'angles' only when B is missing (then not built anyway); 'weld' = erection plates + site butt weld
                val gap = x.n("gap"); val e = x.n("e"); val pi = x.n("pitch"); cutAt(x, f, -gap / 2, gap / 2); val tw = ma.tw / 2; val t = if (k == "weld") 12.0 else 10.0
                val hw = min(hh - (ma.tf + or(ma.r, 10.0) + 5), 150.0)
                val parts = listOf(1, -1).map { sg -> if (k == "angles") f.box(-(2 * e + pi), 2 * e + pi, -hw, hw, sg * tw, sg * (tw + t)) else f.box(-140.0, 140.0, -hw, hw, sg * tw, sg * (tw + t)) }
                val bl = ArrayList<D3>(); for (xx in listOf(-(e + gap / 2), e + gap / 2, -(e + gap / 2 + pi), e + gap / 2 + pi)) for (y in listOf(-hw / 2, hw / 2)) bl += f.p(xx, y, tw + t + 0.1)
                val bg = x.group(bl, -f.z, bolt, 2 * t + 2 * tw + 2); for (hc in bg.holes) { x.cut(x.aId, hc); x.cut(x.bId!!, hc) }
                parts.forEach { x.plate(if (k == "weld") "ERECTION PLATE" else "SPLICE ANGLE", it, t, if (k == "angles") "L90*90*10" else null, minus = bg.holes) }
                x.bolts(bg, bl.size, 2 * t + ma.tw + 2 * bolt); if (k == "weld") x.weld("Site butt weld (CJP)", ma.tf, pP * 1000)
            }
            "lap" -> {
                val e = x.n("e"); val bl = listOf(s.o.add(xd, -e), s.o.add(xd, -(e + x.n("pitch")))).map { it.add(ma.es, ma.b) }
                val bg = x.group(bl, -ma.es, bolt, 2 * ma.b + 2); for (hc in bg.holes) { x.cut(x.aId, hc); x.cut(x.bId!!, hc) }
                x.bolts(bg, 2, 2 * ma.b + 2 * bolt)
            }
            "turnbuckle" -> { cutAt(x, f, -80.0, 80.0); val r = max(ma.h, ma.b) / 2 * 1.6; x.plate("TURNBUCKLE", stCyl(f.p(-90.0, 0.0, 0.0), xd, r, 180.0, 6), 0.0, "TURNBUCKLE") }
            else -> throw IllegalStateException("not built")
        }
    }

    /** cover: v69 stConnGeom(type 'splice') (L14312) with q = CONN.splice.def + the component's gap … grade; through the v58 wrapper (its own TKB_Q). */
    private fun cover(x: FamCtx) {
        val qi = JsonObject(LinkedHashMap<String, JsonElement>().apply { for (k in listOf("gap", "tfp", "twp", "cols", "wrows", "pitch", "e", "bolt", "grade")) x.q[k]?.let { put(k, it) } })
        fun n(k: String) = qi[k]?.let { if (it is JsonNull) 0.0 else StBuild.jsNum(it) } ?: Double.NaN
        val s = frame(x); val ma = s.ma; val f = s.f; val yd = s.yd; val z = f.z; val bolt = n("bolt"); val d0 = or(bolt, 20.0).let { it + (if (it <= 24) 2 else 3) }
        val gap = n("gap"); cutAt(x, f, -gap / 2, gap / 2)
        val hh = ma.h / 2; val bb = ma.b / 2; val tf = ma.tf; val tw = ma.tw; val tfp = n("tfp"); val twp = n("twp"); val e = n("e"); val pi = n("pitch"); val nc = max(1, jsInt(n("cols")))
        val lh = gap / 2 + e + (nc - 1) * pi + e; val xs = ArrayList<Double>(); for (sg in listOf(-1, 1)) for (i in 0 until nc) xs += sg * (gap / 2 + e + i * pi)
        val gz = max(bb * 0.6, tw / 2 + or(ma.r, 10.0) + 1.2 * d0)
        val top = f.box(-lh, lh, hh, hh + tfp, -bb, bb); val bot = f.box(-lh, lh, -hh - tfp, -hh, -bb, bb)
        val ft = ArrayList<D3>(); val fb = ArrayList<D3>(); for (xx in xs) for (zz in listOf(-gz, gz)) { ft += f.p(xx, hh + tfp + 0.1, zz); fb += f.p(xx, -hh - tfp - 0.1, zz) }
        val b1 = StBolts.build(ft, -yd, bolt, tfp + tf + 2, qi); val b2 = StBolts.build(fb, yd, bolt, tfp + tf + 2, qi)
        val hw = hh - tf - or(ma.r, 10.0) - 5; val nr = max(1, jsInt(n("wrows")))
        val w1 = f.box(-lh, lh, -hw, hw, tw / 2, tw / 2 + twp); val w2 = f.box(-lh, lh, -hw, hw, -tw / 2 - twp, -tw / 2)
        val rows = if (nr <= 1) listOf(0.0) else List(nr) { i -> (-hw + e) + ((hw - e) - (-hw + e)) * i / (nr - 1) }
        val bw = ArrayList<D3>(); for (xx in xs) for (y in rows) bw += f.p(xx, y, tw / 2 + twp + 0.1)
        val b3 = StBolts.build(bw, -z, bolt, 2 * twp + tw + 2, qi)
        for (hc in b1.holes + b2.holes + b3.holes) { x.cut(x.aId, hc); x.cut(x.bId!!, hc) }
        val fh = b1.holes + b2.holes
        x.plate("FLANGE PLATE", top, tfp, "PL" + jsStr(qi["tfp"]), fh); x.plate("FLANGE PLATE", bot, tfp, "PL" + jsStr(qi["tfp"]), fh)
        x.plate("WEB PLATE", w1, twp, "PL" + jsStr(qi["twp"]), b3.holes); x.plate("WEB PLATE", w2, twp, "PL" + jsStr(qi["twp"]), b3.holes)
        // the outer v58 wrapper's TKB_G stays empty (no Holes tab), so these groups do not go to x.groups
        x.bolts(b1, ft.size, tfp + tf + 2 * bolt); x.bolts(b2, fb.size, tfp + tf + 2 * bolt); x.bolts(b3, bw.size, 2 * twp + tw + 2 * bolt)
    }

    /** v69 angles (L15478): four splice angles in the flange-web corners, bolted through web and flanges. */
    private fun angles(x: FamCtx) {
        val s = frame(x); val ma = s.ma; val f = s.f; val yd = s.yd; val xd = s.xd; val gap = x.n("gap"); val bolt = x.n("bolt")
        x.cut(x.aId, f.box(-gap / 2, BIG, -BIG / 10, BIG / 10, -BIG / 10, BIG / 10)); x.cut(x.bId!!, f.box(-BIG, gap / 2, -BIG / 10, BIG / 10, -BIG / 10, BIG / 10))
        val hh = ma.h / 2; val tf = ma.tf; val tw = ma.tw / 2; val rr = or(ma.r, 10.0)
        val leg = minOf(90.0, floor((ma.b / 2 - ma.tw / 2 - rr) / 5) * 5, floor((ma.h / 2 - ma.tf - rr) / 5) * 5); val ta = max(8.0, floor(leg / 10 + 0.5))
        val nc = max(1, jsInt(x.n("cols"))); val pi = x.n("pitch"); val e = x.n("e"); val lh = gap / 2 + e + (nc - 1) * pi + e
        val xs = ArrayList<Double>(); for (sg in listOf(-1, 1)) for (i in 0 until nc) xs += sg * (gap / 2 + e + i * pi)
        val angs = ArrayList<List<Mesh>>(); val c = rr * 0.75
        // the root chamfer (c = 0.75·r) can pass beyond the inner corner (c > 2·ta): v69's Manifold extrude fills the self-crossing
        // outline by the positive winding rule, which keeps the two legs and drops the corner (two loops here)
        val loops = if (c > 2 * ta) listOf(listOf(doubleArrayOf(c, 0.0), doubleArrayOf(leg, 0.0), doubleArrayOf(leg, ta), doubleArrayOf(c - ta, ta)), listOf(doubleArrayOf(ta, c - ta), doubleArrayOf(ta, leg), doubleArrayOf(0.0, leg), doubleArrayOf(0.0, c)))
            else listOf(listOf(doubleArrayOf(c, 0.0), doubleArrayOf(leg, 0.0), doubleArrayOf(leg, ta), doubleArrayOf(ta, ta), doubleArrayOf(ta, leg), doubleArrayOf(0.0, leg), doubleArrayOf(0.0, c)))
        for (sy in listOf(1, -1)) for (sz in listOf(1, -1)) angs += loops.map { l ->
            val pts = l.map { p -> doubleArrayOf(sy * (hh - tf - rr - p[1]) + sy * rr, sz * (tw + rr + p[0]) - sz * rr) }
            Prism.extrude(Region(pts), 2 * lh, Prism.Frame(f.p(-lh, 0.0, 0.0), yd, f.z, xd))
        }
        val gyW = hh - tf - rr - leg * 0.55; val gzF = tw + rr + leg * 0.55
        val bw = ArrayList<D3>(); val bt = ArrayList<D3>(); val bo = ArrayList<D3>()
        for (xx in xs) { for (sy in listOf(1, -1)) bw += f.p(xx, sy * gyW, tw + ta + 0.1)
            for (sy in listOf(1, -1)) for (sz in listOf(1, -1)) (if (sy > 0) bt else bo) += f.p(xx, sy * (hh + 0.1), sz * gzF) }
        val gW = x.group(bw, -f.z, bolt, 2 * ta + 2 * tw + 2); val gT = x.group(bt, -yd, bolt, tf + ta + 2); val gB = x.group(bo, yd, bolt, tf + ta + 2)
        val holes = gW.holes + gT.holes + gB.holes; for (hc in holes) { x.cut(x.aId, hc); x.cut(x.bId!!, hc) }
        val prof = "L${JsNumber.format(floor(leg + 0.5))}*${JsNumber.format(floor(ta + 0.5))}"
        angs.forEach { x.parts += ConnPart("SPLICE ANGLE", it, prof = prof, minus = holes) }
        x.bolts(gW, bw.size, 2 * ta + ma.tw + 2 * bolt); x.bolts(gT, bt.size, tf + ta + 2 * bolt); x.bolts(gB, bo.size, tf + ta + 2 * bolt)
    }

    /** v69 stEnvelope(st, grow) (L14883). */
    private fun envelope(x: FamCtx, st: JsonObject, grow: Double): Mesh? {
        val sec = Sections.of(x.profiles.lookup(st.str("prof") ?: "")) ?: return null; val m = Joints.mf(st, x.profiles)
        val l = m.c2.distanceTo(m.c1) + st.num("dx1") + st.num("dx2"); var outer = sec.outer
        if (grow != 0.0) { val cx = outer.sumOf { it[0] } / outer.size; val cy = outer.sumOf { it[1] } / outer.size
            outer = outer.map { q -> val dx = q[0] - cx; val dy = q[1] - cy; val ll = hypot(dx, dy).let { if (it == 0.0) 1.0 else it }; doubleArrayOf(q[0] + dx / ll * grow, q[1] + dy / ll * grow) } }
        return Prism.extrude(Region(outer), l + 2 * grow, Prism.Frame(m.c1.add(m.a, -(st.num("dx1") + grow)), m.es, m.eh, m.a))
    }

    /** v69 stub splice (L15516): a stub of the beam welded to the main part, spliced to the beam with flange and web plates. */
    private fun stub(x: FamCtx, b: JsonObject, bId: String) {
        val j = Joints.joint(x.a, b, x.profiles); val bp = j.b; val p1 = b.vec("p1"); val p2 = b.vec("p2")
        val near = if (p1.distanceTo(j.e) <= p2.distanceTo(j.e)) p1 else p2; val x0 = j.xFace; val ls = or(x.n("Ls"), 500.0); val gap = x.n("gap"); val xj = x0 + ls + gap / 2
        val q0 = near.add(j.x, x0); val q1 = near.add(j.x, x0 + ls)
        val stubSt = JsonObject(LinkedHashMap(b).apply { put("p1", jsArr(q0.x, q0.y, q0.z)); put("p2", jsArr(q1.x, q1.y, q1.z)); put("dx1", com.solidsketchapp.steel.model.jsNum(0.0)); put("dx2", com.solidsketchapp.steel.model.jsNum(0.0)) })
        val stub = StBuild(x.profiles).build(stubSt); val env = runCatching { envelope(x, x.a, 1.0) }.getOrNull()
        for (m in j.cutEnd(x0 + ls + gap)) x.cut(bId, m)
        val hh = bp.h / 2; val bb = bp.b / 2; val tf = bp.tf; val tw = bp.tw / 2; val tfp = x.n("tfp"); val twp = x.n("twp"); val e = x.n("e"); val pi = x.n("pitch"); val nc = max(1, jsInt(x.n("cols"))); val bolt = x.n("bolt")
        val lh = gap / 2 + e + (nc - 1) * pi + e; val xs = ArrayList<Double>(); for (sg in listOf(-1, 1)) for (i in 0 until nc) xs += xj + sg * (gap / 2 + e + i * pi)
        val gz = max(bb * 0.6, tw + or(bp.r, 10.0) + 1.2 * d0Of(bolt))
        val top = j.box(xj - lh, xj + lh, hh, hh + tfp, -bb, bb); val bot = j.box(xj - lh, xj + lh, -hh - tfp, -hh, -bb, bb)
        val ft = ArrayList<D3>(); val fb = ArrayList<D3>(); for (xx in xs) for (z in listOf(-gz, gz)) { ft += j.p(xx, hh + tfp + 0.1, z); fb += j.p(xx, -hh - tfp - 0.1, z) }
        val b1 = x.group(ft, -j.y, bolt, tfp + tf + 2); val b2 = x.group(fb, j.y, bolt, tfp + tf + 2)
        val hw = hh - tf - or(bp.r, 10.0) - 5; val nr = max(1, jsInt(x.n("wrows")))
        val w1 = j.box(xj - lh, xj + lh, -hw, hw, tw, tw + twp); val w2 = j.box(xj - lh, xj + lh, -hw, hw, -tw - twp, -tw)
        val bw = ArrayList<D3>(); for (xx in xs) for (i in 0 until nr) bw += j.p(xx, if (nr == 1) 0.0 else -hw + e + (2 * hw - 2 * e) * i / (nr - 1), tw + twp + 0.1)
        val b3 = x.group(bw, -j.zb, bolt, 2 * twp + 2 * tw + 2)
        val holes = b1.holes + b2.holes + b3.holes; for (hc in holes) x.cut(bId, hc)
        x.parts += ConnPart("STUB", listOf(stub), prof = b.str("prof"), minus = listOfNotNull(env) + holes)
        x.plate("FLANGE PLATE", top, tfp, minus = holes); x.plate("FLANGE PLATE", bot, tfp, minus = holes)
        x.plate("WEB PLATE", w1, twp, minus = holes); x.plate("WEB PLATE", w2, twp, minus = holes)
        x.bolts(b1, ft.size, tfp + bp.tf + 2 * bolt); x.bolts(b2, fb.size, tfp + bp.tf + 2 * bolt); x.bolts(b3, bw.size, 2 * twp + bp.tw + 2 * bolt)
        x.weld("Stub to main part (all round)", 8.0, or(x.profiles.lookup(b.str("prof") ?: "")?.P, 1.0) * 1000)
    }
}
