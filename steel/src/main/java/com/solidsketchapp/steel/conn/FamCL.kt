package com.solidsketchapp.steel.conn

import com.solidsketchapp.core.api.JsNumber
import com.solidsketchapp.core.api.Mesh
import com.solidsketchapp.steel.geom.D3
import com.solidsketchapp.steel.geom.StBuild.Companion.str
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.abs
import kotlin.math.sign

/** CL family: cold rolled purlin cleat L (Tcl), v69 FAM.CL (L15582), routed to famGeom by the stConnGeom wrapper L15639. */
object FamCL {
    const val PORTED = true

    /** v69 purlinWeb (L15578): the purlin's web back face (offset from the centre line), the outward direction, the web thickness. */
    internal class Web(val dir: D3, val off: Double, val t: Double)
    internal fun purlinWeb(x: FamCtx, st: JsonObject, mp: MemberFrame): Web {
        val p = x.profiles.lookup(st.str("prof") ?: ""); val side = if (x.t("side")) x.n("side") else 1.0
        fun or(vararg v: Double?, d: Double) = v.firstOrNull { it != null && it != 0.0 && !it.isNaN() } ?: d
        return when (mp.kind) {
            "C", "U" -> Web(-mp.es, mp.b / 2, or(p?.t, mp.tw, d = 2.0))
            "Z" -> Web(mp.es * side, or(p?.t, d = 2.0) / 2, or(p?.t, d = 2.0))
            else -> Web(mp.es * side, mp.tw / 2, mp.tw)
        }
    }

    /** v69 stFaceAt (L14891): the face of a member nearest a point: outward normal n, offset from the centre line, axis point Pc. */
    internal class Face(val n: D3, val off: Double, val flange: Boolean, val pc: D3)
    internal fun faceAt(m: MemberFrame, p0: D3): Face {
        val al = (p0 - m.c1) dot m.a; val pc = m.c1.add(m.a, al); val w = p0 - pc; val y = w dot m.eh; val z = w dot m.es
        val fl = abs(y) / (if (m.h != 0.0) m.h else 1.0) >= abs(z) / (if (m.b != 0.0) m.b else 1.0)
        val n = if (fl) m.eh * sgn1(y) else m.es * sgn1(z); val tube = m.kind == "O" || m.kind == "R"
        return Face(n, if (fl) m.h / 2 else (if (tube) m.b / 2 else m.tw / 2), fl, pc)
    }
    /** `Math.sign(v) || 1`. */
    internal fun sgn1(v: Double) = if (v > 0) 1.0 else if (v < 0) -1.0 else 1.0
    /** JavaScript String(v) of a q value. */
    internal fun jsStr(e: JsonElement?): String = when {
        e == null -> "undefined"; e is JsonNull -> "null"
        e is JsonPrimitive && !e.isString -> e.content.toDoubleOrNull()?.let { if (it.isFinite()) JsNumber.format(it) else e.content } ?: e.content
        e is JsonPrimitive -> e.content; else -> e.toString()
    }

