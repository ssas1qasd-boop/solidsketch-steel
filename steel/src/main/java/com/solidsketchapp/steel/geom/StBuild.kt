package com.solidsketchapp.steel.geom

import com.solidsketchapp.core.api.Mesh
import com.solidsketchapp.steel.data.ProfileCatalog
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlin.math.PI
import kotlin.math.abs

/**
 * The solid of a steel part with the Tekla position rules: exact port of v69's live `stBuild` chain
 * (v32 L13605: members and flat plates; v41 L15228: contour plates in any plane `pts3`, tapered welded I).
 * Input is the part's `st` object (v69 field names). Units: [u] model units per mm (1 for documents in mm).
 *
 * Members: the section is placed in the frame (es, eh, a) — a along p1 → p2; eh is the section's "up", from the
 * global Z (or −X for columns, |a·Z| > 0.995), turned by the rotation (Top 0°, Front 90°, Below 180°, Back 270°, plus
 * rAng); plane (Middle / Left / Right) shifts across, depth (Middle / Front / Behind) shifts up/down, both with
 * offsets; dx1 / dx2 lengthen the start / end.
 */
class StBuild(private val profiles: ProfileCatalog, private val u: Double = 1.0, private val union: ((List<Mesh>) -> Mesh)? = null) {
    class BuildError(msg: String) : Exception(msg)

    private val rot = mapOf("Top" to 0, "Front" to 1, "Below" to 2, "Back" to 3)

    fun build(st: JsonObject): Mesh {
        val kind = st.str("kind")
        val pts3 = st["pts3"] as? JsonArray
        if (kind == "plate" && pts3 != null && pts3.size >= 3) return contourPlate3d(st, pts3)
        if (kind == "member") {
            val p = profiles.lookup(st.str("prof") ?: "")
            if (p != null && p.taper != null && p.taper != 0.0) return taperedI(st, p)
        }
        return base(st)
    }

    // ---- v32 base ----
    private fun base(st: JsonObject): Mesh {
        if (st.str("kind") == "plate") {
            val p = profiles.lookup(st.str("prof") ?: "")
            val t = thickness(p)
            val z0 = st.num("z") + (when (st.str("depth")) { "Front" -> 0.0; "Behind" -> -t * u; else -> -t * u / 2 }) + st.num("dOff") * u
            val pts = (st["pts"] as JsonArray).map { e -> (e as JsonArray).let { doubleArrayOf(it.d(0), it.d(1)) } }
            return Prism.extrude(Region(pts), t * u, Prism.Frame(D3(0.0, 0.0, z0), D3(1.0, 0.0, 0.0), D3(0.0, 1.0, 0.0), D3.Z))
        }
        val prof = st.str("prof") ?: ""
        val p = profiles.lookup(prof) ?: throw BuildError("Unknown profile $prof")
        val sec = Sections.of(p) ?: throw BuildError("Profile $prof needs a width, e.g. PL20*200")
        val p1 = st.vec("p1"); val p2 = st.vec("p2"); var a = p2 - p1
        val len = a.length(); if (len < 1e-9) throw BuildError("The two points are the same"); a = a.normalize()
        val col = abs(a dot D3.Z) > 0.995
        val dz: D3; val baseV: D3
        if (col) { dz = D3(0.0, 1.0, 0.0); baseV = D3(-1.0, 0.0, 0.0) } else { dz = D3.Z.add(a, -(a dot D3.Z)).normalize(); baseV = dz }
        val ds = (dz cross a).normalize()
        val th = ((rot[st.str("rot")] ?: 0) * 90 + st.num("rAng")) * PI / 180
        val eh = baseV.applyAxisAngle(a, th).normalize(); val es = (eh cross a).normalize()
        var s0 = Double.POSITIVE_INFINITY; var s1 = Double.NEGATIVE_INFINITY; var z0 = Double.POSITIVE_INFINITY; var z1 = Double.NEGATIVE_INFINITY
        for (q in sec.outer) { val w = (es * q[0]).add(eh, q[1]); val s = w dot ds; val zz = w dot dz; s0 = minOf(s0, s); s1 = maxOf(s1, s); z0 = minOf(z0, zz); z1 = maxOf(z1, zz) }
        val shS = (when (st.str("plane")) { "Left" -> -s0; "Right" -> -s1; else -> -(s0 + s1) / 2 }) + st.num("pOff")
        val shZ = (when (st.str("depth")) { "Front" -> -z0; "Behind" -> -z1; else -> -(z0 + z1) / 2 }) + st.num("dOff")
        val dx1 = st.num("dx1") * u; val dx2 = st.num("dx2") * u
        val origin = p1.add(a, -dx1).add(ds, shS * u).add(dz, shZ * u)
        val reg = Region(sec.outer.map { doubleArrayOf(it[0] * u, it[1] * u) }, sec.holes.map { h -> h.map { doubleArrayOf(it[0] * u, it[1] * u) } })
        return Prism.extrude(reg, len + dx1 + dx2, Prism.Frame(origin, es, eh, a))
    }

