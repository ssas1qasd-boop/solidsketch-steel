package com.solidsketchapp.core.view.gl

import com.solidsketchapp.core.api.Mesh
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * A body's CAD edges as line segments: web v69 `featureEdges` (EDGE_ANGLE = 12°): vertices welded by position,
 * an edge is drawn when two of its (non-degenerate) triangles meet at more than 12°. Open edges (one triangle) are not
 * drawn, as in v69. Pure Kotlin (unit-tested); the renderer draws the result in v69's edge colour 0x262b34.
 */
object FeatureEdges {
    const val EDGE_ANGLE = 12.0

    fun of(m: Mesh, angleDeg: Double = EDGE_ANGLE): FloatArray {
        val p = m.positions; val ix = m.indices; val nt = m.triangleCount
        if (nt == 0) return FloatArray(0)
        // Weld by exact position (flat-shaded meshes repeat the same coordinates).
        val weld = IntArray(m.vertexCount)
        val hx = HashMap<Long, MutableList<Int>>()
        var nextId = 0
        for (v in 0 until m.vertexCount) {
            val bx = java.lang.Float.floatToIntBits(p[v * 3]); val by = java.lang.Float.floatToIntBits(p[v * 3 + 1]); val bz = java.lang.Float.floatToIntBits(p[v * 3 + 2])
            val key = (bx.toLong() * 73856093L) xor (by.toLong() * 19349663L) xor (bz.toLong() * 83492791L)
            val bucket = hx.getOrPut(key) { ArrayList(1) }
            var id = -1
            for (w in bucket) if (p[w * 3] == p[v * 3] && p[w * 3 + 1] == p[v * 3 + 1] && p[w * 3 + 2] == p[v * 3 + 2]) { id = weld[w]; break }
            if (id < 0) { id = nextId++; bucket += v }
            weld[v] = id
        }
        // Triangle normals; degenerate (zero-area) triangles are ignored, as v69 does.
        val nrm = FloatArray(nt * 3); val degenerate = BooleanArray(nt)
        for (t in 0 until nt) {
            val a = ix[t * 3] * 3; val b = ix[t * 3 + 1] * 3; val c = ix[t * 3 + 2] * 3
            val ux = p[b] - p[a]; val uy = p[b + 1] - p[a + 1]; val uz = p[b + 2] - p[a + 2]
            val vx = p[c] - p[a]; val vy = p[c + 1] - p[a + 1]; val vz = p[c + 2] - p[a + 2]
            val nx = uy * vz - uz * vy; val ny = uz * vx - ux * vz; val nz = ux * vy - uy * vx
            val l = sqrt(nx * nx + ny * ny + nz * nz)
            if (l < 1e-12f) { degenerate[t] = true; continue }
            nrm[t * 3] = nx / l; nrm[t * 3 + 1] = ny / l; nrm[t * 3 + 2] = nz / l
        }
        val cosLimit = cos(Math.toRadians(angleDeg)).toFloat()
        // Edge -> triangles; sharp when any pair of its triangles bends more than the limit.
        val tris = HashMap<Long, IntArray>(nt * 2)   // [count, t0, t1, t2, ...] grown as needed
        val sharp = HashSet<Long>()
        val ends = HashMap<Long, Int>(nt * 2); val endsB = HashMap<Long, Int>(nt * 2)   // edge -> its two original vertices (output positions)
        for (t in 0 until nt) {
            if (degenerate[t]) continue
            for (e in 0..2) {
                val va = ix[t * 3 + e]; val vb = ix[t * 3 + (e + 1) % 3]
                val a = weld[va]; val b = weld[vb]
                if (a == b) continue
                val key = if (a < b) (a.toLong() shl 32) or b.toLong() else (b.toLong() shl 32) or a.toLong()
                val list = tris[key]
                if (list == null) { tris[key] = intArrayOf(1, t, 0, 0); ends[key] = va; endsB[key] = vb; continue }
                if (key in sharp) continue
                val n = list[0]
                for (i in 1..n) { val o = list[i]; if (nrm[o * 3] * nrm[t * 3] + nrm[o * 3 + 1] * nrm[t * 3 + 1] + nrm[o * 3 + 2] * nrm[t * 3 + 2] < cosLimit) { sharp += key; break } }
                if (key !in sharp) { val grown = if (n + 1 < list.size) list else list.copyOf(list.size * 2); grown[0] = n + 1; grown[n + 1] = t; tris[key] = grown }
            }
        }
        val out = FloatArray(sharp.size * 6); var k = 0
        for (key in sharp) { val a = ends.getValue(key); val b = endsB.getValue(key)
            out[k++] = p[a * 3]; out[k++] = p[a * 3 + 1]; out[k++] = p[a * 3 + 2]; out[k++] = p[b * 3]; out[k++] = p[b * 3 + 1]; out[k++] = p[b * 3 + 2] }
        return out
    }
}