    fun gen(x: FamCtx) {
        val purs = listOfNotNull(x.b?.let { it to x.bId!! }, x.b2?.let { it to x.b2Id!! }); if (purs.isEmpty()) throw IllegalStateException("not built")
        val ma = Joints.mf(x.a, x.profiles); val mp = Joints.mf(purs[0].first, x.profiles)
        // v70 · Tekla parity (web/v70_tekla_parity.js): 'Cleat location' Front on a C / U purlin; the stored settings decide (old models: none = v69)
        if (jsStr(x.conn?.q?.get("loc")) == "front" && (mp.kind == "C" || mp.kind == "U")) return front(x, purs, ma, mp)
        val w0 = ma.c1 - mp.c1; val b = ma.a dot mp.a; val dd = ma.a dot w0; val ee = mp.a dot w0; val den = (1 - b * b).let { if (it == 0.0) 1e-9 else it }
        val pp = mp.c1.add(mp.a, (ee - b * dd) / den)
        val fa = faceAt(ma, pp); val n = fa.n; val base = fa.pc.add(n, fa.off); val y = mp.a.add(n, -(mp.a dot n)).normalize()
        val wb = purlinWeb(x, purs[0].first, mp); val out = wb.dir.add(n, -(wb.dir dot n)).add(y, -(wb.dir dot y)).normalize()
        val webPt = pp.add(wb.dir, wb.off); val sw = (webPt - base) dot out; val o = base.add(out, sw)
        val t = x.n("t"); val l1 = x.n("L1"); val l2 = x.n("L2"); val w = x.n("w") / 2; val f = Joints.Frame3(o, out, n)
        val cl = f.poly(listOf(doubleArrayOf(0.0, 0.0), doubleArrayOf(l2, 0.0), doubleArrayOf(l2, t), doubleArrayOf(t, t), doubleArrayOf(t, l1), doubleArrayOf(0.0, l1)), -w, w)
        if (x.t("stiff")) x.plate("CLEAT STIFFENER", f.poly(listOf(doubleArrayOf(t, t), doubleArrayOf(t + 44, t), doubleArrayOf(t + 44, t + 20), doubleArrayOf(t, t + 124)), -x.n("ts") / 2, x.n("ts") / 2), x.n("ts"))
        val zAxis = f.z; val holes = ArrayList<Mesh>(); val all = ArrayList<Triple<BoltGroup, Int, Double>>()
        for ((pst, pid) in purs) {
            val m2 = Joints.mf(pst, x.profiles); val mid = m2.c1.lerp(m2.c2, 0.5); val sg = sgn1((mid - o) dot zAxis); val tw2 = purlinWeb(x, pst, m2).t.let { if (it != 0.0 && !it.isNaN()) it else 2.0 }
            val z1 = (m2.c1 - o) dot zAxis; val z2 = (m2.c2 - o) dot zAxis
            val zz = if (purs.size == 1 && abs(z1) > w && abs(z2) > w && sign(z1) != sign(z2)) listOf(-x.n("e"), x.n("e")) else listOf(sg * x.n("e"))
            val bl = ArrayList<D3>(); for (z in zz) for (hh in listOf(x.n("h1"), x.n("h2"))) bl += f.p(t + 0.1, hh, z)
            val grip = t + tw2 + 2; val bb = x.group(bl, -out, x.n("bolt"), grip)
            for (hc in bb.holes) { holes += hc; x.cut(pid, hc) }; all += Triple(bb, bl.size, grip)
        }
        x.plate("CLEAT", cl, t, "L${jsStr(x.q["L1"])}*${jsStr(x.q["L2"])}*${jsStr(x.q["t"])}", minus = holes)
        for ((bb, k, grip) in all) x.bolts(bb, k, grip + 2 * x.n("bolt"))
        x.weld("Cleat to rafter top flange", x.n("a"), 2 * x.n("w"))
    }

    /** v70 R10 'Stiff.' PL6*44 outline: (height above the rafter top, along the purlin). */
    private val STIFF70 = listOf(135.0 to 22.0, 21.0 to 22.0, 11.0 to 12.0, 11.0 to -22.0, 21.0 to -22.0, 135.0 to 12.0)

