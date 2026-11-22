package com.solidsketchapp.core.view.gl

import com.solidsketchapp.core.api.BodyId
import com.solidsketchapp.core.api.Mesh
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Packs many bodies into a few GPU-ready chunks (no GL calls here, so it is unit-tested).
 * Vertex layout: x, y, z (float32) + r, g, b, a (unsigned bytes) = 16 bytes. Indices are
 * uint32 with each body's base vertex added. [Chunk.ranges] gives each body's index range,
 * so one body can be drawn again (selection highlight) from the same buffers.
 */
object BatchBuilder {
    const val STRIDE = 16
    /** Vertices per chunk: small enough that rebuilding one chunk after an edit is quick. */
    const val CHUNK_VERTICES = 196_608

    class Item(val id: BodyId, val mesh: Mesh, val rgb: Int)
    class Range(val firstIndex: Int, val count: Int)
    class Chunk(val ids: List<BodyId>, val vertices: ByteBuffer, val indices: ByteBuffer, val vertexCount: Int, val indexCount: Int, val ranges: Map<BodyId, Range>)

    /** Splits [items] (in order) into groups that each fit one chunk. A huge body gets a chunk of its own. */
    fun group(items: List<Item>, maxVertices: Int = CHUNK_VERTICES): List<List<Item>> {
        val out = ArrayList<List<Item>>()
        var cur = ArrayList<Item>(); var n = 0
        for (it in items) {
            val v = it.mesh.vertexCount
            if (cur.isNotEmpty() && n + v > maxVertices) { out += cur; cur = ArrayList(); n = 0 }
            cur += it; n += v
        }
        if (cur.isNotEmpty()) out += cur
        return out
    }

    fun build(items: List<Item>): Chunk {
        val vc = items.sumOf { it.mesh.vertexCount }
        val ic = items.sumOf { it.mesh.indices.size }
        val vb = ByteBuffer.allocateDirect(vc * STRIDE).order(ByteOrder.nativeOrder())
        val ib = ByteBuffer.allocateDirect(ic * 4).order(ByteOrder.nativeOrder())
        val ranges = HashMap<BodyId, Range>(items.size * 2)
        var base = 0; var first = 0
        for (it in items) {
            val p = it.mesh.positions
            val r = ((it.rgb shr 16) and 255).toByte(); val g = ((it.rgb shr 8) and 255).toByte(); val b = (it.rgb and 255).toByte()
            for (v in 0 until it.mesh.vertexCount) {
                vb.putFloat(p[3 * v]); vb.putFloat(p[3 * v + 1]); vb.putFloat(p[3 * v + 2])
                vb.put(r); vb.put(g); vb.put(b); vb.put(-1)
            }
            val idx = it.mesh.indices
            for (k in idx.indices) ib.putInt(idx[k] + base)
            ranges[it.id] = Range(first, idx.size)
            base += it.mesh.vertexCount; first += idx.size
        }
        vb.flip(); ib.flip()
        return Chunk(items.map { it.id }, vb, ib, vc, ic, ranges)
    }
}
