package com.solidsketchapp.steel.conn

import com.solidsketchapp.steel.geom.D3
import com.solidsketchapp.steel.geom.Sections
import com.solidsketchapp.steel.geom.StBuild.Companion.str
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt

/**
 * C02h · Eaves haunch (102), haunch plates only (web v70 FAM.EH; proof docs/steel/PARITY.md "C02h"). Main part = column, secondary =
 * rafter. Tekla 102's Haunch length (horizontal, from the column's centre line to where the haunch flange meets the rafter's bottom
 * flange) and Picture dimension 5 (the haunch plate's plumb depth from the rafter flange, at the end plate); Vert haunch pl t, Horiz
 * haunch pl t and width. The haunch starts at the back face of the End plate (144) joining the same column and rafter (column face +
 * gap + shim / front + plate), else at the column face. Web = triangle under the rafter's underside; flange under its lower edge, ends
 * cut plumb. R12: 48 / 48 eaves with length 2,000 and depth 82.91.
 */
object FamEH {
    fun gen(x: FamCtx) {
        val bSt = x.b ?: run { x.nullResult = true; return }
        val mc = Joints.mf(x.a, x.profiles); val m = Joints.mf(bSt, x.profiles); val z = D3.Z
        var hd = m.a.add(z, -(m.a dot z)); if (hd.length() < 1e-6) { x.nullResult = true; return }; hd = hd.normalize()
        val col = mc.c1; val far = if (((m.c1 - col) dot hd) > ((m.c2 - col) dot hd)) m.c1 else m.c2; if (((far - col) dot hd) < 0) hd = -hd
        val dz = z.add(m.a, -(m.a dot z)).normalize()
        val lo = Sections.of(x.profiles.lookup(bSt.str("prof") ?: ""))?.outer?.minOf { q -> (m.es dot dz) * q[0] + (m.eh dot dz) * q[1] } ?: (-m.h / 2)
        val u0 = m.c1.add(dz, lo); val ad = (m.a dot hd).let { if (it == 0.0) 1e-9 else it }
        fun under(xx: Double) = u0.add(m.a, (xx - ((u0 - col) dot hd)) / ad)
        var xs = abs(mc.eh dot hd) * mc.h / 2 + abs(mc.es dot hd) * mc.b / 2
        x.conns.firstOrNull { k -> k.id != x.conn?.id && k.type == "T144" && k.parts.getOrNull(0) == x.aId && k.parts.getOrNull(1) == x.bId }?.let { ep ->
            val e = LinkedHashMap(Fam.DEF.getValue("EP")).apply { putAll(ep.q) }
            fun n(k: String) = (e[k] as? JsonPrimitive)?.let { if (it.isString && it.content.isBlank()) 0.0 else com.solidsketchapp.steel.geom.StBuild.jsNum(it) }?.takeIf { !it.isNaN() } ?: 0.0
            fun t(k: String) = jsTruthy(e[k])
            xs += n("gap") + n("shim") + (if (t("front") || t("double") || t("stub") || t("stiffT")) max(n("tp"), 10.0) else 0.0) + n("tp")
        }
        val len = x.n("hlen"); val dep = x.n("hdep"); if (!(len > xs + 1) || !(dep > 0)) { x.nullResult = true; return }
        val n0 = under(xs); val fp = under(len); val n1 = n0.add(z, -dep)
        val o = col.add(z, -(col dot z)); val y = (hd cross z).normalize(); val zc = (m.c1 - o) dot y
        val f2 = Joints.Frame3(o.add(y, zc), hd, z); fun p2(p: D3) = doubleArrayOf((p - o) dot hd, p dot z)
        val a0 = p2(n0); val a1 = p2(fp); val a2 = p2(n1); val tw = x.n("tw"); val tf = x.n("tf"); val bf = x.n("bf")
        fun name(k: String, d: String) = (x.q[k] as? JsonPrimitive)?.contentOrNull?.ifEmpty { null } ?: d
        x.plate(name("nweb", "Web"), f2.poly(listOf(a0, a1, a2), -tw / 2, tw / 2), tw, "PL" + com.solidsketchapp.core.api.JsNumber.format(tw))
        val sl = (a1[1] - a2[1]) / (a1[0] - a2[0]); val tv = tf * sqrt(1 + sl * sl)
        x.plate(name("nfl", "Bottom flange"), f2.poly(listOf(a2, a1, doubleArrayOf(a1[0], a1[1] - tv), doubleArrayOf(a2[0], a2[1] - tv)), -bf / 2, bf / 2), tf, "PL" + com.solidsketchapp.core.api.JsNumber.format(tf))
        val lw = hypot(a1[0] - a0[0], a1[1] - a0[1]); val lf = hypot(a1[0] - a2[0], a1[1] - a2[1]); val a = x.n("a")
        x.weld("Haunch web to rafter flange", a, 2 * lw); x.weld("Haunch web to end plate", a, 2 * dep); x.weld("Haunch flange to web (around)", a, 2 * lf + 2 * tw)
        x.weld("Haunch flange to end plate", a, bf + (bf - tw)); x.weld("Haunch flange to rafter flange", a, 2 * bf)
    }
}
