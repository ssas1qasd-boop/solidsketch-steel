package com.solidsketchapp.steel.conn

import com.solidsketchapp.core.api.JsNumber
import com.solidsketchapp.core.api.Mesh
import com.solidsketchapp.steel.data.Profile
import com.solidsketchapp.steel.geom.D3
import com.solidsketchapp.steel.geom.Prism
import com.solidsketchapp.steel.geom.Region
import com.solidsketchapp.steel.geom.StBuild.Companion.str
import com.solidsketchapp.steel.geom.StBuild.Companion.vec
import com.solidsketchapp.steel.model.jsArr
import com.solidsketchapp.steel.model.jsNum
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * HA family: haunches (40, 51, 102 eaves → knee; 106 apex). Live definition: v69 FAM.HA (L15030) builds the v34
 * connection type `knee` or `apex` (stConnGeom L13927 / L13951) with an empty q, i.e. CONN.knee / CONN.apex defaults
 * (L13885 / L13886, apex `a` 5 from L16116; `ph` 0 from L15818, so the v54 apex at L15845 is not used).
 */
object FamHA {
    const val PORTED = true
    /** v69 CONN.knee.def (L13885), CONN.apex.def (L13886 + L15818 + L16116): the inner stConnGeom's q (and stBolts' TKB_Q). */
    private val KNEE = Fam.obj("tp" to 20, "len" to 0, "depth" to 0, "rows" to 4, "bolt" to 20, "grade" to "8.8", "gauge" to 100, "cap" to 15, "stiff" to 12, "a" to 6)
    private val APEX = Fam.obj("ph" to 0, "pw" to 0, "top" to "", "bot" to "", "rowsAt" to "", "hf" to 10, "hb" to 0, "hw" to 8, "tp" to 20, "len" to 900, "depth" to 250, "rows" to 3, "bolt" to 20, "grade" to "8.8", "gauge" to 100, "a" to 5)
    /** v69 S.stGen.span when there is no portal frame: `Math.round(20000 / 20)` is the default haunch length. */
    private const val SPAN = 20000.0

    /** v69 stLF (L13889): s along the horizontal [h], z up, y = Z × h across. */
    private class LF(val o: D3, hd: D3) {
        val h = D3(hd.x, hd.y, 0.0).normalize(); val y = (D3.Z cross h).normalize()
        fun p(s: Double, zz: Double, yy: Double = 0.0) = o.add(h, s).add(D3.Z, zz).add(y, yy)
        /** v69 stPrism: polygon in (s, z), from y1 back to y0 (extrudeRegions takes |y1 − y0|). */
        fun prism(poly: List<DoubleArray>, y0: Double, y1: Double): Mesh = Prism.extrude(Region(poly), abs(y1 - y0), Prism.Frame(p(0.0, 0.0, y1), h, D3.Z, -y))
        fun boxL(s0: Double, s1: Double, z0: Double, z1: Double, y0: Double, y1: Double) = prism(listOf(doubleArrayOf(s0, z0), doubleArrayOf(s1, z0), doubleArrayOf(s1, z1), doubleArrayOf(s0, z1)), y0, y1)
    }
    private fun or(v: Double?, d: Double) = if (v == null || v == 0.0 || v.isNaN()) d else v
    private fun pl(t: Double) = "PL" + JsNumber.format(t)
    private fun q(o: JsonObject, k: String) = com.solidsketchapp.steel.geom.StBuild.jsNum(o[k])

    fun gen(x: FamCtx) {
        val b = x.b ?: throw IllegalStateException("not built"); val bId = x.bId!!
        if (x.s("kind") == "apex") apex(x, b, bId) else knee(x, b, bId)
    }

