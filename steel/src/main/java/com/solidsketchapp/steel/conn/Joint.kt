package com.solidsketchapp.steel.conn

import com.solidsketchapp.core.api.Mesh
import com.solidsketchapp.steel.data.ProfileCatalog
import com.solidsketchapp.steel.geom.D3
import com.solidsketchapp.steel.geom.Prism
import com.solidsketchapp.steel.geom.Region
import com.solidsketchapp.steel.geom.Sections
import com.solidsketchapp.steel.geom.StBuild.Companion.num
import com.solidsketchapp.steel.geom.StBuild.Companion.str
import com.solidsketchapp.steel.geom.StBuild.Companion.vec
import kotlinx.serialization.json.JsonObject
import kotlin.math.PI
import kotlin.math.abs

/** v69 `stMF` (v38 L14245): a member's frame and size: axis a, section up eh / side es, end centres c1 / c2 (with the position offsets). */
class MemberFrame(val a: D3, val eh: D3, val es: D3, val c1: D3, val c2: D3, val h: Double, val b: Double, val tw: Double, val tf: Double, val r: Double = 0.0, val kind: String? = null)

object Joints {
    private val rot = mapOf("Top" to 0, "Front" to 1, "Below" to 2, "Back" to 3)

    fun mf(st: JsonObject, profiles: ProfileCatalog, u: Double = 1.0): MemberFrame {
        val p = profiles.lookup(st.str("prof") ?: ""); val sec = Sections.of(p)
        val p1 = st.vec("p1"); val p2 = st.vec("p2"); val a = (p2 - p1).normalize(); val col = abs(a dot D3.Z) > 0.995
        val dz: D3; val base: D3
        if (col) { dz = D3(0.0, 1.0, 0.0); base = D3(-1.0, 0.0, 0.0) } else { dz = D3.Z.add(a, -(a dot D3.Z)).normalize(); base = dz }
        val ds = (dz cross a).normalize(); val th = ((rot[st.str("rot")] ?: 0) * 90 + st.num("rAng")) * PI / 180
        val eh = base.applyAxisAngle(a, th).normalize(); val es = (eh cross a).normalize()
        var s0 = Double.POSITIVE_INFINITY; var s1 = Double.NEGATIVE_INFINITY; var z0 = Double.POSITIVE_INFINITY; var z1 = Double.NEGATIVE_INFINITY
        for (q in sec?.outer ?: listOf(doubleArrayOf(0.0, 0.0))) { val w = (es * q[0]).add(eh, q[1]); val s = w dot ds; val zz = w dot dz; s0 = minOf(s0, s); s1 = maxOf(s1, s); z0 = minOf(z0, zz); z1 = maxOf(z1, zz) }
        val shS = (when (st.str("plane")) { "Left" -> -s0; "Right" -> -s1; else -> -(s0 + s1) / 2 }) + st.num("pOff")
        val shZ = (when (st.str("depth")) { "Front" -> -z0; "Behind" -> -z1; else -> -(z0 + z1) / 2 }) + st.num("dOff")
        val off = (ds * (shS * u)).add(dz, shZ * u)
        // JavaScript `p.h || 300`, `p.tw || p.t || 8` …: the first one that is set and not 0.
        fun or(vararg v: Double?, d: Double) = v.firstOrNull { it != null && it != 0.0 && !it.isNaN() } ?: d
        val hh = if (p?.kind == "O") p.d!! else or(p?.h, d = 300.0); val bb = if (p?.kind == "O") p.d!! else or(p?.b, d = 150.0)
        return MemberFrame(a, eh, es, p1 + off, p2 + off, hh, bb, or(p?.tw, p?.t, d = 8.0), or(p?.tf, p?.t, d = 12.0), or(p?.r, d = 0.0), p?.kind)
    }

    /** v69 stPoly3: a polygon in the frame (O; X, Y) extruded z0..z1 along X × Y (counter-clockwise as v69 makes it). */
    fun poly3(o: D3, x: D3, y: D3, poly: List<DoubleArray>, z0: Double, z1: Double): Mesh {
        val n = (x cross y).normalize()
        return Prism.extrude(Region(poly), abs(z1 - z0), Prism.Frame(o.add(n, z0), x, y, n))   // v69 extrudeRegions: |height| from z0
    }

    /** v69 stFrame3: P(x, y, z), box (sorted bounds), poly in the frame (O; X, Y, Z = X × Y). */
    class Frame3(val o: D3, val x: D3, val y: D3) {
        val z: D3 = (x cross y).normalize()
        fun p(a: Double, b: Double, c: Double) = o.add(x, a).add(y, b).add(z, c)
        fun box(x0: Double, x1: Double, y0: Double, y1: Double, z0: Double, z1: Double) = box3(o, x, y, minOf(x0, x1), maxOf(x0, x1), minOf(y0, y1), maxOf(y0, y1), minOf(z0, z1), maxOf(z0, z1))
        fun poly(pts: List<DoubleArray>, z0: Double, z1: Double) = poly3(o, x, y, pts, minOf(z0, z1), maxOf(z0, z1))
    }

