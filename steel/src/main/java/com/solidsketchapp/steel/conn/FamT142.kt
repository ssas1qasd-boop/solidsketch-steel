package com.solidsketchapp.steel.conn

import com.solidsketchapp.steel.geom.D3
import com.solidsketchapp.steel.geom.StBuild
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * v70 T142 · Two sided end plate (142) (web `web/v70/t142.js`, section `// == T142 ==`; docs/steel/PARITY.md "T142"): Tekla's one bolt
 * group from end plate 1 through the main part to end plate 2 (conn_142_help: "One bolt group goes through all the three parts"; video 1
 * _Au1wpEDNzw: long bolts across both flanges of an HEB300). End plate 1 on the main part's face (its face normal, not the beam's direction),
 * edges by Picture 1 / 2 or from the bolt group (edge distance 3; h given: centred on the bolts); end plate 2 on the far face, by Picture
 * 3 / 4 or mirroring end plate 1; Sort secondaries by profile height; haunch plates per secondary (End plate 144's C09 plates, [Fam.haunchPlates]).
 * Only for components whose stored settings carry `bgrp = 'one'` (made after v70); older ones keep v69's two groups ([Fam.ep]).
 */
object FamT142 {
    private const val BIG = 60000.0   // v69 v49Big

    fun applies(x: FamCtx): Boolean = x.conn?.type == "T142" && (x.conn.q["bgrp"] as? JsonPrimitive)?.content == "one" && x.b != null && x.b2 != null

    /** web `num(v, d)`: blank / missing / not a number → d. */
    private fun num(x: FamCtx, k: String, d: Double): Double { val e = x.q[k] as? JsonPrimitive ?: return d; if (e.isString && e.content.isBlank()) return d
        val v = if (e.isString) com.solidsketchapp.steel.model.StGrid.jsNumber(e.content) else (e.content.toDoubleOrNull() ?: Double.NaN); return if (v.isFinite()) v else d }

    private class Side(val j: Joints.Joint, val bp: MemberFrame, val u: D3, val o: D3, val fy: D3, val fz: D3, val ff: Joints.Frame3, val cs: List<Triple<Double, Double, D3>>,
                       val yT: Double, val yB: Double, val zC: Double, private val kc: Double, private val cg: Double) {
        fun cl(y: Double) = if (j.cope) min(max(y, (j.yBot + cg) / kc), (j.yTop - cg) / kc) else y
    }

    private fun side(x: FamCtx, bm: JsonObject): Side {
        val j = Joints.joint(x.a, bm, x.profiles); val bp = j.b; val m = j.m
        val fl = abs(j.uDir dot m.eh) >= abs(j.uDir dot m.es); val n0 = if (fl) m.eh else m.es
        val u = (n0 * (if ((j.uDir dot n0) >= 0) 1.0 else -1.0)).normalize(); val xu = (j.x dot u).let { if (it == 0.0) 1.0 else it }
        val pc = m.c1.add(m.a, (j.e - m.c1) dot m.a); val o = pc.add(u, if (fl) m.h / 2 else m.tw / 2)
        var fy = bp.eh.add(u, -(bp.eh dot u)); if (fy.length() < 1e-6) fy = j.y.add(u, -(j.y dot u)); fy = fy.normalize(); val fz = (u cross fy).normalize()
        fun onFace(p: D3): D3 { val s = ((p - o) dot u) / xu; return p.add(j.x, -s) }
        val cs = ArrayList<Triple<Double, Double, D3>>()
        for (sy in listOf(-1, 1)) for (sz in listOf(-1, 1)) { val p = onFace(o.add(bp.eh, sy * bp.h / 2).add(bp.es, sz * bp.b / 2)); cs += Triple((p - o) dot fy, (p - o) dot fz, p) }
        val yT = cs.maxOf { it.first }; val yB = cs.minOf { it.first }; val zC = (cs.maxOf { it.second } + cs.minOf { it.second }) / 2
        return Side(j, bp, u, o, fy, fz, Joints.Frame3(o, u, fy), cs, yT, yB, zC, max(0.2, abs(j.y dot fy)), max(10.0, m.r + 1))
    }

    /** Returns false when the secondaries are not on opposite faces (then v69's End plate path runs). */
    fun gen(x: FamCtx): Boolean {
        var b1 = x.b!!; var b1Id = x.bId!!; var b2 = x.b2!!; var b2Id = x.b2Id!!
        if (StBuild.jsNum(x.q["srt"]) != 0.0 && Joints.mf(b2, x.profiles).h > Joints.mf(b1, x.profiles).h + 1e-9) { val t = b1; b1 = b2; b2 = t; val ti = b1Id; b1Id = b2Id; b2Id = ti }
        val s1 = side(x, b1); val s2 = side(x, b2); val cosA = -(s1.u dot s2.u); if (cosA < 0.2) return false
        val tp = x.n("tp"); val gp = num(x, "gap", 0.0); val sh = num(x, "shim", 0.0); val n0 = gp + sh; val n1 = n0 + tp
        val tp2 = num(x, "tp2", tp); val gp2 = num(x, "gap2", 0.0); val sh2 = num(x, "shim2", 0.0); val m0 = gp2 + sh2; val m1 = m0 + tp2
        val nc = if (jsInt(x.n("cols")) == 4) 4 else 2; val pb = num(x, "epb", Double.NaN); val sd = num(x, "side", 0.0)
        val gEl = x.q["gauge"]; val gBlank = gEl == null || (gEl is JsonPrimitive && gEl.isString && gEl.content == "")
        val gv = if (gBlank) (if (pb > 0 && nc != 4 && pb - 2 * sd > 0) pb - 2 * sd else 90.0) else StBuild.jsNum(gEl)
        val g = gv; val g2 = x.n("gauge2").takeIf { it != 0.0 && !it.isNaN() } ?: gv
        val zs = if (nc == 4) listOf(-g / 2 - g2, -g / 2, g / 2, g / 2 + g2) else listOf(-g / 2, g / 2); val zw = (if (nc == 4) g / 2 + g2 else g / 2) + sd
        var zC = s1.zC; run { val ph = num(x, "posH", 0.0); val gw = zw - sd
            zC = when (x.s("refH")) { "Left" -> zC - s1.bp.b / 2 + ph + gw; "Right" -> zC + s1.bp.b / 2 - ph - gw; else -> zC + ph } }
        val e = num(x, "e", 35.0); val x1 = num(x, "ext", Double.NaN); val x2 = num(x, "extb", Double.NaN)
        val yh0 = if (x1.isFinite()) s1.cl(s1.yT + x1) else Double.NaN; val yl0 = if (x2.isFinite()) s1.cl(s1.yB - x2) else Double.NaN
        val hH = (if (yh0.isFinite()) yh0 else s1.yT) - (if (yl0.isFinite()) yl0 else s1.yB)
        var nr = if (x.t("rows")) jsInt(x.n("rows")) else Fam.tkRows(hH); val ps = Fam.pitches(x, nr); if (x.s("pitches").trim().isNotEmpty()) nr = ps.size + 1
        val sp = Fam.span(ps, nr, x); val pv = num(x, "posV", Double.NaN)
        val top = when (x.s("refV")) {
            "Middle" -> (s1.yT + s1.yB) / 2 + sp / 2 + (if (pv.isFinite()) pv else 0.0)
            "Below" -> (if (pv.isFinite()) s1.yB + pv else (if (yl0.isFinite()) yl0 else s1.yB) + e) + sp
            else -> if (pv.isFinite()) s1.yT - pv else (if (yh0.isFinite()) yh0 else s1.yT) - e }
        val ys = ArrayList<Double>()
        if (x.s("pitches").trim().isEmpty() && (x.n("pitch") == 0.0 || x.n("pitch").isNaN())) { val lo = (if (yl0.isFinite()) yl0 else s1.yB) + e; for (i in 0 until nr) ys += top - (top - lo) * i / max(1, nr - 1) }
        else { var y = top; for (i in 0 until nr) { ys += y; y -= (ps.getOrNull(i)?.takeIf { it != 0.0 } ?: ps.lastOrNull()?.takeIf { it != 0.0 } ?: 70.0) } }
        val yLo = ys.last(); val hP = num(x, "eph", Double.NaN); val yh: Double; val yl: Double
        if (hP > 0) { yh = if (yh0.isFinite()) yh0 else if (yl0.isFinite()) yl0 + hP else (top + yLo) / 2 + hP / 2; yl = yh - hP }
        else { yh = if (yh0.isFinite()) yh0 else top + e; yl = if (yl0.isFinite()) yl0 else yLo - e }
        val z0 = if (pb > 0) zC - pb / 2 else zC - zw; val z1 = if (pb > 0) zC + pb / 2 else zC + zw
        val tkL = x.s("blrule") == "tekla"; val bl = ArrayList<D3>(); for (y in ys) for (z in zs) bl += s1.ff.p(n1 + (if (tkL) 0.0 else 0.1), y, zC + z)
        val f1 = s1.ff.p(0.0, ys[0], zC + zs[0]); val dm = ((f1 - s2.o) dot s2.u) / (s1.u dot s2.u)
        val grip = n1 + dm + m1 / cosA + (if (tkL) 0.0 else 2.0)
        val ax = -s1.u; var y2a = Double.POSITIVE_INFINITY; var y2b = Double.NEGATIVE_INFINITY; var z2a = Double.POSITIVE_INFINITY; var z2b = Double.NEGATIVE_INFINITY
        for (y in ys) for (z in zs) { val qq = s1.ff.p(0.0, y, zC + z).add(ax, dm) - s2.o; val yy = qq dot s2.fy; val zz = qq dot s2.fz; y2a = min(y2a, yy); y2b = max(y2b, yy); z2a = min(z2a, zz); z2b = max(z2b, zz) }
        val x3 = num(x, "extb2", Double.NaN); val x4 = num(x, "ext2", Double.NaN); val hP2 = num(x, "eph2", Double.NaN); val pb2 = num(x, "epb2", Double.NaN)
        var yh2 = if (x4.isFinite()) s2.cl(s2.yT + x4) else y2b + (yh - top); var yl2 = if (x3.isFinite()) s2.cl(s2.yB - x3) else y2a - (yLo - yl)
        if (hP2 > 0) { if (!x4.isFinite()) yh2 = if (x3.isFinite()) yl2 + hP2 else (y2a + y2b) / 2 + (yh - (top + yLo) / 2); yl2 = yh2 - hP2 }
        val zc2 = (z2a + z2b) / 2; val hw2 = if (pb2 > 0) pb2 / 2 else (z1 - z0) / 2
        val bb = x.group(bl, ax, x.n("bolt"), grip)
        for (hc in bb.holes) x.cut(x.aId, hc)
        fun ends(s: Side, id: String, m: Double) { x.cut(id, s.ff.box(-BIG, m, -BIG, BIG, -BIG, BIG)); val j = s.j
            if (j.cope) { val hb = s.bp.h / 2
                if (hb > j.yTop - 10) x.cut(id, j.box(-BIG, j.xFace + j.copeL, j.yTop - 10, BIG / 10, -BIG / 10, BIG / 10))
                if (-hb < j.yBot + 10) x.cut(id, j.box(-BIG, j.xFace + j.copeL, -BIG / 10, j.yBot + 10, -BIG / 10, BIG / 10)) } }
        ends(s1, b1Id, n1); ends(s2, b2Id, m1)
        x.plate("END PLATE", s1.ff.box(n0, n1, yl, yh, z0, z1), tp, minus = bb.holes)
        if (sh != 0.0) x.plate("SHIM", s1.ff.box(n0 - sh, n0, yl, yh, z0, z1), sh, minus = bb.holes)
        x.plate("END PLATE", s2.ff.box(m0, m1, yl2, yh2, zc2 - hw2, zc2 + hw2), tp2, minus = bb.holes)
        if (sh2 != 0.0) x.plate("SHIM", s2.ff.box(m0 - sh2, m0, yl2, yh2, zc2 - hw2, zc2 + hw2), sh2, minus = bb.holes)
        x.bolts(bb, bl.size, if (tkL) Fam.tkBoltLen(x.n("bolt"), grip) else grip + 2 * x.n("bolt"))
        for ((s, m) in listOf(s1 to n1, s2 to m1)) { val lp = listOf(s.cs[0], s.cs[1], s.cs[3], s.cs[2]).map { it.third.add(s.u, m) }; x.weld("Beam to end plate", x.n("a"), 2 * (s.yT - s.yB) + 4 * s.bp.b, lp) }
        // haunch plates: End plate 144's C09 plates (web hp70), one call per secondary with that side's plate edges and settings
        fun haunch(s: Side, bm: JsonObject, k1: Int, k2: Int, hpc: String, t: Double, gpp: Double, shh: Double, yy1: Double, yy0: Double) {
            if (hpc != "auto" && hpc != "both") return
            val q = LinkedHashMap<String, JsonElement>(x.q); fun put(k: String, v: Double) { q[k] = com.solidsketchapp.steel.model.jsNum(v) }
            put("tp", t); put("gap", gpp); put("shim", shh); for (k in listOf("front", "double", "stub", "stiffT")) put(k, 0.0); put("ext", yy1 - s.yT); put("extb", s.yB - yy0); q["hpc"] = JsonPrimitive(hpc)
            put("hpt1", num(x, "hpt$k1", 0.0)); q["hpb1"] = x.q["hpb$k1"] ?: JsonPrimitive(""); q["hph1"] = x.q["hph$k1"] ?: JsonPrimitive("")
            put("hpt2", num(x, "hpt$k2", 0.0)); q["hpb2"] = x.q["hpb$k2"] ?: JsonPrimitive(""); q["hph2"] = x.q["hph$k2"] ?: JsonPrimitive("")
            for (k in listOf("hc1", "hc2", "hc3", "hc4")) put(k, num(x, k, 0.0))
            val y = FamCtx(JsonObject(q), x.a, x.aId, x.b, x.bId, x.b2, x.b2Id, x.profiles, x.pt, x.conn, x.conns, x.stOf)
            Fam.haunchPlates(y, bm, hpc); x.parts += y.parts; x.welds += y.welds
        }
        haunch(s1, b1, 1, 2, x.s("hpc"), tp, gp, sh, yh, yl); haunch(s2, b2, 3, 4, x.s("hpc2"), tp2, gp2, sh2, yh2, yl2)
        return true
    }
}