    /** The inner stConnGeom's BOLTS part after both v58 wrappers: pts / ax / n / d0 from the inner group, std / thr / sw / cmt / blen from the component's q. */
    private fun boltsPart(x: FamCtx, g: BoltGroup, qi: JsonObject, len: Double) {
        val m = linkedMapOf<String, JsonElement>("pts" to JsonArray(g.pts.map { jsArr(it.x, it.y, it.z) }), "ax" to jsArr(g.ax.x, g.ax.y, g.ax.z), "d" to (qi["bolt"]!!),
            "n" to jsNum(g.pts.size.toDouble()), "grade" to qi["grade"]!!, "L" to jsNum(floor(len + 0.5)), "d0" to jsNum(floor(g.d0 * 10 + 0.5) / 10))
        if (x.s("grade").isNotEmpty()) m["std"] = JsonPrimitive(x.s("grade") + "XOX")
        m["thr"] = JsonPrimitive(if ((x.q["thr"] as? JsonPrimitive)?.content == "0") "No" else "Yes"); m["sw"] = JsonPrimitive(x.s("sw").ifEmpty { "Site" })
        if (x.s("bcmt").isNotEmpty()) m["cmt"] = JsonPrimitive(x.s("bcmt")); StBolts.tkNum(x.q["blen"], 0.0).let { if (it != 0.0) m["blen"] = jsNum(it) }
        x.parts += ConnPart("BOLTS", g.pieces, JsonObject(m))   // no group: the outer wrapper's TKB_G stays empty (no Holes tab)
    }
    private fun depthOff(st: JsonObject, rhv: Double) = when (st.str("depth")) { "Front" -> rhv; "Middle" -> rhv / 2; else -> 0.0 }

