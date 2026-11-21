package com.solidsketchapp.core.api

/** A point or direction in model space (millimetres). */
data class Vec3(val x: Double, val y: Double, val z: Double)

/** A ray in model space, e.g. from a touch point into the scene. */
data class Ray(val origin: Vec3, val direction: Vec3)

/** Stable id of a body in the document (the web build's body id). */
@JvmInline
value class BodyId(val value: String)

/**
 * Triangle mesh exchanged with the C core.
 * [positions] is xyz per vertex; [indices] has three entries per triangle.
 */
class Mesh(val positions: FloatArray, val indices: IntArray) {
    val vertexCount: Int get() = positions.size / 3
    val triangleCount: Int get() = indices.size / 3
}
