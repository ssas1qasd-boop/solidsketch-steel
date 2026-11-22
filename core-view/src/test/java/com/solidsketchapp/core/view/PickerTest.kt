package com.solidsketchapp.core.view

import com.solidsketchapp.core.api.BodyId
import com.solidsketchapp.core.api.Ray
import com.solidsketchapp.core.api.SelectionApi
import com.solidsketchapp.core.api.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PickerTest {
    private fun body(id: String, x0: Float) = boxMesh(x0, -1f, -1f, x0 + 2f, 1f, 1f).let { PickBody(BodyId(id), it, Aabb.of(it)!!) }

    @Test
    fun nearestBodyWins() {
        val ray = Ray(Vec3(-10.0, 0.0, 0.0), Vec3(1.0, 0.0, 0.0))
        val far = body("far", 10f); val near = body("near", 0f)
        val hit = Picker.pick(ray, listOf(far, near)) as SelectionApi.Hit.Face
        assertEquals(BodyId("near"), hit.body)
        assertEquals(0.0, hit.point.x, 1e-9)
        assertEquals(-1.0, hit.normal.x, 1e-9)   // the face looking at the ray
    }

    @Test
    fun missAndBehind() {
        assertNull(Picker.pick(Ray(Vec3(-10.0, 5.0, 0.0), Vec3(1.0, 0.0, 0.0)), listOf(body("a", 0f))))
        assertNull(Picker.pick(Ray(Vec3(10.0, 0.0, 0.0), Vec3(1.0, 0.0, 0.0)), listOf(body("a", 0f))))
    }

    @Test
    fun aabbSlab() {
        val b = Aabb(Vec3(0.0, 0.0, 0.0), Vec3(1.0, 1.0, 1.0))
        assertEquals(1.0, b.hit(Ray(Vec3(-1.0, 0.5, 0.5), Vec3(1.0, 0.0, 0.0)))!!, 1e-12)
        assertNull(b.hit(Ray(Vec3(-1.0, 2.0, 0.5), Vec3(1.0, 0.0, 0.0))))
    }
}
