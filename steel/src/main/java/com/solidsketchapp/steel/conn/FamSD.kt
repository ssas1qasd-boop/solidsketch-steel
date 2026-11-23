package com.solidsketchapp.steel.conn

import com.solidsketchapp.steel.geom.D3
import com.solidsketchapp.steel.geom.Prism
import com.solidsketchapp.steel.geom.Region
import com.solidsketchapp.steel.geom.StBuild.Companion.str
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * SD family: details at a picked point on one part (stiffeners 1003 / 1030 / 1041 / 1064 / 1017, doubler 1022 / pd, end
 * plate 1002, cap, cast-in 1069, L base 1020, seat angles 1040 / 1048 / 1049, connection plate 1026 / 114, gussets 1065 / 171,
 * stub 1011, studs 1010, bearings 1013 / 1015 / 1067, lug 1031, web hole stiffeners 1060, ring plate). Live definition:
 * v69 FAM.SD (L15110, the only one).
 */
object FamSD {
    const val PORTED = true

    /** v69 stFaceAt (L14891): the face nearest a point, with `along` (mm along the axis). */
    private class Face(val n: D3, val off: Double, val flange: Boolean, val pc: D3, val along: Double)
    private fun faceAt(m: MemberFrame, p0: D3): Face {
        val al = (p0 - m.c1) dot m.a; val pc = m.c1.add(m.a, al); val w = p0 - pc; val y = w dot m.eh; val z = w dot m.es
        val fl = abs(y) / (if (m.h != 0.0) m.h else 1.0) >= abs(z) / (if (m.b != 0.0) m.b else 1.0)
        val n = if (fl) m.eh * sgn1(y) else m.es * sgn1(z); val tube = m.kind == "O" || m.kind == "R"
        return Face(n, if (fl) m.h / 2 else (if (tube) m.b / 2 else m.tw / 2), fl, pc, al)
    }
    /** `Math.sign(v) || 1`. */
    private fun sgn1(v: Double) = if (v > 0) 1.0 else if (v < 0) -1.0 else 1.0
    private fun or(v: Double?, d: Double) = if (v == null || v == 0.0 || v.isNaN()) d else v
    /** A ring (outer / inner n-gons) extruded t along n in the frame (o; u, v) (v69 C.placeInFrame(C.extrudeRegions(...))). */
    private fun ring(nSeg: Int, r1: Double, r0: Double, t: Double, o: D3, u: D3, v: D3, n: D3) =
        Prism.extrude(Region(List(nSeg) { i -> doubleArrayOf(r1 * cos(i * PI / (nSeg / 2)), r1 * sin(i * PI / (nSeg / 2))) },
            listOf(List(nSeg) { i -> doubleArrayOf(r0 * cos(i * PI / (nSeg / 2)), r0 * sin(i * PI / (nSeg / 2))) })), t, Prism.Frame(o, u, v, n))

