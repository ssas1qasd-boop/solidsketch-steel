package com.solidsketchapp.core.view.gl

import com.solidsketchapp.core.api.BodyId
import com.solidsketchapp.core.view.boxMesh
import org.junit.Assert.assertEquals
import org.junit.Test

class BatchBuilderTest {
    @Test
    fun packsVerticesColoursAndOffsetIndices() {
        val a = BatchBuilder.Item(BodyId("a"), boxMesh(0f, 0f, 0f, 1f, 1f, 1f), 0xC0392B)
        val b = BatchBuilder.Item(BodyId("b"), boxMesh(5f, 0f, 0f, 6f, 1f, 1f), 0x2E8B57)
        val c = BatchBuilder.build(listOf(a, b))
        assertEquals(16, c.vertexCount); assertEquals(72, c.indexCount)
        assertEquals(5f, c.vertices.getFloat(8 * 16), 0f)                       // first vertex of b
        assertEquals(0x2E.toByte(), c.vertices.get(8 * 16 + 12))                 // its red byte
        assertEquals(-1, c.vertices.get(8 * 16 + 15).toInt())                   // alpha 255
        assertEquals(8 + 0, c.indices.getInt(36 * 4))                           // b's first index is offset by a's 8 vertices
        assertEquals(36, c.ranges[BodyId("b")]!!.firstIndex); assertEquals(36, c.ranges[BodyId("b")]!!.count)
    }

    @Test
    fun groupsByVertexBudget() {
        val items = (0 until 10).map { BatchBuilder.Item(BodyId("$it"), boxMesh(0f, 0f, 0f, 1f, 1f, 1f), 0) }
        val g = BatchBuilder.group(items, maxVertices = 24)   // 3 boxes of 8 vertices each
        assertEquals(listOf(3, 3, 3, 1), g.map { it.size })
        assertEquals(listOf(1), BatchBuilder.group(items.take(1), maxVertices = 4).map { it.size })   // too big alone: own chunk
    }
}
