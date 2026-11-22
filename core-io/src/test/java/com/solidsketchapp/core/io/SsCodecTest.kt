package com.solidsketchapp.core.io

import com.solidsketchapp.core.api.BodyId
import com.solidsketchapp.core.api.Mesh
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.StringWriter
import java.util.zip.GZIPInputStream

class SsCodecTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
    private val r10 = File(root, "tests/steel/models/Frame_28x53m_R10.ss")

    private fun savedAtOf(json: String) = Regex("\"savedAt\":\"([^\"]*)\"").find(json)!!.groupValues[1]

    @Test
    fun r10LoadsAndSavesByteIdentical() {
        val original = GZIPInputStream(r10.inputStream()).readBytes().toString(Charsets.UTF_8)
        val t0 = System.nanoTime()
        val doc = SsCodec.read(r10.inputStream())
        val loadMs = (System.nanoTime() - t0) / 1e6
        assertEquals(6638, doc.bodyIds().size)
        assertEquals(1825326, doc.bodyIds().sumOf { doc.bodyMesh(it)!!.triangleCount })
        assertEquals(8297L, doc.nextBodyId)
        assertEquals("\"Web 1\"", Regex("\"name\":(\"[^\"]*\")").find(doc.bodyJson(BodyId("1"))!!)!!.groupValues[1])
        assertTrue(doc.field("stConns")!!.startsWith("[{\"id\":1,\"type\":\"TKCUT\""))

        val sw = StringWriter()
        val t1 = System.nanoTime()
        SsCodec.writeJson(doc, sw, savedAtOf(original))
        val saveMs = (System.nanoTime() - t1) / 1e6
        val again = sw.toString()
        if (again != original) {
            val i = again.indices.firstOrNull { it >= original.length || again[it] != original[it] } ?: again.length
            fail("first difference at $i:\n orig …${original.substring(maxOf(0, i - 80), minOf(original.length, i + 80))}\n ours …${again.substring(maxOf(0, i - 80), minOf(again.length, i + 80))}")
        }
        println("R10 load %.0f ms, save %.0f ms".format(java.util.Locale.ROOT, loadMs, saveMs))
    }

    @Test
    fun unknownFieldsAtEveryLevelRoundTrip() {
        val json = """{"format":"solidsketch","zzTop":{"a":[1,2.5,"x\"y"]},"version":1,"app":"SolidSketch Web","unit":"mm","name":"t","rev":3,"savedAt":"2026-01-01T00:00:00.000Z","doc":{"v":1,"future":[true,null],"nextId":3,"bodies":[{"id":1,"name":"P 1","color":1483448,"hidden":false,"newBodyField":{"k":"ü€"},"st":{"kind":"plate","unknownSt":42},"shape":{"vp":[0,0,0,1,0,0,0,1,0,0.10000000149011612,-2.5,1e-7],"tv":[0,1,2,0,2,3]}},{"id":2,"name":"no shape","color":0,"hidden":true}],"stConns":[],"after":"bodies"}}"""
        val doc = SsCodec.read(ByteArrayInputStream(json.toByteArray()))   // plain JSON, not gzipped
        val m = doc.bodyMesh(BodyId("1"))!!
        assertArrayEquals(floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f, 0.1f, -2.5f, 1e-7f), m.positions, 0f)
        assertEquals("""{"id":1,"name":"P 1","color":1483448,"hidden":false,"newBodyField":{"k":"ü€"},"st":{"kind":"plate","unknownSt":42}}""", doc.bodyJson(BodyId("1")))
        val sw = StringWriter(); SsCodec.writeJson(doc, sw, "2026-01-01T00:00:00.000Z")
        // 1e-7 as float32 is 1.0000000116860974e-7 in JavaScript: the only expected change.
        assertEquals(json.replace(",1e-7]", ",1.0000000116860974e-7]"), sw.toString())
    }

    @Test
    fun newDocumentWritesEveryV69DocFieldWithDefaults() {
        val doc = MemoryDocument()
        val tri = Mesh(floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f), intArrayOf(0, 1, 2))
        doc.addBody("""{"name":"COLUMN","color":12597547,"hidden":false}""", tri)
        val bytes = ByteArrayOutputStream().also { SsCodec.write(doc, it, "2026-11-22T00:00:00.000Z", name = "Frame A") }.toByteArray()
        assertEquals(0x1f, bytes[0].toInt() and 0xff)
        val text = GZIPInputStream(ByteArrayInputStream(bytes)).readBytes().toString(Charsets.UTF_8)
        // Same doc fields, order and defaults as a new v69 document (docObject + kept); nextId/colorIdx advanced by the body.
        assertEquals(
            """{"format":"solidsketch","version":1,"app":"SolidSketch Steel Android","unit":"mm","name":"Frame A","rev":0,"savedAt":"2026-11-22T00:00:00.000Z",""" +
                """"doc":{"v":1,"nextId":2,"colorIdx":1,"sketchLines":[],"sketchPlane":null,"paths":[],"nextPathId":1,"profiles":[],"nextProfileId":1,""" +
                """"bodies":[{"id":1,"name":"COLUMN","color":12597547,"hidden":false,"shape":{"vp":[0,0,0,1,0,0,0,1,0],"tv":[0,1,2]}}],""" +
                """"folders":[],"notes28":[],"refModels28":[],"furn28":[],"stGrid":null,"stGen":null,"stConns":[],"stNextConn":1,"stNum":null,"stDwg":null,"curType":null,"curTag":"","unit":"mm","kept":[]}}""",
            text)
        assertEquals(24, MemoryDocument.V69_DEFAULTS.size + 1)   // + bodies = R10's 24 doc fields
        val back = SsCodec.read(ByteArrayInputStream(bytes))
        assertArrayEquals(tri.positions, back.bodyMesh(BodyId("1"))!!.positions, 0f)
        assertEquals(2L, back.nextBodyId)
    }

    @Test
    fun openedFileKeepsEveryDocFieldAndHeaderNameFollowsTheFile() {
        val doc = SsCodec.read(r10.inputStream())
        val r10Fields = listOf("v", "nextId", "colorIdx", "sketchLines", "sketchPlane", "paths", "nextPathId", "profiles", "nextProfileId",
            "folders", "notes28", "refModels28", "furn28", "stGrid", "stGen", "stConns", "stNextConn", "stNum", "stDwg", "curType", "curTag", "unit", "kept")
        assertEquals(r10Fields, doc.fieldNames()); assertEquals(9, doc.bodiesIndex)
        assertEquals(MemoryDocument.V69_DEFAULTS.map { it.first }, r10Fields)   // a new document has the same names
        val sw = StringWriter(); SsCodec.writeJson(doc, sw, "x", name = "R10 copy")
        assertTrue(sw.toString().startsWith("""{"format":"solidsketch","version":1,"app":"SolidSketch Web","unit":"mm","name":"R10 copy","rev":0,"savedAt":"x","doc":{"v":1,"nextId":8297,"colorIdx":8296,"""))
        // A body added in the app advances nextId and colorIdx like v69.
        doc.addBody("{}", null)
        val sw2 = StringWriter(); SsCodec.writeJson(doc, sw2, "x")
        assertTrue(sw2.toString().contains(""""doc":{"v":1,"nextId":8298,"colorIdx":8297,"""))
    }

    @Test
    fun saveNames() {
        assertEquals("Untitled.ss", SsNames.suggest(null, null))
        assertEquals("R10_try9.ss", SsNames.suggest(null, "R10_try9"))
        assertEquals("Unttitled.ss", SsNames.suggest("Unttitled.ss", "x"))   // the last name the owner typed is reused
        assertEquals("Frame.ss", SsNames.suggest("Frame", null))
        assertEquals("Frame", SsNames.base("Frame.SS")); assertEquals("Untitled", SsNames.base(" "))
    }

    @Test
    fun rejectsOtherFilesAndNewerVersions() {
        fun msg(s: String) = runCatching { SsCodec.read(ByteArrayInputStream(s.toByteArray())) }.exceptionOrNull()?.message
        assertEquals("This is not a SolidSketch file", msg("""{"format":"other","doc":{}}"""))
        assertEquals("This file was saved by a newer SolidSketch — update the app to open it", msg("""{"format":"solidsketch","version":2,"doc":{}}"""))
        assertTrue(msg("not json")!!.startsWith("This is not a SolidSketch file"))
    }

    @Test
    fun loadIsNotUndoableAndSnapshotsKeepFieldOrder() {
        val doc = SsCodec.read(r10.inputStream())
        val s = doc.snapshot()
        doc.removeBody(BodyId("1")); doc.setField("stGrid", "null")
        doc.restore(s)
        assertEquals(6638, doc.bodyIds().size)
        assertEquals(listOf("v", "nextId", "colorIdx"), doc.fieldNames().take(3))
        assertEquals("""{"x":"0 13195 13195 13195 13195","y":"0 5570 5570 5570 5570 5570","z":"0 7500 1500"}""", doc.field("stGrid"))
    }
}