    /** v69 stConnGeom `knee` (L13927). */
    private fun knee(x: FamCtx, rs: JsonObject, rafId: String) {
        val cs = x.a; val qi = KNEE
        val cp1 = cs.vec("p1"); val cp2 = cs.vec("p2"); val top = if (cp1.z >= cp2.z) cp1 else cp2
        val rp1 = rs.vec("p1"); val rp2 = rs.vec("p2"); val near = rp1.distanceTo(top) <= rp2.distanceTo(top); val r0 = if (near) rp1 else rp2; val r1 = if (near) rp2 else rp1
        val f = LF(D3(top.x, top.y, 0.0), D3(r1.x - r0.x, r1.y - r0.y, 0.0)); val run = hypot(r1.x - r0.x, r1.y - r0.y).let { if (it == 0.0) 1.0 else it }
        val tanp = (r1.z - r0.z) / run; val cosp = cos(atan(tanp))
        val cp: Profile? = x.profiles.lookup(cs.str("prof") ?: ""); val rp: Profile? = x.profiles.lookup(rs.str("prof") ?: "")
        val turned = cs.str("rot") == "Front" || cs.str("rot") == "Back"
        val ch = (if (turned) cp?.b else cp?.h) ?: Double.NaN; val cb = (if (turned) cp?.h else cp?.b) ?: Double.NaN; val ctf = or(cp?.tf, 12.0); val ctw = or(cp?.tw, 8.0)
        val rh = or(rp?.h, 400.0); val rb = or(rp?.b, 180.0); val rtf = or(rp?.tf, 13.0); val rtw = or(rp?.tw, 8.6); val rhv = rh / cosp
        val sOff = (r0 - D3(top.x, top.y, 0.0)) dot f.h; val dOff = depthOff(rs, rhv)
        fun zTop(s: Double) = r0.z + (s - sOff) * tanp + dOff
        fun zBot(s: Double) = zTop(s) - rhv
        val tp = q(qi, "tp"); val sf = ch / 2; val se = sf + tp; val dh = or(q(qi, "depth"), or(rp?.h, 400.0)); val lh = or(q(qi, "len"), Math.floor(SPAN / 20 + 0.5))
        x.cut(rafId, f.boxL(-50000.0, se, -50000.0, 50000.0, -2000.0, 2000.0))
        val zc = zTop(sf); val capT = q(qi, "cap"); x.cut(x.aId, f.boxL(-2000.0, 2000.0, zc - capT, zc + 20000, -2000.0, 2000.0))
        val ep = f.boxL(sf, se, zBot(se) - dh - 15, zTop(sf) + 10, -rb / 2 - 10, rb / 2 + 10)
        val z1 = zTop(sf) - 60; val z2 = zBot(se) - dh + 60; val n = max(2, jsInt(q(qi, "rows"))); val g = min(q(qi, "gauge"), cb - 50) / 2
        val bs = ArrayList<D3>(); for (i in 0 until n) for (y in listOf(-g, g)) bs += f.p(se + 0.1, z1 + (z2 - z1) * i / (n - 1), y)
        val bolt = q(qi, "bolt"); val bb = StBolts.build(bs, -f.h, bolt, tp + ctf + 2, qi); for (hc in bb.holes) x.cut(x.aId, hc)
        x.plate("END PLATE", ep, tp, pl(tp), minus = bb.holes); boltsPart(x, bb, qi, (tp + ctf) + 2 * bolt)
        val hx = se + lh
        x.plate("HAUNCH WEB", f.prism(listOf(doubleArrayOf(se, zBot(se)), doubleArrayOf(hx, zBot(hx)), doubleArrayOf(se, zBot(se) - dh)), -rtw / 2, rtw / 2), 0.0, pl(or(rp?.tw, 9.0)))
        val ang = atan2(dh - (zBot(se) - zBot(hx)), hx - se); val nx = sin(ang); val nz = -cos(ang)
        x.plate("HAUNCH FLANGE", f.prism(listOf(doubleArrayOf(se, zBot(se) - dh), doubleArrayOf(hx, zBot(hx)), doubleArrayOf(hx + nx * rtf, zBot(hx) + nz * rtf), doubleArrayOf(se, zBot(se) - dh + nz * rtf / cos(ang))), -rb / 2, rb / 2), 0.0, pl(or(rp?.tf, 13.0)))
        x.plate("CAP PLATE", f.boxL(-ch / 2 - 10, ch / 2 + 10, zc - capT, zc, -cb / 2 - 10, cb / 2 + 10), 0.0, pl(capT))
        val st = q(qi, "stiff")
        for (zz in listOf(zBot(sf) + rtf / 2, zBot(se) - dh + rtf / 2)) for (sy in listOf(-1, 1))
            x.plate("STIFFENER", f.boxL(-ch / 2 + ctf, ch / 2 - ctf, zz - st / 2, zz + st / 2, if (sy > 0) ctw / 2 else -cb / 2 + 5, if (sy > 0) cb / 2 - 5 else -ctw / 2), 0.0, pl(st))
        val a = q(qi, "a"); val a5 = min(5.0, or(a, 5.0))
        val loop = listOf(doubleArrayOf(se, zTop(se), -rb / 2), doubleArrayOf(se, zTop(se), rb / 2), doubleArrayOf(se, zBot(se) - dh, rb / 2), doubleArrayOf(se, zBot(se) - dh, -rb / 2)).map { f.p(it[0], it[1], it[2]) }
        x.welds += Weld("Rafter + haunch to end plate", a, or(rp?.P, 1.4) * 1000 + 2 * dh + rb, loop)
        x.welds += Weld("Haunch to rafter", a5, 2 * lh); x.welds += Weld("Cap plate to column", a5, or(cp?.P, 1.5) * 1000)
        x.welds += Weld("Stiffeners", a5, 4 * 2 * ((ch - 2 * ctf) + (cb - ctw) / 2))
    }

