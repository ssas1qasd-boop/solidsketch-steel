package com.solidsketchapp.steel.conn

import com.solidsketchapp.core.api.Mesh
import com.solidsketchapp.steel.geom.D3
import com.solidsketchapp.steel.geom.Prism
import com.solidsketchapp.steel.geom.Region
import com.solidsketchapp.steel.geom.Sections
import com.solidsketchapp.steel.geom.StBuild.Companion.num
import com.solidsketchapp.steel.geom.StBuild.Companion.str
import kotlinx.serialization.json.JsonObject
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/**
 * WF family: welded connections, fitting, notches, mitre, saddle, crossing, chamfer (13, 4, 23, 31w, 44w, 123, 128, 183,
 * 49, 76, 1006, 9, 192-195, 41, 1057, 85, and Ttc which v69 moves to WF as a mitre, L15461). Live definition: v69 FAM.WF (L15132).
 */
object FamWF {
    const val PORTED = true
    private const val BIG = 60000.0   // v69 v49Big
    /** v69 FAM_DEF.WF (L14858). */
    internal val DEF = Fam.obj("kind" to "fit", "gap" to 10, "tp" to 0, "r" to 25, "a" to 6)
    /** The live CONN.T30.def (Seating, family CP) that the crossing kind builds with `{cross: 1}`. */
    private val DEF_T30 = Fam.obj("Lp" to 0, "Wp" to 0, "xsAt" to "", "tp" to 20, "ext" to 25, "gx" to 0, "gy" to 0, "bolt" to 20, "grade" to "8.8", "stiff" to 0, "ts" to 10, "cross" to 1, "a" to 6)

    /** v69 stEnvelope (L14883): the profile's outline (no hollows) along the part, grown by [grow] mm (negative: shrunk). */
    internal fun envelope(x: FamCtx, st: JsonObject, grow: Double = 0.0): Mesh? {
        val sec = Sections.of(x.profiles.lookup(st.str("prof") ?: "")) ?: return null; val m = Joints.mf(st, x.profiles)
        val l = m.c2.distanceTo(m.c1) + st.num("dx1") + st.num("dx2")
        var outer = sec.outer
        if (grow != 0.0) { val cx = outer.sumOf { it[0] } / outer.size; val cy = outer.sumOf { it[1] } / outer.size
            outer = outer.map { q -> val dx = q[0] - cx; val dy = q[1] - cy; val ll = hypot(dx, dy).let { if (it == 0.0) 1.0 else it }; doubleArrayOf(q[0] + dx / ll * grow, q[1] + dy / ll * grow) } }
        return Prism.extrude(Region(outer), l + 2 * grow, Prism.Frame(m.c1.add(m.a, -(st.num("dx1") + grow)), m.es, m.eh, m.a))
    }
    /** `(p.P || 1)`: the profile's paint surface per metre. */
    private fun paint(x: FamCtx, st: JsonObject) = x.profiles.lookup(st.str("prof") ?: "")?.P?.takeIf { it != 0.0 && !it.isNaN() } ?: 1.0
    private fun kindOf(x: FamCtx, st: JsonObject) = x.profiles.lookup(st.str("prof") ?: "")?.kind

