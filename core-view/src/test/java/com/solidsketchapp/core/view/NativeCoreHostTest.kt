package com.solidsketchapp.core.view

import com.solidsketchapp.core.api.Vec3
import com.solidsketchapp.core.view.math.Quat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

/** Runs the real JNI glue + Manifold on the host. Only with -PhostJni (skipped otherwise). */
class NativeCoreHostTest {
    @Before
    fun needsHostLib() = assumeTrue("host JNI not built (use -PhostJni)", System.getProperty("sscore.hostJni") == "1")

    private val engine by lazy { ManifoldBooleanEngine() }
    private fun circle(r: Double, n: Int) = 0.5 * n * r * r * sin(2 * PI / n)

    @Test
    fun plateWithHolesThroughJni() {
        val plate = NativeCore.box(100f, 100f, 10f)
        val holes = listOf(25f to 25f, 75f to 25f, 25f to 75f, 75f to 75f).map { (x, y) ->
            MeshOps.translate(NativeCore.cylinder(11f, 30f, 32), Vec3(x.toDouble(), y.toDouble(), -10.0))
        }
        val r = engine.subtract(plate, holes)
        assertEquals(100.0 * 100 * 10 - 4 * circle(11.0, 32) * 10, engine.volume(r), 1e-2)
        assertTrue(engine.isClosedSolid(r))
        assertEquals(engine.volume(r), MeshOps.volume(r), 1e-6)   // Kotlin and C++ volume agree
    }

    @Test
    fun unionIntersectAndRotatedCutter() {
        val a = NativeCore.box(10f, 10f, 10f)
        val b = MeshOps.translate(NativeCore.box(10f, 10f, 10f), Vec3(5.0, 0.0, 0.0))
        assertEquals(1500.0, engine.volume(engine.union(listOf(a, b))), 1e-3)
        assertEquals(500.0, engine.volume(engine.intersect(a, b)), 1e-3)
        // A cylinder turned from Z to X with MeshOps, as Steel does for bolt holes.
        val q = Quat.fromAxes(Vec3(0.0, 0.0, -1.0), Vec3(0.0, 1.0, 0.0), Vec3(1.0, 0.0, 0.0))
        val hole = MeshOps.transform(NativeCore.cylinder(2f, 20f, 24), q, Vec3(-5.0, 5.0, 5.0))
        assertEquals(1000.0 - circle(2.0, 24) * 10, engine.volume(engine.subtract(a, listOf(hole))), 1e-3)
    }

    @Test
    fun badInputThrowsAndOpenMeshIsNotSolid() {
        val a = NativeCore.box(10f, 10f, 10f)
        val open = com.solidsketchapp.core.api.Mesh(a.positions, a.indices.copyOf(a.indices.size - 3))
        assertFalse(engine.isClosedSolid(open))
        val e = runCatching { engine.subtract(open, listOf(a)) }.exceptionOrNull()
        assertTrue("expected IllegalStateException, got $e", e is IllegalStateException && e.message!!.contains("NOT_CLOSED"))
        assertTrue(NativeCore.version().contains("Manifold 3.5.4"))
    }
}