    /** v69 stConnGeom `apex` (L13951). */
    private fun apex(x: FamCtx, bs0: JsonObject, bId: String) {
        val ast = x.a; val qi = APEX
        val a1 = ast.vec("p1"); val a2 = ast.vec("p2"); var best: Triple<Double, D3, D3>? = null
        for (pa in listOf(a1, a2)) for (pb in listOf(bs0.vec("p1"), bs0.vec("p2"))) { val d = pa.distanceTo(pb); if (best == null || d < best.first) best = Triple(d, pa, pb) }
        val apx = best!!.second; val far = if (apx === a1) a2 else a1
        val f = LF(D3(apx.x, apx.y, 0.0), D3(apx.x - far.x, apx.y - far.y, 0.0)); val run = hypot(apx.x - far.x, apx.y - far.y).let { if (it == 0.0) 1.0 else it }
        val tanp = (apx.z - far.z) / run; val cosp = cos(atan(tanp)); val rp: Profile? = x.profiles.lookup(ast.str("prof") ?: "")
        val rh = or(rp?.h, 400.0); val rb = or(rp?.b, 180.0); val rtw = or(rp?.tw, 8.6); val rtf = or(rp?.tf, 13.0); val rhv = rh / cosp; val dOff = depthOff(ast, rhv)
        fun zTop(s: Double) = apx.z - abs(s) * tanp + dOff
        fun zBot(s: Double) = zTop(s) - rhv
        val tp = q(qi, "tp"); val da = q(qi, "depth"); val la = q(qi, "len")
        x.cut(x.aId, f.boxL(-tp, 50000.0, -50000.0, 50000.0, -2000.0, 2000.0)); x.cut(bId, f.boxL(-50000.0, tp, -50000.0, 50000.0, -2000.0, 2000.0))
        val e1 = f.boxL(-tp, 0.0, zBot(tp) - da - 15, zTop(0.0) + 10, -rb / 2 - 10, rb / 2 + 10); val e2 = f.boxL(0.0, tp, zBot(tp) - da - 15, zTop(0.0) + 10, -rb / 2 - 10, rb / 2 + 10)
        val n = max(2, jsInt(q(qi, "rows"))); val z1 = zTop(tp) - 60; val z2 = zBot(tp) - da + 60; val g = q(qi, "gauge") / 2
        val bl = ArrayList<D3>(); for (i in 0 until n) for (y in listOf(-g, g)) bl += f.p(-tp - 0.1, z1 + (z2 - z1) * i / (n - 1), y)
        val bolt = q(qi, "bolt"); val bb = StBolts.build(bl, f.h, bolt, 2 * tp + 2, qi)
        x.plate("END PLATE", e1, tp, pl(tp), minus = bb.holes); x.plate("END PLATE", e2, tp, pl(tp), minus = bb.holes); boltsPart(x, bb, qi, 2 * tp + 2 * bolt)
        for (sg in listOf(-1, 1)) { val s0 = sg * tp; val s1 = sg * (tp + la)
            fun p(s: Double, z: Double) = doubleArrayOf(s, z)
            x.plate("APEX HAUNCH WEB", f.prism(if (sg < 0) listOf(p(s1, zBot(s1)), p(s0, zBot(s0)), p(s0, zBot(s0) - da)) else listOf(p(s0, zBot(s0)), p(s1, zBot(s1)), p(s0, zBot(s0) - da)), -rtw / 2, rtw / 2), 0.0, pl(or(rp?.tw, 9.0)))
            x.plate("APEX HAUNCH FLANGE", f.prism(if (sg < 0) listOf(p(s1, zBot(s1)), p(s0, zBot(s0) - da), p(s0, zBot(s0) - da - rtf), p(s1, zBot(s1) - rtf))
                else listOf(p(s0, zBot(s0) - da - rtf), p(s0, zBot(s0) - da), p(s1, zBot(s1)), p(s1, zBot(s1) - rtf)), -rb / 2, rb / 2), 0.0, pl(or(rp?.tf, 13.0))) }
        val a = q(qi, "a")
        for (sg in listOf(-1, 1)) x.welds += Weld("Rafter + haunch to end plate", a, or(rp?.P, 1.4) * 1000 + 2 * da + rb,
            listOf(doubleArrayOf(sg * tp, zTop(tp), -rb / 2), doubleArrayOf(sg * tp, zTop(tp), rb / 2), doubleArrayOf(sg * tp, zBot(tp) - da, rb / 2), doubleArrayOf(sg * tp, zBot(tp) - da, -rb / 2)).map { f.p(it[0], it[1], it[2]) })
        x.welds += Weld("Haunches to rafters", min(5.0, or(a, 5.0)), 4 * la)
    }
}
