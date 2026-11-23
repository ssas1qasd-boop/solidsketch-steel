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
}
