package com.solidsketchapp.steel.conn

import com.solidsketchapp.core.api.JsNumber
import com.solidsketchapp.core.api.Mesh
import com.solidsketchapp.steel.geom.D3
import com.solidsketchapp.steel.geom.Prism
import com.solidsketchapp.steel.geom.Region
import com.solidsketchapp.steel.geom.StBuild
import com.solidsketchapp.steel.geom.StBuild.Companion.str
import com.solidsketchapp.steel.model.StGrid
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * CP family: cap plates and seating on a column head (39, 37, 2, 27, 14 two beams, 100 tube, wind column 5) and
 * crossing beams (30, 36). Live chain: v69 FAM.CP (L14990) wrapped by the T39 plate-size version (L15890, FAM_CP23).
 */
object FamCP {
    const val PORTED = true

    /** The family's own settings (v69 `q` inside FAM.CP; FAM.JO calls it with another q, the collectors keep famGeom's). */
    private class Q(val o: JsonObject) {
        fun n(k: String): Double { val e = o[k] ?: return 0.0; return if (e is JsonNull) 0.0 else StBuild.jsNum(e) }
        fun t(k: String) = jsTruthy(o[k])
        /** JavaScript `'' + q.k`. */
        fun js(k: String): String = jsStr(o[k])
    }

    fun gen(x: FamCtx) = gen(x, x.q)

    /** FAM.CP with the settings [q] (FAM.JO: `FAM.CP(Object.assign({}, X, { q }))`); parts, cuts, bolts and welds go to [x]. */
    fun gen(x: FamCtx, q: JsonObject) {
        val Q = Q(q); val ma = Joints.mf(x.a, x.profiles); val b = x.b
        if (b == null || !(Q.n("Lp") > 0) || abs(ma.a.z) < 0.8 || Q.t("cross")) { cp23(x, Q, ma); return }
        // v69 L15890: a Tekla plate size (Lp × Wp) and bolts at xsAt along the beam
        val mb = Joints.mf(b, x.profiles); val top = if (ma.c1.z > ma.c2.z) ma.c1 else ma.c2
        val p = mb.c1.add(mb.a, (top - mb.c1) dot mb.a); val zb = p.z - mb.h / 2; val tp = Q.n("tp")
        val xb = D3(mb.a.x, mb.a.y, 0.0).normalize(); val yb = (D3.Z cross xb).normalize(); val o = D3(top.x, top.y, zb - tp)
        val l = Q.n("Lp") / 2; val w = (if (Q.t("Wp")) Q.n("Wp") else mb.b) / 2
        val cp = Joints.box3(o, xb, yb, -l, l, -w, w, 0.0, tp); x.cut(x.aId, Joints.box3(o, xb, yb, -l * 5, l * 5, -w * 5, w * 5, 0.0, 50000.0))
        // String(q.xsAt || '').trim().split(/[\s,]+/).map(Number).filter(isFinite): an empty list gives [0] (Number('') = 0)
        val xs = (if (Q.t("xsAt")) Q.js("xsAt") else "").trim().split(Regex("""[\s,]+""")).map { StGrid.jsNumber(it) }.filter { it.isFinite() }
        val gy = if (Q.t("gy")) Q.n("gy") else 30.0; val bl = ArrayList<D3>()
        for (xx in xs.ifEmpty { listOf(-l + 40, l - 40) }) for (y in listOf(-gy, gy)) bl += D3(top.x, top.y, zb - tp - 0.1).add(xb, xx).add(yb, y)
        val grip = tp + mb.tf + 2; val bb = x.group(bl, D3.Z, Q.n("bolt"), grip); for (hc in bb.holes) x.cut(x.bId!!, hc)
        x.bolts(bb, bl.size, grip + 2 * Q.n("bolt"))
        x.plate("CAP PLATE", cp, tp, "PL" + Q.js("tp") + "*" + JsNumber.format(floor(2 * w + 0.5)), minus = bb.holes)
        x.weld("Cap plate to column", Q.n("a"), paint(x) * 1000)
    }

    /** (MA.p.P || 1.2): the column's paint surface per metre. */
    private fun paint(x: FamCtx) = x.profiles.lookup(x.a.str("prof") ?: "")?.P?.takeIf { it != 0.0 && !it.isNaN() } ?: 1.2

