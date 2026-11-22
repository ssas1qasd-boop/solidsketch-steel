package com.solidsketchapp.core.view

import com.solidsketchapp.core.api.BooleanEngine
import com.solidsketchapp.core.api.Mesh

/** BooleanEngine on sscore-lite (Manifold). Main-port rule: Manifold first, CGAL fallback (not in Steel). */
class ManifoldBooleanEngine : BooleanEngine {
    override fun subtract(a: Mesh, cutters: List<Mesh>): Mesh =
        if (cutters.isEmpty()) a else NativeCore.subtractMany(a, cutters.toTypedArray())

    override fun union(parts: List<Mesh>): Mesh {
        require(parts.isNotEmpty()) { "union of nothing" }
        return if (parts.size == 1) parts[0] else NativeCore.unionMany(parts.toTypedArray())
    }

    override fun intersect(a: Mesh, b: Mesh): Mesh = NativeCore.booleanOp(2, a, b)
    override fun isClosedSolid(m: Mesh): Boolean = NativeCore.isClosedSolid(m)
    override fun volume(m: Mesh): Double = NativeCore.volume(m)
}
