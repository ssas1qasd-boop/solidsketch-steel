package com.solidsketchapp.core.view.gl

import com.solidsketchapp.core.api.Mesh
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** S5: v69 feature edges (12°): a box shows its 12 edges; a smooth 48-sided cylinder only its two rims. */
class FeatureEdgesTest {
    /** Flat-shaded box (each face has its own 4 vertices, like the meshes v69 and the .ss files carry). */
    private fun box(): Mesh {
        val c = listOf(floatArrayOf(0f, 0f, 0f), floatArrayOf(1f, 0f, 0f), floatArrayOf(1f, 1f, 0f), floatArrayOf(0f, 1f, 0f), floatArrayOf(0f, 0f, 1f), floatArrayOf(1f, 0f, 1f), floatArrayOf(1f, 1f, 1f), floatArrayOf(0f, 1f, 1f))
        val faces = listOf(intArrayOf(0, 3, 2, 1), intArrayOf(4, 5, 6, 7), intArrayOf(0, 1, 5, 4), intArrayOf(1, 2, 6, 5), intArrayOf(2, 3, 7, 6), intArrayOf(3, 0, 4, 7))
        val pos = ArrayList<Float>(); val idx = ArrayList<Int>()
        for (f in faces) { val b = pos.size / 3; for (i in f) pos.addAll(c[i].toList()); idx.addAll(listOf(b, b + 1, b + 2, b, b + 2, b + 3)) }
        return Mesh(pos.toFloatArray(), idx.toIntArray())
    }

    private fun cylinder(n: Int): Mesh {
        val pos = ArrayList<Float>(); val idx = ArrayList<Int>()
        for (i in 0 until n) { val a = 2 * PI * i / n; pos += listOf(cos(a).toFloat(), sin(a).toFloat(), 0f, cos(a).toFloat(), sin(a).toFloat(), 1f) }
        for (i in 0 until n) { val j = (i + 1) % n; idx += listOf(2 * i, 2 * j, 2 * j + 1, 2 * i, 2 * j + 1, 2 * i + 1) }
        val c0 = pos.size / 3; pos += listOf(0f, 0f, 0f); val c1 = pos.size / 3; pos += listOf(0f, 0f, 1f)
        for (i in 0 until n) { val j = (i + 1) % n; idx += listOf(c0, 2 * j, 2 * i, c1, 2 * i + 1, 2 * j + 1) }
        return Mesh(pos.toFloatArray(), idx.toIntArray())
    }

    @Test fun boxHasTwelveEdges() = assertEquals(12, FeatureEdges.of(box()).size / 6)
    @Test fun smoothCylinderShowsOnlyItsRims() = assertEquals(2 * 48, FeatureEdges.of(cylinder(48)).size / 6)   // 7.5° between sides < 12°
    @Test fun coarsePrismShowsItsSides() = assertEquals(3 * 6, FeatureEdges.of(cylinder(6)).size / 6)          // 60° between sides
}