    /** v69 FAM.CP (L14990). */
    private fun cp23(x: FamCtx, q: Q, ma: MemberFrame) {
        val bs = listOfNotNull(x.b?.let { it to x.bId!! }, x.b2?.let { it to x.b2Id!! }); if (bs.isEmpty()) throw IllegalStateException("not built")
        val vert = abs(ma.a.z) > 0.8; val bolt = q.n("bolt")
        if (!vert || q.t("cross")) {   // crossing beams: a seating plate in the gap, bolts through both flanges
            val mb = Joints.mf(bs[0].first, x.profiles); val pA = ma.c1; val pB = mb.c1
            if ((ma.a cross mb.a).length() < 1e-6) throw IllegalStateException("not built")
            val w0 = pA - pB; val a1 = ma.a; val a2 = mb.a; val bb = a1 dot a2; val d = a1 dot w0; val e = a2 dot w0; val den = 1 - bb * bb
            val s1 = (bb * e - d) / den; val s2 = (e - bb * d) / den
            val PA = pA.add(a1, s1); val PB = pB.add(a2, s2); val lowA = PA.z <= PB.z
            val lo = if (lowA) ma else mb; val hi = if (lowA) mb else ma; val Plo = if (lowA) PA else PB; val Phi = if (lowA) PB else PA
            val ztop = Plo.z + lo.h / 2; val zbot = Phi.z - hi.h / 2; val gap = zbot - ztop
            val fy = (D3.Z cross hi.a).normalize(); val wx = lo.b / 2; val wy = hi.b / 2
            if (gap > 0.5) x.plate("SEATING PLATE", Joints.box3(D3(Phi.x, Phi.y, ztop), hi.a, fy, -wx - 10, wx + 10, -wy - 10, wy + 10, 0.0, gap), gap)
            val gx = max(wx * 0.55, 35.0); val gy = max(min(wy, wx) * 0.55, 35.0); val bl = ArrayList<D3>()
            for (sx in listOf(-1, 1)) for (sy in listOf(-1, 1)) bl += D3(Phi.x, Phi.y, zbot + hi.tf + 0.1).add(hi.a, sx * gx).add(fy, sy * gy)
            val grip = hi.tf + max(0.0, gap) + lo.tf + 2; val g = x.group(bl, D3(0.0, 0.0, -1.0), bolt, grip)
            for (hc in g.holes) { x.cut(x.aId, hc); x.cut(bs[0].second, hc) }
            x.bolts(g, 4, grip + 2 * bolt); return
        }
        val top = if (ma.c1.z > ma.c2.z) ma.c1 else ma.c2; val mbs = bs.map { Joints.mf(it.first, x.profiles) }
        var zb = Double.POSITIVE_INFINITY
        for (mb in mbs) { val p = mb.c1.add(mb.a, (top - mb.c1) dot mb.a); zb = min(zb, p.z - mb.h / 2) }
        if (!zb.isFinite()) throw IllegalStateException("not built")
        val tp = q.n("tp"); val xc = D3(ma.eh.x, ma.eh.y, 0.0).normalize(); val yc = (D3.Z cross xc).normalize(); val o = D3(top.x, top.y, zb - tp)
        val l = ma.h / 2 + q.n("ext"); val w = ma.b / 2 + q.n("ext")
        val cp: Mesh = if (ma.kind == "O") Prism.extrude(Region(List(32) { i -> doubleArrayOf(l * cos(i * PI / 16), l * sin(i * PI / 16)) }), tp, Prism.Frame(o, xc, yc, D3.Z))
            else Joints.box3(o, xc, yc, -l, l, -w, w, 0.0, tp)
        x.cut(x.aId, Joints.box3(o, xc, yc, -l * 5, l * 5, -w * 5, w * 5, 0.0, 50000.0))
        val holes = ArrayList<Mesh>(); val d0 = bolt + (if (bolt <= 24) 2 else 3)
        for (k in bs.indices) {
            val mb = mbs[k]; val p = mb.c1.add(mb.a, (top - mb.c1) dot mb.a); val side = (D3.Z cross mb.a).normalize()
            val gy = if (q.t("gy")) q.n("gy") else (if (2 * min(mb.b / 2 - 30, 60.0) < 2.5 * bolt) 0.0 else min(mb.b / 2 - 30, 60.0))
            val extA = abs(mb.a dot xc) * l + abs(mb.a dot yc) * w; val gx = if (q.t("gx")) q.n("gx") else min(extA - 1.4 * d0, 0.75 * extA) / 0.6
            val xs = if (bs.size > 1) listOf(Math.signum((mb.c1 + mb.c2) * 0.5 - top dot mb.a) * gx * 0.5) else listOf(-gx * 0.6, gx * 0.6)
            val bl = ArrayList<D3>(); for (xx in xs) for (y in (if (gy > 0) listOf(-gy, gy) else listOf(0.0))) bl += D3(p.x, p.y, zb - tp - 0.1).add(mb.a, xx).add(side, y)
            val grip = tp + mb.tf + 2; val g = x.group(bl, D3.Z, bolt, grip)
            for (hc in g.holes) { holes += hc; x.cut(bs[k].second, hc) }; x.bolts(g, bl.size, grip + 2 * bolt)
            if (q.t("stiff")) for (sg in listOf(-1, 1)) {
                val fb = Joints.Frame3(D3(p.x, p.y, zb), mb.a, D3.Z); val ts = q.n("ts"); val wz = mb.b / 2 - mb.tw / 2 - 5
                for (zs in listOf(mb.tw / 2, -mb.tw / 2 - wz))
                    x.plate("STIFFENER", fb.box(sg * (ma.h / 2 - ma.tf / 2) - ts / 2, sg * (ma.h / 2 - ma.tf / 2) + ts / 2, mb.tf, mb.h - mb.tf, zs, zs + wz), ts)
            }
        }
        x.plate("CAP PLATE", cp, tp, minus = holes); x.weld("Cap plate to column", q.n("a"), paint(x) * 1000)
    }

    /** JavaScript String(v) of a q value. */
    private fun jsStr(e: JsonElement?): String = when {
        e == null -> "undefined"; e is JsonNull -> "null"
        e is JsonPrimitive && !e.isString -> e.content.toDoubleOrNull()?.let { if (it.isFinite()) JsNumber.format(it) else e.content } ?: e.content
        e is JsonPrimitive -> e.content; else -> e.toString()
    }
}
