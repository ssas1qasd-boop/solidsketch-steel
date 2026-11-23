package com.solidsketchapp.steel

import com.solidsketchapp.core.io.SsCodec
import com.solidsketchapp.core.view.Aabb
import com.solidsketchapp.core.view.gl.BatchBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** R10 through the same path the app uses: SsCodec -> chunks the renderer uploads. */
class R10RenderDataTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }

    @Test
    fun r10PacksIntoChunks() {
        val doc = SsCodec.read(File(root, "tests/steel/models/Frame_28x53m_R10.ss").inputStream())
        val items = doc.bodyIds().map { BatchBuilder.Item(it, doc.bodyMesh(it)!!, 0x8E8E93) }
        val t0 = System.nanoTime()
        val chunks = BatchBuilder.group(items).map { BatchBuilder.build(it) }
        val ms = (System.nanoTime() - t0) / 1e6
        val verts = items.sumOf { it.mesh.vertexCount }
        assertEquals(verts, chunks.sumOf { it.vertexCount })
        assertEquals(1825326 * 3, chunks.sumOf { it.indexCount })
        assertEquals(6638, chunks.sumOf { it.ranges.size })
        assertTrue(chunks.all { it.vertexCount <= BatchBuilder.CHUNK_VERTICES || it.ids.size == 1 })
        // Every index stays inside its chunk.
        // S5: v69 feature edges for every R10 body (computed once per mesh when a chunk is first drawn).
        val te = System.nanoTime()
        val edged = BatchBuilder.group(items).map { BatchBuilder.build(it, com.solidsketchapp.core.view.gl.FeatureEdges::of) }
        val ems = (System.nanoTime() - te) / 1e6
        val segs = edged.sumOf { it.edgeVertexCount } / 2
        println("R10 feature edges: $segs segments in ${ems.toLong()} ms (JVM)")
        assertTrue(segs in 100_000..1_825_326)
        for (c in chunks) { val ib = c.indices.duplicate().order(java.nio.ByteOrder.nativeOrder()); while (ib.hasRemaining()) { val i = ib.getInt(); assertTrue(i in 0 until c.vertexCount) } }
        val box = items.mapNotNull { Aabb.of(it.mesh) }.reduce(Aabb::union)
        val gpuMb = chunks.sumOf { it.vertices.capacity().toLong() + it.indices.capacity() } / 1048576.0
        println("R10: %d vertices, %d chunks, %.1f MB GPU buffers, packed in %.0f ms, box %s..%s".format(java.util.Locale.ROOT, verts, chunks.size, gpuMb, ms, box.min, box.max))
    }
}
