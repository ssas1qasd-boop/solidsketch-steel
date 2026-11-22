package com.solidsketchapp.core.view

import com.solidsketchapp.core.api.BodyId
import com.solidsketchapp.core.api.Mesh
import com.solidsketchapp.core.api.Ray
import com.solidsketchapp.core.api.SelectionApi
import com.solidsketchapp.core.api.Vec3
import com.solidsketchapp.core.view.math.cross
import com.solidsketchapp.core.view.math.dot
import com.solidsketchapp.core.view.math.minus
import com.solidsketchapp.core.view.math.normalized
import com.solidsketchapp.core.view.math.plus
import com.solidsketchapp.core.view.math.times
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Axis-aligned box of a mesh. */
data class Aabb(val min: Vec3, val max: Vec3) {
    fun union(o: Aabb) = Aabb(
        Vec3(min(min.x, o.min.x), min(min.y, o.min.y), min(min.z, o.min.z)),
        Vec3(max(max.x, o.max.x), max(max.y, o.max.y), max(max.z, o.max.z)),
    )

    /** Distance along the ray to the box, or null if missed (slab test). */
    fun hit(r: Ray): Double? {
        var t0 = 0.0; var t1 = Double.MAX_VALUE
        val o = doubleArrayOf(r.origin.x, r.origin.y, r.origin.z)
        val d = doubleArrayOf(r.direction.x, r.direction.y, r.direction.z)
        val lo = doubleArrayOf(min.x, min.y, min.z); val hi = doubleArrayOf(max.x, max.y, max.z)
        for (i in 0 until 3) {
            if (abs(d[i]) < 1e-15) { if (o[i] < lo[i] || o[i] > hi[i]) return null; continue }
            var a = (lo[i] - o[i]) / d[i]; var b = (hi[i] - o[i]) / d[i]
            if (a > b) { val t = a; a = b; b = t }
            t0 = max(t0, a); t1 = min(t1, b)
            if (t0 > t1) return null
        }
        return t0
    }

    companion object {
        fun of(m: Mesh): Aabb? {
            if (m.vertexCount == 0) return null
            val p = m.positions
            var x0 = Double.MAX_VALUE; var y0 = x0; var z0 = x0; var x1 = -x0; var y1 = x1; var z1 = x1
            for (i in 0 until m.vertexCount) {
                val x = p[3 * i].toDouble(); val y = p[3 * i + 1].toDouble(); val z = p[3 * i + 2].toDouble()
                x0 = min(x0, x); y0 = min(y0, y); z0 = min(z0, z); x1 = max(x1, x); y1 = max(y1, y); z1 = max(z1, z)
            }
            return Aabb(Vec3(x0, y0, z0), Vec3(x1, y1, z1))
        }
    }
}

/** A body the picker can test. */
class PickBody(val id: BodyId, val mesh: Mesh, val box: Aabb)

/** CPU picking: box test first, then every triangle (Moller-Trumbore). Nearest hit wins. */
object Picker {
    fun pick(ray: Ray, bodies: Iterable<PickBody>): SelectionApi.Hit? {
        val cands = bodies.mapNotNull { b -> b.box.hit(ray)?.let { b to it } }.sortedBy { it.second }
        var best: SelectionApi.Hit.Face? = null
        var bestT = Double.MAX_VALUE
        for ((b, tBox) in cands) {
            if (tBox > bestT) break
            val p = b.mesh.positions; val ix = b.mesh.indices
            for (t in 0 until b.mesh.triangleCount) {
                val a = v(p, ix[3 * t]); val bb = v(p, ix[3 * t + 1]); val c = v(p, ix[3 * t + 2])
                val d = intersect(ray, a, bb, c) ?: continue
                if (d < bestT) {
                    bestT = d
                    val n = ((bb - a) cross (c - a)).normalized()
                    best = SelectionApi.Hit.Face(b.id, ray.origin + ray.direction * d, t, n)
                }
            }
        }
        return best
    }

    private fun v(p: FloatArray, i: Int) = Vec3(p[3 * i].toDouble(), p[3 * i + 1].toDouble(), p[3 * i + 2].toDouble())

    /** Distance along the ray to triangle (a, b, c), either side, or null. */
    fun intersect(r: Ray, a: Vec3, b: Vec3, c: Vec3): Double? {
        val e1 = b - a; val e2 = c - a
        val pv = r.direction cross e2
        val det = e1 dot pv
        if (abs(det) < 1e-12) return null
        val inv = 1 / det
        val tv = r.origin - a
        val u = (tv dot pv) * inv
        if (u < 0 || u > 1) return null
        val qv = tv cross e1
        val w = (r.direction dot qv) * inv
        if (w < 0 || u + w > 1) return null
        val t = (e2 dot qv) * inv
        return if (t > 1e-9) t else null
    }
}
