package com.solidsketchapp.steel.geom

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** A double-precision 3D vector, immutable (the operations v69 uses from THREE.Vector3). */
data class D3(val x: Double, val y: Double, val z: Double) {
    operator fun plus(o: D3) = D3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: D3) = D3(x - o.x, y - o.y, z - o.z)
    operator fun times(k: Double) = D3(x * k, y * k, z * k)
    operator fun unaryMinus() = D3(-x, -y, -z)
    infix fun dot(o: D3) = x * o.x + y * o.y + z * o.z
    infix fun cross(o: D3) = D3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun length() = sqrt(x * x + y * y + z * z)
    fun distanceTo(o: D3) = (this - o).length()
    /** THREE.Vector3.normalize: a zero vector stays zero. */
    fun normalize(): D3 { val l = length(); return if (l == 0.0) this else D3(x / l, y / l, z / l) }
    /** this + v * k (THREE addScaledVector). */
    fun add(v: D3, k: Double) = D3(x + v.x * k, y + v.y * k, z + v.z * k)
    fun lerp(o: D3, t: Double) = D3(x + (o.x - x) * t, y + (o.y - y) * t, z + (o.z - z) * t)
    fun toList() = listOf(x, y, z)

    /** THREE.Vector3.applyAxisAngle: rotation about a unit [axis] by [angle] radians (via a quaternion, as three.js). */
    fun applyAxisAngle(axis: D3, angle: Double): D3 {
        val h = angle / 2; val s = sin(h)
        val qx = axis.x * s; val qy = axis.y * s; val qz = axis.z * s; val qw = cos(h)
        // three.js Vector3.applyQuaternion
        val tx = 2 * (qy * z - qz * y); val ty = 2 * (qz * x - qx * z); val tz = 2 * (qx * y - qy * x)
        return D3(x + qw * tx + qy * tz - qz * ty, y + qw * ty + qz * tx - qx * tz, z + qw * tz + qx * ty - qy * tx)
    }

    companion object {
        val ZERO = D3(0.0, 0.0, 0.0)
        val Z = D3(0.0, 0.0, 1.0)
        fun of(l: List<Double>) = D3(l[0], l[1], l[2])
    }
}

/** A 2D outline with holes, in millimetres (v69 `{ outer, holes }`). */
data class Region(val outer: List<DoubleArray>, val holes: List<List<DoubleArray>> = emptyList())