    /** v69 stBox3: a box in the frame (O; X, Y, N = X × Y), from x0..x1, y0..y1, z0..z1. */
    fun box3(o: D3, x: D3, y: D3, x0: Double, x1: Double, y0: Double, y1: Double, z0: Double, z1: Double): Mesh {
        val n = (x cross y).normalize()
        // v69 extrudeRegions takes |height| and stBox3 starts at z0: z1 < z0 gives z0 .. 2·z0 − z1, not z1 .. z0 (J.box with sg = −1)
        return Prism.extrude(Region(listOf(doubleArrayOf(x0, y0), doubleArrayOf(x1, y0), doubleArrayOf(x1, y1), doubleArrayOf(x0, y1))), abs(z1 - z0), Prism.Frame(o.add(n, z0), x, y, n))
    }

    /** v69 stJoint (v38 L14257): where a secondary member meets the main part: its near end E, axis X out of the joint, the face it lands on (xFace), copes. */
    class Joint(val m: MemberFrame, val b: MemberFrame, val e: D3, val x: D3, val y: D3, val zb: D3, val flange: Boolean, val xFace: Double, val cope: Boolean, val yTop: Double, val yBot: Double, val copeL: Double, private val u: Double,
                /** v69 J.u (the face normal of the main part, toward the secondary), J.Pc, J.tPrim (plate thickness of the face). */
                val uDir: D3 = D3.ZERO, val pc: D3 = D3.ZERO, val tPrim: Double = 0.0) {
        val yHi: Double get() = minOf(b.h / 2 * u, yTop - 10 * u)
        val yLo: Double get() = maxOf(-b.h / 2 * u, yBot + 10 * u)
        fun p(x0: Double, y0: Double, z0: Double) = e.add(x, x0).add(y, y0).add(zb, z0)
        fun box(x0: Double, x1: Double, y0: Double, y1: Double, z0: Double, z1: Double) = box3(e, x, y, x0, x1, y0, y1, z0, z1)
        /** v69 J.cutEnd: cut the secondary's end back to xEnd, and its flanges where it is coped. */
        fun cutEnd(xEnd: Double): List<Mesh> {
            val big = 60000 * u; val out = mutableListOf(box(-big, xEnd, -big / 10, big / 10, -big / 10, big / 10))
            if (cope) { val hb = b.h / 2 * u
                if (hb > yTop - 10 * u) out += box(-big, xFace + copeL, yTop - 10 * u, big / 10, -big / 10, big / 10)
                if (-hb < yBot + 10 * u) out += box(-big, xFace + copeL, -big / 10, yBot + 10 * u, -big / 10, big / 10) }
            return out
        }
    }

    fun joint(prim: JsonObject, sec: JsonObject, profiles: ProfileCatalog, u: Double = 1.0): Joint {
        val m = mf(prim, profiles, u); val b = mf(sec, profiles, u)
        fun dl(p: D3): Double { val v = p - m.c1; return v.add(m.a, -(v dot m.a)).length() }
        val near = dl(b.c1) <= dl(b.c2); val e = if (near) b.c1 else b.c2; val far = if (near) b.c2 else b.c1; val x = (far - e).normalize()
        val v = e - m.c1; val pc = m.c1.add(m.a, v dot m.a); var uu = x.add(m.a, -(x dot m.a))
        if (uu.length() < 1e-6) throw IllegalStateException("the two parts are parallel"); uu = uu.normalize()
        val flange = abs(uu dot m.eh) >= abs(uu dot m.es); val f = (if (flange) m.h / 2 else m.tw / 2) * u
        val xu = (x dot uu).let { if (it == 0.0) 1.0 else it }
        val xFace = (pc.add(uu, f) - e).dot(uu) / xu
        val y = b.eh; val zb = (x cross y).normalize()
        var cope = false; var yTop = Double.POSITIVE_INFINITY; var yBot = Double.NEGATIVE_INFINITY; var copeL = 0.0
        if (!flange && abs(m.a.z) < 0.9 && abs(m.eh dot y) > 0.9) {
            val yc = (pc - e) dot y; val hi = (m.h / 2 - m.tf) * u; val yT = yc + hi; val yB = yc - hi
            if (b.h / 2 * u > yT - 10 * u || -b.h / 2 * u < yB + 10 * u) { cope = true; yTop = yT; yBot = yB; copeL = (m.b / 2 - m.tw / 2) * u / xu + 15 * u }
        }
        return Joint(m, b, e, x, y, zb, flange, xFace, cope, yTop, yBot, copeL, u, uu, pc, if (flange) m.tf else m.tw)
    }
}
