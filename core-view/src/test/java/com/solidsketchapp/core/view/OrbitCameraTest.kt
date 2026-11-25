package com.solidsketchapp.core.view

import com.solidsketchapp.core.api.Vec3
import com.solidsketchapp.core.view.math.dot
import com.solidsketchapp.core.view.math.minus
import com.solidsketchapp.core.view.math.plus
import com.solidsketchapp.core.view.math.times
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class OrbitCameraTest {
    private fun cam() = OrbitCamera().apply { viewportWidth = 1080; viewportHeight = 2000; setView(Vec3(1000.0, 2000.0, 500.0), 8000.0, rotation) }
    /** Pixels for one full turn (orbit uses PI / min(w, h) * 1.5 rad per px). */
    private fun fullTurnPx(c: OrbitCamera) = (2 * Math.PI / (Math.PI / minOf(c.viewportWidth, c.viewportHeight) * 1.5)).toFloat()
    private fun assertVec(e: Vec3, a: Vec3, tol: Double) {
        assertEquals(e.x, a.x, tol); assertEquals(e.y, a.y, tol); assertEquals(e.z, a.z, tol)
    }

    @Test
    fun tenFullTurnsEachWayReturnToStart() {
        val c = cam(); val eye0 = c.eye; val up0 = c.up
        val turn = fullTurnPx(c)
        repeat(10) { repeat(20) { c.orbit(turn / 20, 0f) } }   // 10 turns horizontally
        repeat(10) { repeat(20) { c.orbit(0f, turn / 20) } }   // 10 turns vertically, over the poles
        assertVec(eye0, c.eye, 1e-3)
        assertVec(up0, c.up, 1e-6)
    }

    @Test
    fun verticalOrbitIsNotClamped() {
        val c = cam(); val up0 = c.up
        c.orbit(0f, fullTurnPx(c) / 2)   // half a turn: the view is upside down
        assertTrue("up should be flipped, got ${c.up}", (c.up dot up0) < -0.999)
    }

    @Test
    fun centreRayHitsTargetAndProjectionRoundTrips() {
        val c = cam()
        val r = c.screenRay(540f, 1000f)
        val toTarget = c.target - r.origin
        val t = toTarget dot r.direction
        assertVec(c.target, r.origin + r.direction * t, 1e-6)
        val (sx, sy) = c.worldToScreen(c.target)!!
        assertEquals(540f, sx, 1e-3f); assertEquals(1000f, sy, 1e-3f)
        for ((x, y) in listOf(10f to 20f, 1000f to 1900f, 300f to 1500f)) {
            val ray = c.screenRay(x, y)
            val p = c.worldToScreen(ray.origin + ray.direction * 5000.0)!!
            assertEquals(x, p.first, 1e-2f); assertEquals(y, p.second, 1e-2f)
        }
        assertNull(c.worldToScreen(c.eye + c.back * 10.0))
    }

    @Test
    fun fitShowsWholeBox() {
        val c = cam()
        val min = Vec3(0.0, 0.0, 0.0); val max = Vec3(53000.0, 28000.0, 9000.0)
        c.fit(min, max)
        for (x in listOf(min.x, max.x)) for (y in listOf(min.y, max.y)) for (z in listOf(min.z, max.z)) {
            val p = c.worldToScreen(Vec3(x, y, z))
            assertNotNull(p)
            assertTrue("corner off screen: $p", p!!.first in 0f..1080f && p.second in 0f..2000f)
        }
        assertTrue(c.near() > 0 && c.far() > c.distance)
    }

    @Test
    fun panMovesTargetInScreenPlane() {
        val c = cam(); val t0 = c.target
        c.pan(100f, -50f)
        val d = c.target - t0
        assertTrue(abs(d dot c.back) < 1e-6)
        assertTrue((d dot c.right) < 0)   // dragging right moves the target left
    }

    @Test
    fun orthographicKeepsScaleAndHasParallelRays() {
        val c = cam(); c.orthographic = true
        val t = c.target; val o = c.worldToScreen(t)!!
        // a point 1000 mm nearer the eye lands on the same pixel (parallel projection)
        val near = c.worldToScreen(t + c.back * 1000.0)!!
        assertEquals(o.first, near.first, 0.01f); assertEquals(o.second, near.second, 0.01f)
        // 100 mm to the right: pxPerUnit pixels, the same as the perspective scale at the target
        val r = c.worldToScreen(t + c.right * 100.0)!!; val k = c.pxPerUnit(t)!!
        assertEquals(100 * k, (r.first - o.first).toDouble(), 0.01)
        c.orthographic = false; assertEquals(k, c.pxPerUnit(t)!!, 1e-9)
        c.orthographic = true
        val ray = c.screenRay(r.first, r.second); assertTrue(abs((ray.direction dot c.back) + 1) < 1e-9)
        val back = ray.origin - (t + c.right * 100.0); assertTrue("ray through the point", (back - c.back * (back dot c.back)).let { abs(it dot it) } < 1e-6)
    }
}
