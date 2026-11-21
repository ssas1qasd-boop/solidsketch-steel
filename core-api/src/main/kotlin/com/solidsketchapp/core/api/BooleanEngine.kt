package com.solidsketchapp.core.api

/** Solid booleans. Main-port rule: Manifold first, CGAL fallback. */
interface BooleanEngine {
    fun subtract(a: Mesh, cutters: List<Mesh>): Mesh
    fun union(parts: List<Mesh>): Mesh
    fun intersect(a: Mesh, b: Mesh): Mesh
    fun isClosedSolid(m: Mesh): Boolean
    fun volume(m: Mesh): Double
}
