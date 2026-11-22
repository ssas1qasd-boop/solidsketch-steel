package com.solidsketchapp.core.io

import com.solidsketchapp.core.api.BodyId
import com.solidsketchapp.core.api.Mesh
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

/**
 * Both ways with web v69 (see tools/golden/README.md, "ss interop"):
 * 1. this test writes native files to core-io/build/ss-interop/native/;
 * 2. `node tools/golden/ss_interop.js` opens them in v69 and checks them, and saves R10 from v69;
 * 3. this test then checks the file v69 saved (skipped until step 2 has run).
 */
class SsInteropTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
    private val r10 = File(root, "tests/steel/models/Frame_28x53m_R10.ss")
    private val dir = File(root, "core-io/build/ss-interop")

    /** Closed box (outward, CCW) as the web stores shapes: vp + tv. */
    private fun box(x: Float, y: Float, z: Float, s: Float) = Mesh(
        floatArrayOf(x, y, z, x + s, y, z, x + s, y + s, z, x, y + s, z, x, y, z + s, x + s, y, z + s, x + s, y + s, z + s, x, y + s, z + s),
        intArrayOf(0, 2, 1, 0, 3, 2, 4, 5, 6, 4, 6, 7, 0, 1, 5, 0, 5, 4, 1, 2, 6, 1, 6, 5, 2, 3, 7, 2, 7, 6, 3, 0, 4, 3, 4, 7),
    )

    @Test
    fun writeNativeFiles() {
        val out = File(dir, "native").apply { mkdirs() }
        // 1. R10 saved again unchanged.
        val doc = SsCodec.read(r10.inputStream())
        File(out, "R10_resaved.ss").outputStream().use { SsCodec.write(doc, it) }
        // 2. R10 with body 1 ("Web 1") deleted and a new box added in this app.
        doc.removeBody(BodyId("1"))
        val added = doc.addBody("""{"name":"NATIVE BOX","color":12597547,"hidden":false}""", box(0f, 0f, 0f, 500f))
        assertEquals(BodyId("8297"), added)
        File(out, "R10_edited.ss").outputStream().use { SsCodec.write(doc, it) }
        // 3. A document made only in this app (no header from a web file).
        val fresh = MemoryDocument()
        fresh.addBody("""{"name":"COLUMN","color":12597547,"hidden":false}""", box(0f, 0f, 0f, 300f))
        fresh.addBody("""{"name":"BEAM","color":3050327,"hidden":false}""", box(400f, 0f, 0f, 200f))
        File(out, "native_new.ss").outputStream().use { SsCodec.write(fresh, it) }
    }

    @Test
    fun readFileSavedByWeb() {
        val web = File(dir, "web/R10_web.ss")
        assumeTrue("run node tools/golden/ss_interop.js first", web.exists())
        val a = SsCodec.read(r10.inputStream())
        val b = SsCodec.read(web.inputStream())
        assertEquals(a.bodyIds(), b.bodyIds())
        // Every body field and every doc field has the same JSON value after a trip through the web build.
        // (Text can differ: R10 was written by Python, e.g. 1e-06, and v69 writes 0.000001.)
        var textDiffs = 0
        for (id in a.bodyIds()) {
            val ja = a.bodyJson(id)!!; val jb = b.bodyJson(id)!!
            if (ja != jb) { textDiffs++; assertEquals("body $id", Json.parse(ja), Json.parse(jb)) }
        }
        assertEquals(a.fieldNames(), b.fieldNames())
        for (k in a.fieldNames()) if (a.field(k) != b.field(k)) { textDiffs++; assertEquals("doc.$k", Json.parse(a.field(k)!!), Json.parse(b.field(k)!!)) }
        println("web-saved R10: %d values differ only in number text".format(java.util.Locale.ROOT, textDiffs))
        // Native reads v69's file exactly: read -> write gives v69's own bytes back (number text included).
        val webText = java.util.zip.GZIPInputStream(web.inputStream()).readBytes().toString(Charsets.UTF_8)
        val sw = java.io.StringWriter()
        SsCodec.writeJson(b, sw, Regex("\"savedAt\":\"([^\"]*)\"").find(webText)!!.groupValues[1])
        assertEquals("native read -> write of the v69 file", webText, sw.toString())

        // Geometry against the original: v69 runs clean() on every body it loads ("seam scraps removed"),
        // which rebuilds many bolt solids and re-triangulates some members. Plates must be identical,
        // members within 1e-4 of their volume; bolts are reported only (they are not in the weights).
        val worst = HashMap<String, Double>(); val changed = HashMap<String, Int>()
        for (id in a.bodyIds()) {
            val ma = a.bodyMesh(id)!!; val mb = b.bodyMesh(id)!!
            if (ma.positions.contentEquals(mb.positions) && ma.indices.contentEquals(mb.indices)) continue
            val kind = Regex("\"kind\":\"([a-z]+)\"").find(a.bodyJson(id)!!)?.groupValues?.get(1) ?: "?"
            val va = vol(ma); val vb = vol(mb)
            changed.merge(kind, 1, Int::plus); worst.merge(kind, abs(va - vb) / maxOf(1.0, abs(va)), ::maxOf)
        }
        println("v69 clean() on load changed: %s; worst relative volume change: %s".format(java.util.Locale.ROOT, changed, worst))
        assertEquals("plates must come back unchanged", null, changed["plate"])
        assertTrue("member volume changed by ${worst["member"]}", (worst["member"] ?: 0.0) < 1e-4)
    }

    @Test
    fun reopenAppFileAfterV69AddedABody() {
        val web = File(dir, "web/native_new_web_added.ss")
        assumeTrue("run node tools/golden/ss_interop.js first", web.exists())
        val doc = SsCodec.read(web.inputStream())   // no error
        val ids = doc.bodyIds()
        assertEquals(listOf("1", "2", "3"), ids.map { it.value })
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all { doc.bodyMesh(it) != null && doc.bodyMesh(it)!!.triangleCount > 0 })
        assertTrue(doc.bodyJson(BodyId("1"))!!.contains("\"name\":\"COLUMN\""))
        assertTrue(doc.bodyJson(BodyId("2"))!!.contains("\"name\":\"BEAM\""))
        assertTrue(doc.bodyJson(BodyId("3"))!!.contains("\"kind\":\"member\""))   // the v69 column
        assertEquals(MemoryDocument.V69_DEFAULTS.map { it.first }, doc.fieldNames())
        assertTrue(doc.field("folders")!!.contains("stColumn"))                  // v69 added its folder
        // A body added in the app after that gets a new, unique id.
        assertEquals(BodyId("4"), doc.addBody("{}", null))
        // And the app writes v69's file back byte for byte.
        val webText = java.util.zip.GZIPInputStream(web.inputStream()).readBytes().toString(Charsets.UTF_8)
        val again = SsCodec.read(web.inputStream())
        val sw = java.io.StringWriter()
        SsCodec.writeJson(again, sw, Regex("\"savedAt\":\"([^\"]*)\"").find(webText)!!.groupValues[1])
        assertEquals(webText, sw.toString())
    }

    private fun vol(m: Mesh): Double {
        val p = m.positions; val ix = m.indices; var v = 0.0
        for (t in 0 until m.triangleCount) {
            val a = 3 * ix[3 * t]; val b = 3 * ix[3 * t + 1]; val c = 3 * ix[3 * t + 2]
            v += p[a].toDouble() * (p[b + 1].toDouble() * p[c + 2] - p[b + 2].toDouble() * p[c + 1]) -
                p[a + 1].toDouble() * (p[b].toDouble() * p[c + 2] - p[b + 2].toDouble() * p[c]) +
                p[a + 2].toDouble() * (p[b].toDouble() * p[c + 1] - p[b + 1].toDouble() * p[c])
        }
        return v / 6
    }

    /** Minimal JSON value parser for comparing values (numbers as Double, objects as ordered maps). */
    private class Json(private val s: String) {
        private var i = 0
        companion object { fun parse(s: String): Any? = Json(s).value() }
        private fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }
        fun value(): Any? {
            ws()
            return when (s[i]) {
                '{' -> { i++; val m = LinkedHashMap<String, Any?>(); ws(); if (s[i] == '}') { i++; return m }
                    while (true) { val k = value() as String; ws(); i++; m[k] = value(); ws(); if (s[i++] == '}') return m } }
                '[' -> { i++; val l = ArrayList<Any?>(); ws(); if (s[i] == ']') { i++; return l }
                    while (true) { l += value(); ws(); if (s[i++] == ']') return l } }
                '"' -> { i++; val b = StringBuilder()
                    while (s[i] != '"') { if (s[i] == '\\') { i++; b.append(when (s[i]) { 'n' -> '\n'; 't' -> '\t'; 'u' -> { val c = s.substring(i + 1, i + 5).toInt(16).toChar(); i += 4; c }; else -> s[i] }) } else b.append(s[i]); i++ }
                    i++; b.toString() }
                't' -> { i += 4; true }
                'f' -> { i += 5; false }
                'n' -> { i += 4; null }
                else -> { val st = i; while (i < s.length && s[i] in "+-0123456789.eE") i++; s.substring(st, i).toDouble() }
            }
        }
    }
}
