package com.solidsketchapp.steel.conn

import com.solidsketchapp.core.api.JsNumber
import com.solidsketchapp.core.api.Mesh
import com.solidsketchapp.steel.geom.D3
import com.solidsketchapp.steel.geom.Prism
import com.solidsketchapp.steel.geom.Region
import com.solidsketchapp.steel.geom.Sections
import com.solidsketchapp.steel.geom.StBuild.Companion.num
import com.solidsketchapp.steel.geom.StBuild.Companion.str
import kotlinx.serialization.json.JsonObject
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * TG family: tongue plate tube ends (Ttg) and tube crossings (Ttgx). Live chain: v69 FAM.TG (L15612, FAM_TG22) wrapped by
 * the v54 node gusset (L15867), which only acts when several Ttg connections share one node of the main part.
 */
object FamTG {
    const val PORTED = true
    private const val BIG = 60000.0   // v69 v49Big

    private fun tubeHalf(m: MemberFrame, dir: D3) = (abs(dir dot m.eh) * m.h + abs(dir dot m.es) * m.b) / 2

    /** v69 stFaceToward (L15536): the face of a member a brace comes from, by the brace direction. */
    private fun faceToward(m: MemberFrame, p0: D3, d: D3): FamCL.Face {
        val al = (p0 - m.c1) dot m.a; val pc = m.c1.add(m.a, al); var u = d.add(m.a, -(d dot m.a)); if (u.length() < 1e-6) return FamCL.faceAt(m, p0); u = u.normalize()
        val w = p0 - pc; val y0 = w dot m.eh; val z0 = w dot m.es; val uy = u dot m.eh; val uz = u dot m.es; val tube = m.kind == "O" || m.kind == "R"
        val fl = abs(uy) >= abs(uz)
        fun sg(a: Double, b: Double) = if (a != 0.0) Math.signum(a) else FamCL.sgn1(b)
        val s = if (fl) sg(uy, y0) else sg(uz, z0)
        return FamCL.Face(if (fl) m.eh * s else m.es * s, if (fl) m.h / 2 else (if (tube) m.b / 2 else m.tw / 2), fl, pc)
    }

    /** v69 stEnvelope (L14883): the profile's outline (no hollows) along the part. */
    private fun envelope(x: FamCtx, st: JsonObject): Mesh? {
        val sec = Sections.of(x.profiles.lookup(st.str("prof") ?: "")) ?: return null; val m = Joints.mf(st, x.profiles)
        val l = m.c2.distanceTo(m.c1) + st.num("dx1") + st.num("dx2")
        return Prism.extrude(Region(sec.outer), l, Prism.Frame(m.c1.add(m.a, -st.num("dx1")), m.es, m.eh, m.a))
    }