    fun gen(x: FamCtx) {
        val k = x.s("kind")
        if (k == "chamfer") {
            val m = Joints.mf(x.a, x.profiles); val pt = x.pt ?: m.c2; val atEnd = pt.distanceTo(m.c2) < pt.distanceTo(m.c1)
            val p = if (atEnd) m.c2 else m.c1; val o = if (atEnd) m.a else -m.a; val r = 30.0
            val f = Joints.Frame3(p, (o + m.eh).normalize(), (o - m.eh).normalize())
            x.cut(x.aId, f.box(m.h / 2 * 0.7071 - r, 5000.0, -5000.0, 5000.0, -5000.0, 5000.0)); return
        }
        val b = x.b ?: throw IllegalStateException("not built"); val bId = x.bId!!
        if (k == "mitre") {
            val ma = Joints.mf(x.a, x.profiles); val mb = Joints.mf(b, x.profiles)
            val w0 = ma.c1 - mb.c1; val bb = ma.a dot mb.a; val dd = ma.a dot w0; val ee = mb.a dot w0; val den = (1 - bb * bb).let { if (it == 0.0) 1e-9 else it }
            val pa = ma.c1.add(ma.a, (bb * ee - dd) / den); val pb = mb.c1.add(mb.a, (ee - bb * dd) / den); val j0 = (pa + pb) * 0.5
            val dA = ((if (ma.c1.distanceTo(j0) > ma.c2.distanceTo(j0)) ma.c1 else ma.c2) - j0).normalize()
            val dB = ((if (mb.c1.distanceTo(j0) > mb.c2.distanceTo(j0)) mb.c1 else mb.c2) - j0).normalize()
            val nb = (dA - dB).normalize(); val y = (if (abs(nb.z) < 0.9) D3.Z cross nb else D3(1.0, 0.0, 0.0) cross nb).normalize()
            val f = Joints.Frame3(j0, nb, y); val tp = if (x.t("tp")) x.n("tp") else 0.0
            x.cut(x.aId, f.box(-BIG, tp / 2, -BIG, BIG, -BIG, BIG)); x.cut(bId, f.box(-tp / 2, BIG, -BIG, BIG, -BIG, BIG))
            if (tp > 0) x.plate("MITRE PLATE", f.box(-tp / 2, tp / 2, -(ma.h / 2 + 20), ma.h / 2 + 20, -(ma.h / 2 + 20), ma.h / 2 + 20), x.n("tp"))
            x.weld("Mitre (CJP)", ma.tf, paint(x, x.a) * 1000); return
        }
        if (k == "cross") {   // famGeom of a T30 (CP crossing) on the same parts: its plates only (no bolts, cuts or welds)
            val y = FamCtx(x.q, x.a, x.aId, x.b, x.bId, x.b2, x.b2Id, x.profiles, x.pt, x.conn, x.conns, x.stOf)
            val ok = try { FamCP.gen(y, DEF_T30); true } catch (e: IllegalStateException) { false }
            if (ok) x.parts += y.parts.filter { it.bolts == null }
            x.groups += y.groups   // stBolts still records into the outer TKB_G (the Holes tab)
            x.weld("Crossing weld", x.n("a"), 400.0); return
        }
        val ma = Joints.mf(x.a, x.profiles); val mb = Joints.mf(b, x.profiles); val ka = kindOf(x, x.a); val tubeMain = ka == "O" || ka == "R"
        if (k == "saddle" || (k == "fit" && tubeMain && kindOf(x, b) == "O")) {
            x.cut(bId, envelope(x, x.a)); val j = Joints.joint(x.a, b, x.profiles)
            for (m in j.cutEnd(j.xFace - ma.h / 2)) x.cut(bId, m); x.weld("Saddle weld", x.n("a"), PI * mb.h); return
        }
        val j = Joints.joint(x.a, b, x.profiles); val bp = j.b; val gap = x.n("gap"); val xg = j.xFace + gap
        for (m in j.cutEnd(xg)) x.cut(bId, m)
        x.weld("Fillet all round", x.n("a"), paint(x, b) * 1000, listOf(j.p(xg, j.yHi, -bp.b / 2), j.p(xg, j.yHi, bp.b / 2), j.p(xg, j.yLo, bp.b / 2), j.p(xg, j.yLo, -bp.b / 2)))
        if (k == "prep" || k == "colweld") for (s in listOf(1, -1)) x.cut(bId, stCyl(j.p(xg, s * (bp.h / 2 - bp.tf), -bp.b), j.zb, x.n("r"), 2 * bp.b, 16))
        if (k == "prep" && !j.cope) for (s in listOf(1, -1))
            x.plate("BACKING BAR", j.box(j.xFace + gap * 0.5, xg + 25, s * (bp.h / 2 - bp.tf), s * (bp.h / 2 - bp.tf - 8), -bp.b / 2 - 10, bp.b / 2 + 10), 8.0)
        if (k == "colweld" && j.flange) { val nn = (j.m.eh cross j.m.es).normalize()
            for (yy in listOf(bp.h / 2 - bp.tf / 2, -(bp.h / 2 - bp.tf / 2))) Fam.stiffPair(x, j.m, (j.p(0.0, yy, 0.0) - j.m.c1) dot nn, max(10.0, bp.tf), 15.0, listOf(1, -1)) }
        if (k == "notch") {
            val cd = j.m.tf + 15; val hb = bp.h / 2; val xn = j.xFace + (j.m.b / 2 + 15)
            x.cut(bId, j.box(-BIG, xn, hb - cd, BIG / 10, -BIG / 10, BIG / 10))
            if (x.conn?.type == "T1006") for (s in listOf(1, -1))
                x.plate("NOTCH STIFFENER", j.box(xn, xn + 150, hb - cd - 10, hb - cd, s * (bp.tw / 2 + bp.r + 1), s * (bp.b / 2 - 5)), 10.0)
        }
    }

    /** FAM.WF with the settings [q] (FAM.TU's saddle: `FAM.WF(Object.assign({}, X, { q }))`); the collectors stay [x]'s. */
    internal fun gen(x: FamCtx, q: JsonObject) {
        val y = FamCtx(q, x.a, x.aId, x.b, x.bId, x.b2, x.b2Id, x.profiles, x.pt, x.conn, x.conns, x.stOf); gen(y)
        x.parts += y.parts; for ((id, l) in y.cuts) x.cuts.getOrPut(id) { ArrayList() } += l; x.groups += y.groups; x.welds += y.welds
    }
    internal fun with(base: JsonObject, vararg kv: Pair<String, Any>) = JsonObject(LinkedHashMap(base).apply { putAll(Fam.obj(*kv)) })
}
