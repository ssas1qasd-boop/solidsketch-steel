package com.solidsketchapp.core.view

import com.solidsketchapp.core.api.Ray
import com.solidsketchapp.core.api.Vec3
import com.solidsketchapp.core.view.math.Mat4
import com.solidsketchapp.core.view.math.Quat
import com.solidsketchapp.core.view.math.cross
import com.solidsketchapp.core.view.math.dot
import com.solidsketchapp.core.view.math.minus
import com.solidsketchapp.core.view.math.normalized
import com.solidsketchapp.core.view.math.plus
import com.solidsketchapp.core.view.math.times
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

/**
 * Perspective camera that orbits a target. Orbit is fully free (owner rule): it rotates
 * about the camera's own axes with a quaternion, so any number of turns in any direction
 * works, with no clamping at the poles and no gimbal flip. Model units are millimetres, Z up.
 */
class OrbitCamera {
    var target = Vec3(0.0, 0.0, 0.0)
    var distance = 10_000.0
        private set
    /** Camera-to-world rotation: columns are right, up, back. */
    var rotation: Quat = defaultRotation()
        private set
    val fovY = Math.toRadians(45.0)
    var viewportWidth = 1
    var viewportHeight = 1
    /** Radius of the scene, for near/far planes. */
    var sceneRadius = 10_000.0

    val aspect get() = viewportWidth.toDouble() / max(1, viewportHeight)
    val eye get() = target + rotation.rotate(Vec3(0.0, 0.0, distance))
    val right get() = rotation.rotate(Vec3(1.0, 0.0, 0.0))
    val up get() = rotation.rotate(Vec3(0.0, 1.0, 0.0))
    val back get() = rotation.rotate(Vec3(0.0, 0.0, 1.0))

    /** Drag by (dx, dy) pixels: horizontal turns about the camera's up axis, vertical about its right axis. */
    fun orbit(dxPx: Float, dyPx: Float) {
        val k = Math.PI / max(1, min(viewportWidth, viewportHeight)) * 1.5
        val yaw = Quat.axisAngle(Vec3(0.0, 1.0, 0.0), -dxPx * k)
        val pitch = Quat.axisAngle(Vec3(1.0, 0.0, 0.0), -dyPx * k)
        rotation = (rotation * yaw * pitch).normalized()
    }

    /** Drag the model by (dx, dy) pixels (two fingers). */
    fun pan(dxPx: Float, dyPx: Float) {
        val perPx = 2 * distance * tan(fovY / 2) / max(1, viewportHeight)
        target = target - right * (dxPx * perPx) + up * (dyPx * perPx)
    }

    /** factor < 1 moves closer. */
    fun zoom(factor: Double) {
        distance = (distance * factor).coerceIn(1.0, 1e8)
    }

    fun setView(target: Vec3, distance: Double, rotation: Quat) {
        this.target = target; this.distance = distance.coerceIn(1.0, 1e8); this.rotation = rotation.normalized()
    }

    /** Looks from [eye] at [target] with Z up on screen (three.js camera.lookAt, as web v69 places its camera). */
    fun lookAt(target: Vec3, eye: Vec3) {
        val b = (eye - target).normalized(); var r = Vec3(0.0, 0.0, 1.0) cross b
        if ((r dot r) < 1e-12) r = Vec3(1.0, 0.0, 0.0)
        r = r.normalized(); val u = b cross r
        setView(target, (eye - target).let { kotlin.math.sqrt(it dot it) }, Quat.fromAxes(r, u, b))
    }

    /** Frames the box [min]..[max] keeping the current direction. */
    fun fit(min: Vec3, max: Vec3) {
        target = (min + max) * 0.5
        val radius = max(((max - min).let { kotlin.math.sqrt(it dot it) }) / 2, 1.0)
        sceneRadius = radius
        val half = min(fovY / 2, kotlin.math.atan(tan(fovY / 2) * aspect))
        distance = radius / sin(half) * 1.05
    }

    fun viewMatrix(): FloatArray {
        val r = right; val u = up; val b = back; val e = eye
        return floatArrayOf(
            r.x.toFloat(), u.x.toFloat(), b.x.toFloat(), 0f,
            r.y.toFloat(), u.y.toFloat(), b.y.toFloat(), 0f,
            r.z.toFloat(), u.z.toFloat(), b.z.toFloat(), 0f,
            (-(r dot e)).toFloat(), (-(u dot e)).toFloat(), (-(b dot e)).toFloat(), 1f,
        )
    }

    fun near() = max(distance - sceneRadius * 2, distance * 1e-3).coerceAtLeast(0.5)
    fun far() = distance + sceneRadius * 4
    fun projectionMatrix(): FloatArray = Mat4.perspective(fovY, aspect, near(), far())
    fun viewProjection(): FloatArray = Mat4.multiply(projectionMatrix(), viewMatrix())

    /** Ray from the eye through pixel ([x], [y]) (origin top-left). */
    fun screenRay(x: Float, y: Float): Ray {
        val nx = 2.0 * x / max(1, viewportWidth) - 1
        val ny = 1 - 2.0 * y / max(1, viewportHeight)
        val t = tan(fovY / 2)
        val dir = (right * (nx * t * aspect) + up * (ny * t) - back).normalized()
        return Ray(eye, dir)
    }

    /** Screen pixels per model unit at [p] (perspective: what a 1-unit sprite at [p] measures on screen), or null behind the camera. */
    fun pxPerUnit(p: Vec3): Double? {
        val zc = -((p - eye) dot back); if (zc <= 1e-9) return null
        return viewportHeight / (2 * tan(fovY / 2) * zc)
    }

    /** Pixel of a model point, or null if it is behind the camera. */
    fun worldToScreen(p: Vec3): Pair<Float, Float>? {
        val d = p - eye
        val zc = -(d dot back)
        if (zc <= 1e-9) return null
        val t = tan(fovY / 2)
        val nx = (d dot right) / (zc * t * aspect)
        val ny = (d dot up) / (zc * t)
        return Pair(((nx + 1) / 2 * viewportWidth).toFloat(), ((1 - ny) / 2 * viewportHeight).toFloat())
    }

    companion object {
        /** Isometric start: looking from +X -Y +Z towards the target, Z up on screen. */
        fun defaultRotation(): Quat {
            val back = Vec3(1.0, -1.0, 1.0).normalized()
            val right = (Vec3(0.0, 0.0, 1.0) cross back).normalized()
            val up = back cross right
            return Quat.fromAxes(right, up, back)
        }
    }
}