    fun gen(x: FamCtx) {
        val k = x.s("kind")
        if (x.b != null && k == "stiff") {   // L15111: stiffeners in the main part at the secondary's flanges
            val j = Joints.joint(x.a, x.b, x.profiles); val bp = j.b; val nn = (j.m.eh cross j.m.es).normalize()
            for (yy in listOf(bp.h / 2 - bp.tf / 2, -(bp.h / 2 - bp.tf / 2))) Fam.stiffPair(x, j.m, (j.p(0.0, yy, 0.0) - j.m.c1) dot nn, x.n("t"), 15.0, listOf(1, -1))
            return
        }
        val m = Joints.mf(x.a, x.profiles); val prof = x.profiles.lookup(x.a.str("prof") ?: ""); val l = m.c2.distanceTo(m.c1)
        val pt = x.pt ?: m.c1.lerp(m.c2, 0.5); val fa = faceAt(m, pt); val al = min(l, max(0.0, fa.along)); val nn = (m.eh cross m.es).normalize()
        val tz = (m.c1.add(m.a, al) - m.c1) dot nn
        val atEnd = al >= l / 2; val endP = if (atEnd) m.c2 else m.c1; val out = if (atEnd) m.a else -m.a; val t = x.n("t")
        fun pP() = or(prof?.P, 1.0)
        when (k) {
            "stiff" -> { Fam.stiffPair(x, m, tz, x.n("t"), 15.0, listOf(1, -1)); return }
            "multi" -> { val s = or(x.n("s"), 1000.0); var xx = s; while (xx < l - s / 2) { Fam.stiffPair(x, m, (m.c1.add(m.a, xx) - m.c1) dot nn, x.n("t"), 15.0, listOf(1, -1)); xx += s }; return }
            "hstiff" -> { val f = Joints.Frame3(m.c1.add(m.a, al), m.a, m.es)
                for (sg in listOf(1.0, -1.0)) for (y in listOf(-(m.h / 4), m.h / 4)) x.plate("STIFFENER", f.box(-(x.n("L") / 2), x.n("L") / 2, sg * m.tw / 2, sg * (m.b / 2 - 5), y - t / 2, y + t / 2), x.n("t")); return }
            "doubler" -> { val f = Joints.Frame3(m.c1.add(m.a, al), m.a, m.eh); val hw = m.h / 2 - m.tf - or(m.r, 10.0); val lq = or(x.n("L"), 400.0)
                x.plate("DOUBLER PLATE", f.box(-lq / 2, lq / 2, -hw, hw, -m.tw / 2 - t, -m.tw / 2), x.n("t")); x.weld("Doubler to web", 6.0, 2 * (lq + 2 * hw)); return }
            "endplate", "cap", "castin" -> { val ex = if (k == "cap") 15.0 else 10.0; val f = Joints.Frame3(endP, m.eh, m.es); val tp = max(x.n("t"), 15.0); val sgn = (out dot nn) >= 0
                x.plate(if (k == "cap") "CAP PLATE" else if (k == "castin") "CAST-IN PLATE" else "END PLATE", f.box(-m.h / 2 - ex, m.h / 2 + ex, -m.b / 2 - ex, m.b / 2 + ex, if (sgn) 0.0 else -tp, if (sgn) tp else 0.0), tp)
                x.cut(x.aId, Joints.box3(endP, m.eh, m.es, -m.h, m.h, -m.b, m.b, if (sgn) -1e-6 else -50000.0, if (sgn) 50000.0 else 1e-6))
                if (k == "castin") for (sx in listOf(-1, 1)) for (sy in listOf(-1, 1))
                    x.plate("STUD", stCyl(endP.add(m.eh, sx * m.h / 3).add(m.es, sy * m.b / 3).add(out, tp), out, 9.5, 100.0, 8), 0.0, "STUD 19x100")
                x.weld("Plate to part", x.n("a"), pP() * 1000); return }
            "lcleat", "seatangle" -> { val p0 = (if (k == "lcleat") endP else fa.pc).add(fa.n, fa.off); val along = if (k == "lcleat") -out else m.a
                x.plate(if (k == "lcleat") "BASE ANGLE" else "SEAT ANGLE", Fam.lAngle(Joints.Frame3(p0, fa.n, along), 0.0, 0.0, 100.0, 100.0, 10.0, 1.0, 1.0, -80.0, 80.0), 10.0, "L100*100*10"); return }
        }
        val base = fa.pc.add(fa.n, fa.off); val fp = Joints.Frame3(base, fa.n, m.a)
        when (k) {
            "plate", "gusset", "gussetstiff" -> { val hgt = or(x.n("h"), 150.0); val wd = or(x.n("w"), 200.0) / 2
                x.plate(if (k == "plate") "CONNECTION PLATE" else "GUSSET", fp.box(0.0, hgt, -wd, wd, -t / 2, t / 2), x.n("t"))
                if (k != "plate" && !fa.flange) for (s in listOf(-1, 1)) Fam.stiffPair(x, m, (m.c1.add(m.a, al + s * wd) - m.c1) dot nn, 10.0, 15.0, listOf(sgn1(fa.n dot m.es).toInt()))
                x.weld("Plate to part", x.n("a"), 2 * or(x.n("w"), 200.0)) }
            "stub" -> { val sb = or(prof?.b, 150.0); val sh = or(prof?.h, 200.0)
                x.plate("STUB", Joints.box3(base, fa.n, m.a, 0.0, 300.0, -sb / 2, sb / 2, -sh / 2 * 0.3, sh / 2 * 0.3), 0.0, prof?.key?.takeIf { it.isNotEmpty() } ?: x.a.str("prof")) }
            "studs" -> { val s = or(x.n("s"), 200.0); val f = Joints.Frame3(m.c1.add(m.eh, m.h / 2), m.a, m.es); var xx = s / 2
                while (xx < l) { for (z in listOf(-m.b / 5, m.b / 5)) x.plate("SHEAR STUD", stCyl(f.p(xx, 0.0, z), m.eh, 9.5, 100.0, 8), 0.0, "STUD 19x100"); xx += s } }
            "bearing", "stubplate" -> { x.plate("BEARING PLATE", fp.box(0.0, t, -100.0, 100.0, -m.b / 2, m.b / 2), x.n("t"))
                x.plate("STIFFENER", fp.poly(listOf(doubleArrayOf(t, -80.0), doubleArrayOf(t + 120, -80.0), doubleArrayOf(t, 60.0)), -6.0, 6.0), 12.0) }
            "lug" -> { val lg = fp.poly(listOf(doubleArrayOf(0.0, -60.0), doubleArrayOf(0.0, 60.0), doubleArrayOf(90.0, 40.0), doubleArrayOf(110.0, 0.0), doubleArrayOf(90.0, -40.0)), -10.0, 10.0)
                x.plate("LIFTING LUG", lg, 20.0, minus = listOf(stCyl(base.add(fa.n, 80.0).add(fp.z, -15.0), fp.z, 17.0, 30.0, 16))) }
            "hole", "ring" -> {
                val rmax = m.h / 2 - m.tf - m.r - 62; val r = min(or(x.n("d"), if (m.kind == "O") m.h * 0.65 else m.h * 0.25), if (k == "hole") max(rmax, 20.0) else 1e9)
                val c = m.c1.add(m.a, al)
                if (k == "hole") { x.cut(x.aId, stCyl(c.add(m.es, -m.b), m.es, r, 2 * m.b, 24))
                    for (s in listOf(1, -1)) x.plate("RING STIFFENER", ring(32, r + 60, r, t, c.add(m.es, if (s > 0) m.tw / 2 else -m.tw / 2 - t), m.a, m.eh, m.es), x.n("t")); return }
                val r0 = m.h / 2; x.plate("RING PLATE", ring(40, r0 + 80, r0, t, c, m.es, m.eh, m.a), x.n("t")) }
            else -> throw IllegalStateException("not built")   // v69 `return null`
        }
    }
}
