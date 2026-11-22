package com.solidsketchapp.core.view

import com.solidsketchapp.core.api.Mesh
import com.solidsketchapp.core.api.Vec3
import com.solidsketchapp.core.view.math.Quat

/** Small rigid transforms on meshes (no copy of the index buffer). */
object MeshOps {
    fun translate(m: Mesh, d: Vec3): Mesh {
        val p = m.positions.copyOf()
        for (i in 0 until m.vertexCount) { p[3 * i] += d.x.toFloat(); p[3 * i + 1] += d.y.toFloat(); p[3 * i + 2] += d.z.toFloat() }
        return Mesh(p, m.indices)
    }

    /** Rotates about the origin, then translates. */
    fun transform(m: Mesh, q: Quat, d: Vec3 = Vec3(0.0, 0.0, 0.0)): Mesh {
        val p = m.positions.copyOf()
        for (i in 0 until m.vertexCount) {
            val v = q.rotate(Vec3(p[3 * i].toDouble(), p[3 * i + 1].toDouble(), p[3 * i + 2].toDouble()))
            p[3 * i] = (v.x + d.x).toFloat(); p[3 * i + 1] = (v.y + d.y).toFloat(); p[3 * i + 2] = (v.z + d.z).toFloat()
        }
        return Mesh(p, m.indices)
    }

    /** Signed volume (positive for an outward-facing closed mesh). */
    fun volume(m: Mesh): Double {
        val p = m.positions; val ix = m.indices; var v = 0.0
        for (t in 0 until m.triangleCount) {
            val a = 3 * ix[3 * t]; val b = 3 * ix[3 * t + 1]; val c = 3 * ix[3 * t + 2]
            v += p[a].toDouble() * (p[b + 1].toDouble() * p[c + 2] - p[b + 2].toDouble() * p[c + 1]) -
                p[a + 1].toDouble() * (p[b].toDouble() * p[c + 2] - p[b + 2].toDouble() * p[c]) +
                p[a + 2].toDouble() * (p[b].toDouble() * p[c + 1] - p[b + 1].toDouble() * p[c])
        }
        return v / 6
    }
}