    /**
     * v70 · Tekla parity, C04 (web/v70_tekla_parity.js, docs/steel/PARITY.md): Tekla Cold rolled overlap (1) 'Cleat location' Front on a
     * C / U purlin. The long leg stands on the rafter on the purlin's toe line, the short leg at its top points away from the purlin;
     * the angle is placed like a Tekla L member at Middle / Middle (its turned profile's bounding box in the part's Top frame is
     * centred on the axis); the stiffener lies on the long leg's outer face; bolts start at the web back face toward the toes.
     */
    private fun front(x: FamCtx, purs: List<Pair<JsonObject, String>>, ma: MemberFrame, mp: MemberFrame) {
        val w0 = ma.c1 - mp.c1; val b = ma.a dot mp.a; val dd = ma.a dot w0; val ee = mp.a dot w0; val den = (1 - b * b).let { if (it == 0.0) 1e-9 else it }
        val pp = mp.c1.add(mp.a, (ee - b * dd) / den)
        val fa = faceAt(ma, pp); val n = fa.n; val base = fa.pc.add(n, fa.off); val y = mp.a.add(n, -(mp.a dot n)).normalize()
        val wb = purlinWeb(x, purs[0].first, mp); val out = wb.dir.add(n, -(wb.dir dot n)).add(y, -(wb.dir dot y)).normalize()
        val webPt = pp.add(wb.dir, wb.off); val sw = (webPt - base) dot out; val ow = base.add(out, sw)
        val toe = -out; val o = ow.add(toe, mp.b)
        val t = x.n("t"); val l1 = x.n("L1"); val l2 = x.n("L2"); val w = x.n("w") / 2; val f0 = Joints.Frame3(o, toe, n)
        val pts = listOf(doubleArrayOf(0.0, 0.0), doubleArrayOf(t, 0.0), doubleArrayOf(t, l1 - t), doubleArrayOf(l2, l1 - t), doubleArrayOf(l2, l1), doubleArrayOf(0.0, l1))
        val xa = f0.z; var yT = D3(0.0, 0.0, 1.0).add(xa, -xa.z); if (yT.length() < 1e-6) yT = D3(1.0, 0.0, 0.0).add(xa, -xa.x); yT = yT.normalize(); val zT = xa cross yT
        var a0 = Double.POSITIVE_INFINITY; var a1 = Double.NEGATIVE_INFINITY; var b0 = Double.POSITIVE_INFINITY; var b1 = Double.NEGATIVE_INFINITY
        for (pt in pts) { val p = f0.p(pt[0], pt[1], 0.0); val a = p dot yT; val bb = p dot zT; a0 = minOf(a0, a); a1 = maxOf(a1, a); b0 = minOf(b0, bb); b1 = maxOf(b1, bb) }
        val c0 = f0.p(l2 / 2, l1 / 2, 0.0); val sh = (yT * ((c0 dot yT) - (a0 + a1) / 2)).add(zT, (c0 dot zT) - (b0 + b1) / 2)
        val cl = Joints.Frame3(o + sh, toe, n).poly(pts, -w, w)
        if (x.t("stiff")) { val g = Joints.Frame3(f0.p(t - x.n("ts") / 2, 0.0, 0.0), n, f0.z)
            x.plate("CLEAT STIFFENER", g.poly(STIFF70.map { doubleArrayOf(it.first, it.second) }, 0.0, x.n("ts")), x.n("ts")) }
        val zAxis = f0.z; val holes = ArrayList<Mesh>(); val all = ArrayList<Triple<BoltGroup, Int, Double>>()
        for ((pst, pid) in purs) {
            val m2 = Joints.mf(pst, x.profiles); val mid = m2.c1.lerp(m2.c2, 0.5); val sg = sgn1((mid - o) dot zAxis)
            val z1 = (m2.c1 - o) dot zAxis; val z2 = (m2.c2 - o) dot zAxis
            val zz = if (purs.size == 1 && abs(z1) > w && abs(z2) > w && sign(z1) != sign(z2)) listOf(-x.n("e"), x.n("e")) else listOf(sg * x.n("e"))
            val bl = ArrayList<D3>(); for (z in zz) for (hh in listOf(x.n("h1"), x.n("h2"))) bl += f0.p(-m2.b, hh, z)
            val grip = m2.b + l2; val bb = x.group(bl, toe, x.n("bolt"), grip)   // web back to the short leg's end: R10 M12 L144 = b + L2 + 2·d
            for (hc in bb.holes) { holes += hc; x.cut(pid, hc) }; all += Triple(bb, bl.size, grip)
        }
        x.plate("CLEAT", cl, t, "L${jsStr(x.q["L1"])}*${jsStr(x.q["L2"])}*${jsStr(x.q["t"])}", minus = holes)
        for ((bb, k, grip) in all) x.bolts(bb, k, grip + 2 * x.n("bolt"))
        x.weld("Cleat to rafter top flange", x.n("a"), 2 * x.n("w"))
    }
}