    // ---- v41: contour plate in any plane (pts3, mm) ----
    private fun contourPlate3d(st: JsonObject, pts3: JsonArray): Mesh {
        val pp = pts3.map { e -> (e as JsonArray).let { D3(it.d(0) * u, it.d(1) * u, it.d(2) * u) } }
        var nx = 0.0; var ny = 0.0; var nz = 0.0
        for (i in pp.indices) { val a = pp[i]; val b = pp[(i + 1) % pp.size]; nx += (a.y - b.y) * (a.z + b.z); ny += (a.z - b.z) * (a.x + b.x); nz += (a.x - b.x) * (a.y + b.y) }
        val n = D3(nx, ny, nz).normalize()
        var ux = pp[1] - pp[0]; ux = ux.add(n, -(ux dot n)).normalize(); val vv = n cross ux
        val pr = st.str("prof")?.let { profiles.lookup(it) }
        val t = thickness(pr) * u
        val z0 = (when (st.str("depth")) { "Front" -> 0.0; "Behind" -> -t; else -> -t / 2 }) + st.num("dOff") * u
        val poly = pp.map { q -> val d = q - pp[0]; doubleArrayOf(d dot ux, d dot vv) }
        return Prism.extrude(Region(poly), t, Prism.Frame(pp[0].add(n, z0), ux, vv, n))
    }

    // ---- v41: tapered welded I (three plates in the web plane) ----
    private fun taperedI(st: JsonObject, p: com.solidsketchapp.steel.data.Profile): Mesh {
        val p1 = st.vec("p1"); val p2 = st.vec("p2"); var a = p2 - p1; val len = a.length(); a = a.normalize()
        val col = abs(a dot D3.Z) > 0.995
        val baseV = if (col) D3(-1.0, 0.0, 0.0) else D3.Z.add(a, -(a dot D3.Z)).normalize()
        val th = ((rot[st.str("rot")] ?: 0) * 90 + st.num("rAng")) * PI / 180
        val eh = baseV.applyAxisAngle(a, th).normalize(); val es = (eh cross a).normalize()
        val dx1 = st.num("dx1") * u; val dx2 = st.num("dx2") * u; val lt = len + dx1 + dx2
        val h1 = p.h1!!; val h2 = p.h2!!
        fun hAt(x: Double) = (h1 + (h2 - h1) * (x + dx1) / lt) * u
        val ref = when (st.str("depth")) { "Behind" -> "top"; "Front" -> "bot"; else -> "mid" }
        fun yTop(x: Double) = when (ref) { "top" -> 0.0; "bot" -> hAt(x); else -> hAt(x) / 2 }
        fun yBot(x: Double) = yTop(x) - hAt(x)
        val sh = ((when (st.str("plane")) { "Left" -> p.b!! / 2; "Right" -> -p.b!! / 2; else -> 0.0 }) + st.num("pOff")) * u
        val dO = st.num("dOff") * u
        val o = p1.add(es, -sh * 0).add(eh, dO)
        val xa = -dx1; val xb = len + dx2; val tf = p.tf!! * u; val tf2 = (p.tf2 ?: p.tf) * u
        val n = a cross eh
        fun prism(poly: List<DoubleArray>, w: Double) = Prism.extrude(Region(poly), w, Prism.Frame(o.add(n, -w / 2).add(es, sh), a, eh, n))
        fun q(x: Double, y: Double) = doubleArrayOf(x, y)
        val top = prism(listOf(q(xa, yTop(xa) - tf), q(xb, yTop(xb) - tf), q(xb, yTop(xb)), q(xa, yTop(xa))), (p.bt ?: p.b!!) * u)
        val bot = prism(listOf(q(xa, yBot(xa)), q(xb, yBot(xb)), q(xb, yBot(xb) + tf2), q(xa, yBot(xa) + tf2)), (p.bb ?: p.b!!) * u)
        val web = prism(listOf(q(xa, yBot(xa) + tf2), q(xb, yBot(xb) + tf2), q(xb, yTop(xb) - tf), q(xa, yTop(xa) - tf)), p.tw!! * u)
        // v69 unions the three plates (stUnion); they only touch, so without a boolean engine they are kept side by side.
        return union?.invoke(listOf(top, bot, web)) ?: Prism.concat(listOf(top, bot, web))
    }

    /** JavaScript `(p && (p.t || p.h)) || 10`. */
    private fun thickness(p: com.solidsketchapp.steel.data.Profile?): Double = listOfNotNull(p?.t, p?.h).firstOrNull { it != 0.0 && !it.isNaN() } ?: 10.0

    companion object {
        fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull
        /** JavaScript `+st.k || 0`: numbers and numeric strings; anything else (missing, NaN, "") is 0. */
        fun JsonObject.num(k: String): Double = jsNum(this[k])
        fun JsonObject.vec(k: String): D3 = (this[k] as JsonArray).let { D3(it.d(0), it.d(1), it.d(2)) }
        fun JsonArray.d(i: Int): Double = jsNum(this[i])
        fun jsNum(e: JsonElement?): Double {
            val p = e as? JsonPrimitive ?: return 0.0
            p.booleanOrNull?.let { if (!p.isString) return if (it) 1.0 else 0.0 }
            val v = p.doubleOrNull ?: com.solidsketchapp.steel.model.StGrid.jsNumber(p.content)
            return if (v.isNaN()) 0.0 else v
        }
    }
}
