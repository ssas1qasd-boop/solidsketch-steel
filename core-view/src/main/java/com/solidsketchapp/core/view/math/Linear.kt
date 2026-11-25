package com.solidsketchapp.core.view.math

import com.solidsketchapp.core.api.Vec3
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

operator fun Vec3.plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
operator fun Vec3.minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
operator fun Vec3.times(s: Double) = Vec3(x * s, y * s, z * s)
operator fun Vec3.unaryMinus() = Vec3(-x, -y, -z)
infix fun Vec3.dot(o: Vec3) = x * o.x + y * o.y + z * o.z
infix fun Vec3.cross(o: Vec3) = Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
fun Vec3.length() = sqrt(this dot this)
fun Vec3.normalized(): Vec3 { val l = length(); return if (l > 0) this * (1 / l) else this }

/** Unit quaternion (w, x, y, z). Used for the camera so orbit never clamps or flips. */
data class Quat(val w: Double, val x: Double, val y: Double, val z: Double) {
    operator fun times(q: Quat) = Quat(
        w * q.w - x * q.x - y * q.y - z * q.z,
        w * q.x + x * q.w + y * q.z - z * q.y,
        w * q.y - x * q.z + y * q.w + z * q.x,
        w * q.z + x * q.y - y * q.x + z * q.w,
    )

    fun normalized(): Quat { val l = sqrt(w * w + x * x + y * y + z * z); return Quat(w / l, x / l, y / l, z / l) }

    fun rotate(v: Vec3): Vec3 {
        val u = Vec3(x, y, z)
        val t = (u cross v) * 2.0
        return v + t * w + (u cross t)
    }

    fun conjugate() = Quat(w, -x, -y, -z)

    companion object {
        val IDENTITY = Quat(1.0, 0.0, 0.0, 0.0)
        fun axisAngle(axis: Vec3, rad: Double): Quat {
            val a = axis.normalized(); val s = sin(rad / 2)
            return Quat(cos(rad / 2), a.x * s, a.y * s, a.z * s)
        }
        /** Rotation whose columns are the given orthonormal axes (camera right, up, back). */
        fun fromAxes(r: Vec3, u: Vec3, b: Vec3): Quat {
            val m00 = r.x; val m01 = u.x; val m02 = b.x
            val m10 = r.y; val m11 = u.y; val m12 = b.y
            val m20 = r.z; val m21 = u.z; val m22 = b.z
            val tr = m00 + m11 + m22
            return if (tr > 0) { val s = sqrt(tr + 1.0) * 2; Quat(0.25 * s, (m21 - m12) / s, (m02 - m20) / s, (m10 - m01) / s) }
            else if (m00 > m11 && m00 > m22) { val s = sqrt(1.0 + m00 - m11 - m22) * 2; Quat((m21 - m12) / s, 0.25 * s, (m01 + m10) / s, (m02 + m20) / s) }
            else if (m11 > m22) { val s = sqrt(1.0 + m11 - m00 - m22) * 2; Quat((m02 - m20) / s, (m01 + m10) / s, 0.25 * s, (m12 + m21) / s) }
            else { val s = sqrt(1.0 + m22 - m00 - m11) * 2; Quat((m10 - m01) / s, (m02 + m20) / s, (m12 + m21) / s, 0.25 * s) }.normalized()
        }
    }
}

/** Column-major 4x4 float matrices for GL. */
object Mat4 {
    fun multiply(a: FloatArray, b: FloatArray): FloatArray {
        val r = FloatArray(16)
        for (c in 0 until 4) for (row in 0 until 4) {
            var s = 0f
            for (k in 0 until 4) s += a[k * 4 + row] * b[c * 4 + k]
            r[c * 4 + row] = s
        }
        return r
    }

    /** Orthographic projection of the box [-halfH*aspect, halfH*aspect] x [-halfH, halfH] x [-near, -far] (column-major, GL). */
    fun ortho(halfH: Double, aspect: Double, near: Double, far: Double): FloatArray {
        val r = FloatArray(16)
        r[0] = (1.0 / (halfH * aspect)).toFloat(); r[5] = (1.0 / halfH).toFloat()
        r[10] = (-2.0 / (far - near)).toFloat(); r[14] = (-(far + near) / (far - near)).toFloat(); r[15] = 1f
        return r
    }

    fun perspective(fovYRad: Double, aspect: Double, near: Double, far: Double): FloatArray {
        val f = 1.0 / kotlin.math.tan(fovYRad / 2)
        val r = FloatArray(16)
        r[0] = (f / aspect).toFloat(); r[5] = f.toFloat()
        r[10] = ((far + near) / (near - far)).toFloat(); r[11] = -1f
        r[14] = (2 * far * near / (near - far)).toFloat()
        return r
    }
}
