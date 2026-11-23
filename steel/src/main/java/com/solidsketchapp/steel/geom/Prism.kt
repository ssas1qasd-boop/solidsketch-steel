package com.solidsketchapp.steel.geom

import com.solidsketchapp.core.api.Mesh
import kotlin.math.abs

/**
 * Extrusions as closed, outward-facing triangle meshes (the parts of v69's `C.extrudeRegions` and `C.placeInFrame`
 * that steel parts use). Vertices: the outline at z = 0 and at z = depth, nothing else, so the vertex set equals
 * the Manifold mesh v69 makes. Caps are triangulated by ear clipping with the holes bridged into the outer ring.
 */
object Prism {
    /** A local frame: local (x, y, z) -> origin + x·u + y·v + z·n. */
    data class Frame(val origin: D3, val u: D3, val v: D3, val n: D3) {
        fun apply(x: Double, y: Double, z: Double) = origin.add(u, x).add(v, y).add(n, z)
        companion object { val IDENTITY = Frame(D3.ZERO, D3(1.0, 0.0, 0.0), D3(0.0, 1.0, 0.0), D3.Z) }
    }

    /** [region] extruded from local z = 0 to z = [depth], then placed in [frame]. */
    fun extrude(region: Region, depth: Double, frame: Frame = Frame.IDENTITY): Mesh {
        val outer = ccw(region.outer, true)
        val holes = region.holes.map { ccw(it, false) }
        val rings = listOf(outer) + holes
        val n = rings.sumOf { it.size }
        val pos = FloatArray(n * 2 * 3)
        val base = IntArray(rings.size); var k = 0
        for ((ri, ring) in rings.withIndex()) { base[ri] = k; for (q in ring) { put(pos, k, frame.apply(q[0], q[1], 0.0)); put(pos, k + n, frame.apply(q[0], q[1], depth)); k++ } }
        val idx = ArrayList<Int>()
        val capTris = triangulate(outer, holes)   // indices into the concatenated rings, CCW seen from +z
        val flip = depth < 0
        for (t in capTris) {
            // top cap (z = depth) faces +n: keep CCW; bottom cap faces -n: reverse.
            if (!flip) { idx += listOf(t[0] + n, t[1] + n, t[2] + n, t[0], t[2], t[1]) } else { idx += listOf(t[0] + n, t[2] + n, t[1] + n, t[0], t[1], t[2]) }
        }
        for ((ri, ring) in rings.withIndex()) {
            val b = base[ri]; val m = ring.size
            for (i in 0 until m) {
                val a = b + i; val c = b + (i + 1) % m
                // outer ring CCW and holes CW: the side faces (a, c, c', a') point outward for depth > 0.
                if (!flip) idx += listOf(a, c, c + n, a, c + n, a + n) else idx += listOf(a, c + n, c, a, a + n, c + n)
            }
        }
        val mesh = Mesh(pos, idx.toIntArray())
        // A mirrored frame (left-handed) turns the solid inside out; flip it back like Manifold's transform does.
        return if (signedVolume(mesh) < 0) flipped(mesh) else mesh
    }

    private fun put(a: FloatArray, i: Int, p: D3) { a[i * 3] = p.x.toFloat(); a[i * 3 + 1] = p.y.toFloat(); a[i * 3 + 2] = p.z.toFloat() }

    fun area(r: List<DoubleArray>): Double { var s = 0.0; for (i in r.indices) { val a = r[i]; val b = r[(i + 1) % r.size]; s += a[0] * b[1] - b[0] * a[1] }; return s / 2 }
    private fun ccw(r: List<DoubleArray>, want: Boolean) = if ((area(r) > 0) == want) r else r.reversed()

    fun signedVolume(m: Mesh): Double {
        var v = 0.0; val p = m.positions; val ix = m.indices
        for (t in 0 until m.triangleCount) {
            val a = ix[t * 3] * 3; val b = ix[t * 3 + 1] * 3; val c = ix[t * 3 + 2] * 3
            v += (p[a].toDouble() * (p[b + 1].toDouble() * p[c + 2] - p[b + 2].toDouble() * p[c + 1]) -
                p[a + 1].toDouble() * (p[b].toDouble() * p[c + 2] - p[b + 2].toDouble() * p[c]) +
                p[a + 2].toDouble() * (p[b].toDouble() * p[c + 1] - p[b + 1].toDouble() * p[c])) / 6
        }
        return v
    }

    fun flipped(m: Mesh): Mesh { val ix = m.indices.copyOf(); for (t in 0 until m.triangleCount) { val s = ix[t * 3 + 1]; ix[t * 3 + 1] = ix[t * 3 + 2]; ix[t * 3 + 2] = s }; return Mesh(m.positions, ix) }

    /** Several meshes as one (no boolean): used where v69 unions parts that only touch. */
    fun concat(ms: List<Mesh>): Mesh {
        val pos = FloatArray(ms.sumOf { it.positions.size }); val idx = IntArray(ms.sumOf { it.indices.size }); var po = 0; var io = 0; var vo = 0
        for (m in ms) { m.positions.copyInto(pos, po); for (i in m.indices) idx[io++] = i + vo; po += m.positions.size; vo += m.vertexCount }
        return Mesh(pos, idx)
    }

    // ---------------- ear clipping with holes ----------------