    /** v69 stHull2 (L15542): monotone-chain convex hull. */
    private fun hull(p: List<DoubleArray>): List<DoubleArray> {
        val s = p.sortedWith { a, b -> val d = a[0] - b[0]; if (d != 0.0) d.compareTo(0.0) else (a[1] - b[1]).compareTo(0.0) }
        fun cr(o: DoubleArray, a: DoubleArray, b: DoubleArray) = (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0])
        val lo = ArrayList<DoubleArray>(); val up = ArrayList<DoubleArray>()
        for (q in s) { while (lo.size >= 2 && cr(lo[lo.size - 2], lo[lo.size - 1], q) <= 0) lo.removeAt(lo.size - 1); lo += q }
        for (q in s.reversed()) { while (up.size >= 2 && cr(up[up.size - 2], up[up.size - 1], q) <= 0) up.removeAt(up.size - 1); up += q }
        return lo.dropLast(1) + up.dropLast(1)
    }

    private class End(val tg: Mesh, val bl: List<D3>, val f: Joints.Frame3)
    /** v69 tgEnd (L15599): one brace end on a tongue, in the brace plane (normal g); s along d from S0. */
    private fun tgEnd(x: FamCtx, brSt: JsonObject, brId: String, s0: D3, d: D3, g: D3, sTip: Double, toff: Double, out: Double): End {
        val mb = Joints.mf(brSt, x.profiles); val pIn = (g cross d).normalize(); val t = x.n("t"); val w2 = x.n("w") / 2; val e = x.n("e"); val p = x.n("p")
        val sTube = sTip + (x.n("L") - x.n("ins")); val f = Joints.Frame3(s0, d, pIn)
        val tg = f.box(sTip, sTip + x.n("L"), -w2, w2, toff - t / 2, toff + t / 2); val bl = ArrayList<D3>()
        for (s in listOf(sTip + e, sTip + e + p)) for (z in listOf(-x.n("g") / 2, x.n("g") / 2)) bl += f.p(s, z, toff - out * (t / 2 + 0.1))
        x.cut(brId, f.box(-BIG, sTube, -BIG / 10, BIG / 10, -BIG / 10, BIG / 10)); val hw = tubeHalf(mb, pIn); val hg = tubeHalf(mb, g)
        x.cut(brId, f.box(sTube - 1, sTube + x.n("ins") + 10, -hw - 5, hw + 5, toff - t / 2 - 1, toff + t / 2 + 1))
        if (x.t("cap")) for (sg in listOf(1.0, -1.0)) { val z0 = toff + sg * t / 2; val z1 = sg * hg
            if (abs(z1 - z0) > 1 && Math.signum(z1 - z0) == sg) x.plate("END CAP", f.box(sTube - x.n("cap"), sTube, -hw, hw, z0, z1), x.n("cap"), "FLT" + FamCL.jsStr(x.q["cap"]) + "*" + JsNumber.format(floor(2 * hw + 0.5))) }
        x.weld("Tongue in tube", x.n("a"), 4 * x.n("ins")); return End(tg, bl, f)
    }

    private fun flt(x: FamCtx) = "FLT" + FamCL.jsStr(x.q["t"]) + "*" + FamCL.jsStr(x.q["w"])

    fun gen(x: FamCtx) {
        val t = x.n("t"); val w2 = x.n("w") / 2
        if (x.s("kind") == "cross") {
            val h1 = x.b ?: throw IllegalStateException("not built"); val mc = Joints.mf(x.a, x.profiles)
            val halves = listOfNotNull(h1 to x.bId!!, x.b2?.let { it to x.b2Id!! }); val m1 = Joints.mf(h1, x.profiles)
            var g = mc.a cross m1.a; if (g.length() < 1e-6) throw IllegalStateException("not built"); g = g.normalize()
            val w0 = mc.c1 - m1.c1; val b = mc.a dot m1.a; val dd = mc.a dot w0; val ee = m1.a dot w0; val den = (1 - b * b).let { if (it == 0.0) 1e-9 else it }
            val p0 = mc.c1.add(mc.a, (b * ee - dd) / den)
            val pC = (g cross mc.a).normalize(); val gf = Joints.Frame3(p0, mc.a, pC); val toff = t / 2 + x.n("t") / 2
            val hwC = tubeHalf(mc, pC)
            val pts = arrayListOf(doubleArrayOf(-hwC - 60, -hwC - 10), doubleArrayOf(hwC + 60, -hwC - 10), doubleArrayOf(hwC + 60, hwC + 10), doubleArrayOf(-hwC - 60, hwC + 10))
            val ends = ArrayList<End>()
            for ((hst, hid) in halves) {
                val mh = Joints.mf(hst, x.profiles); val near = mh.c1.distanceTo(p0) <= mh.c2.distanceTo(p0); val d = ((if (near) mh.c2 else mh.c1) - (if (near) mh.c1 else mh.c2)).normalize()
                val sinA = max(0.2, (d cross mc.a).length()); val cosA = abs(d dot mc.a)
                val sTip = (hwC + x.n("clear") + w2 * cosA) / sinA; ends += tgEnd(x, hst, hid, p0, d, g, sTip, toff, 1.0); val pIn = (g cross d).normalize()
                for (s in listOf(sTip - 5, sTip + (2 * x.n("e") + x.n("p")))) for (z in listOf(-w2, w2)) { val v = p0.add(d, s).add(pIn, z) - p0; pts += doubleArrayOf(v dot mc.a, v dot pC) }
            }
            val gus = gf.poly(hull(pts), -t / 2, t / 2)
            x.cut(x.aId, gf.box(pts.minOf { it[0] } - 1, pts.maxOf { it[0] } + 1, -hwC - 10, hwC + 10, -t / 2 - 1, t / 2 + 1))   // slot the continuous brace for the gusset
            val gh = ArrayList<Mesh>()
            for (r in ends) { val bb = x.group(r.bl, g, x.n("bolt"), 2 * t + 2); gh += bb.holes
                x.plate("TONGUE PLATE", r.tg, t, flt(x), minus = bb.holes); x.bolts(bb, r.bl.size, 2 * x.n("t") + 2 * x.n("bolt")) }
            x.plate("CROSSING GUSSET", gus, t, minus = gh); x.weld("Crossing gusset to brace", x.n("a"), 4 * 2 * hwC); return
        }
        // v69 L15867 (node gusset) falls back to this unless two or more Ttg share the node; see the report / LIVE-FUNCTIONS.
        val br = x.b ?: throw IllegalStateException("not built"); val mb = Joints.mf(br, x.profiles); val ma = Joints.mf(x.a, x.profiles)
        fun dl(p: D3): Double { val v = p - ma.c1; return v.add(ma.a, -(v dot ma.a)).length() }
        val near = dl(mb.c1) <= dl(mb.c2); val e = if (near) mb.c1 else mb.c2; val far = if (near) mb.c2 else mb.c1; val d = (far - e).normalize()
        val fa = faceToward(ma, e, d); val nF = fa.n; val f0 = fa.pc.add(nF, fa.off); val dn = d dot nF
        val w = if (abs(dn) > 0.05) e.add(d, ((f0 - e) dot nF) / dn) else e
        val pl = x.s("plane"); var v2 = if (pl == "main") ma.a else if (pl == "vert") D3.Z else (if (abs(d dot ma.a) < 0.3) D3.Z else ma.a)
        if ((d cross v2).length() < 0.1) v2 = ma.es
        val g = (d cross v2).normalize(); val pIn = (g cross d).normalize(); val sinT = max(0.2, abs(dn)); val cosT = sqrt(max(0.0, 1 - sinT * sinT))
        val outst = if (!fa.flange && ma.kind == "I" && !(abs(g dot ma.eh) > 0.9 && abs(ma.a.z) < 0.8)) ma.b / 2 - ma.tw / 2 else 0.0
        val sTip = (outst + w2 * cosT + x.n("clear")) / sinT; val sg = if (x.t("side")) x.n("side") else 1.0; val toff = 0.0
        val r = tgEnd(x, br, x.bId!!, w, d, g, sTip, toff, sg)
        val tf = x.n("tf"); val fw2 = (if (x.t("fw")) x.n("fw") else x.n("w")) / 2; val z0 = toff + sg * t / 2; val z1 = toff + sg * (t / 2 + tf); val sEnd = sTip + (2 * x.n("e") + x.n("p"))
        val s0 = -80 - outst / sinT
        val fin = r.f.poly(listOf(doubleArrayOf(s0, -fw2), doubleArrayOf(sEnd, -fw2), doubleArrayOf(sEnd, fw2 - 25), doubleArrayOf(sEnd - 25, fw2), doubleArrayOf(s0, fw2)), min(z0, z1), max(z0, z1))
        // fin.intersect(the half space in front of the face) = fin minus the half space behind it; then minus the main part's envelope
        val behind = Joints.box3(f0, nF, pIn.add(nF, -(pIn dot nF)).normalize(), -BIG, -0.01, -BIG, BIG, -BIG, BIG)
        val bb = x.group(r.bl, g * sg, x.n("bolt"), t + tf + 2)
        x.plate("TONGUE PLATE", r.tg, t, flt(x), minus = bb.holes)
        x.plate("FIN PLATE", fin, tf, minus = listOfNotNull(behind, runCatching { envelope(x, x.a) }.getOrNull()) + bb.holes)
        x.bolts(bb, r.bl.size, x.n("t") + x.n("tf") + 2 * x.n("bolt")); x.weld("Fin plate to main part", x.n("a"), 2 * (sEnd + outst / sinT + 80))
    }
}
