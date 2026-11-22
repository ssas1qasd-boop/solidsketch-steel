package com.solidsketchapp.core.view

import com.solidsketchapp.core.api.Mesh

/** JNI entry to native/sscore-lite (Manifold). Calls throw IllegalStateException on failure. */
object NativeCore {
    init {
        System.loadLibrary("sscore_lite_jni")
    }

    external fun version(): String
    external fun boxVolume(sx: Float, sy: Float, sz: Float): Double
    /** Box with one corner at the origin. */
    external fun box(sx: Float, sy: Float, sz: Float): Mesh
    /** Prism: XY outline (x0, y0, x1, y1, ...) from z = 0 to z = depth. */
    external fun extrude(outlineXy: FloatArray, depth: Float): Mesh
    /** Cylinder along +Z from z = 0. */
    external fun cylinder(radius: Float, height: Float, segments: Int): Mesh
    /** op: 0 subtract, 1 union, 2 intersect. */
    external fun booleanOp(op: Int, a: Mesh, b: Mesh): Mesh
    external fun subtractMany(a: Mesh, cutters: Array<Mesh>): Mesh
    external fun unionMany(parts: Array<Mesh>): Mesh
    external fun isClosedSolid(m: Mesh): Boolean
    external fun volume(m: Mesh): Double
}