    /** Triangles (CCW) of the polygon [outer] (CCW) with [holes] (CW); indices into outer ++ holes. */
    fun triangulate(outer: List<DoubleArray>, holes: List<List<DoubleArray>>): List<IntArray> {
        val pts = ArrayList<DoubleArray>(); pts += outer; val holeIdx = holes.map { h -> val s = pts.size; pts += h; IntArray(h.size) { s + it } }
        var ring = ArrayList<Int>(outer.indices.toList())
        // Bridge holes, rightmost first (Eberly).
        for (h in holeIdx.sortedByDescending { hi -> hi.maxOf { pts[it][0] } }) ring = bridge(pts, ring, h)
        return earClip(pts, ring)
    }

    private fun bridge(pts: List<DoubleArray>, ring: ArrayList<Int>, hole: IntArray): ArrayList<Int> {
        val mi = hole.indices.maxByOrNull { pts[hole[it]][0] }!!; val m = pts[hole[mi]]
        // Ray from m to +x: the nearest crossing with a ring edge.
        var best = -1; var bestX = Double.POSITIVE_INFINITY
        for (i in ring.indices) {
            val a = pts[ring[i]]; val b = pts[ring[(i + 1) % ring.size]]
            if ((a[1] > m[1]) == (b[1] > m[1]) && !(a[1] == m[1] || b[1] == m[1])) continue
            if (a[1] == b[1]) continue
            val t = (m[1] - a[1]) / (b[1] - a[1]); if (t < 0 || t > 1) continue
            val x = a[0] + t * (b[0] - a[0]); if (x < m[0] - 1e-12) continue
            if (x < bestX) { bestX = x; best = if (a[0] > b[0]) i else (i + 1) % ring.size }
        }
        if (best < 0) best = ring.indices.minByOrNull { val q = pts[ring[it]]; (q[0] - m[0]) * (q[0] - m[0]) + (q[1] - m[1]) * (q[1] - m[1]) }!!
        // A ring vertex inside the triangle (m, crossing, candidate) blocks the bridge: take the one closest in angle.
        val cand = pts[ring[best]]; val ip = doubleArrayOf(bestX, m[1])
        var pick = best; var bestAng = Double.POSITIVE_INFINITY
        for (i in ring.indices) {
            if (ring[i] == ring[best]) continue
            val q = pts[ring[i]]
            if (q[0] >= m[0] && inTri(q, m, ip, cand)) {
                val ang = abs(q[1] - m[1]) / maxOf(q[0] - m[0], 1e-12)
                if (ang < bestAng || (ang == bestAng && q[0] < pts[ring[pick]][0])) { bestAng = ang; pick = i }
            }
        }
        val out = ArrayList<Int>(ring.size + hole.size + 2)
        for (i in 0..pick) out += ring[i]
        for (j in 0..hole.size) out += hole[(mi + j) % hole.size]
        out += ring[pick]
        for (i in pick + 1 until ring.size) out += ring[i]
        return out
    }

    private fun cross(o: DoubleArray, a: DoubleArray, b: DoubleArray) = (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0])
    private fun inTri(p: DoubleArray, a: DoubleArray, b: DoubleArray, c: DoubleArray): Boolean {
        val d1 = cross(a, b, p); val d2 = cross(b, c, p); val d3 = cross(c, a, p)
        val neg = d1 < 0 || d2 < 0 || d3 < 0; val pos = d1 > 0 || d2 > 0 || d3 > 0
        return !(neg && pos)
    }

    private fun earClip(pts: List<DoubleArray>, ring0: List<Int>): List<IntArray> {
        val ring = ArrayList(ring0); val out = ArrayList<IntArray>()
        var guard = 0
        while (ring.size > 3 && guard++ < 100000) {
            var clipped = false
            for (i in ring.indices) {
                val ia = ring[(i + ring.size - 1) % ring.size]; val ib = ring[i]; val ic = ring[(i + 1) % ring.size]
                val a = pts[ia]; val b = pts[ib]; val c = pts[ic]
                if (cross(a, b, c) <= 1e-12) continue   // reflex or flat
                var ok = true
                for (j in ring) {
                    if (j == ia || j == ib || j == ic) continue
                    val q = pts[j]
                    // bridge duplicates share coordinates with a corner: not inside.
                    if ((q[0] == a[0] && q[1] == a[1]) || (q[0] == b[0] && q[1] == b[1]) || (q[0] == c[0] && q[1] == c[1])) continue
                    if (inTri(q, a, b, c)) { ok = false; break }
                }
                if (!ok) continue
                out += intArrayOf(ia, ib, ic); ring.removeAt(i); clipped = true; break
            }
            if (!clipped) {   // degenerate leftovers (collinear runs): drop a flat vertex, else fan the rest
                val flat = ring.indices.firstOrNull { i -> abs(cross(pts[ring[(i + ring.size - 1) % ring.size]], pts[ring[i]], pts[ring[(i + 1) % ring.size]])) <= 1e-12 }
                if (flat != null) { ring.removeAt(flat); continue }
                for (i in 1 until ring.size - 1) out += intArrayOf(ring[0], ring[i], ring[i + 1]); return out
            }
        }
        if (ring.size == 3) out += intArrayOf(ring[0], ring[1], ring[2])
        return out
    }
}
