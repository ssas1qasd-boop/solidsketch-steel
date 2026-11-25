package com.solidsketchapp.steel

import com.solidsketchapp.core.api.Mesh
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.zip.GZIPInputStream
import kotlin.math.abs

/**
 * C04 follow-up (owner 2026-11-25): the purlin BODY, not only the connection parts. Every R11 (= R10) C / Z purlin (220) is built by
 * the app from its R11 input (p1, p2, rot, rAng, plane, depth, offsets) and compared with R11's own Tekla mesh (st.shape):
 * the side the web is on (the lips are the vertices 5…30 mm inside the section's depth ends, at the other flange edge), the
 * web's outer face along R11's st.v (0.1 mm) and the box (0.1 mm). The web build gives the same (tools/golden/r10_purlin_web.js,
 * tests/steel/golden/r10/purlin_web.json: 220 / 220, web faces within 0.003 mm). Needs Manifold: -PhostJni.
 */
class R11PurlinBodyTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }

    private class Side(val web: Int, val face: Double?)
    private fun side(pts: List<DoubleArray>, st: JsonObject): Side {
        fun v3(k: String) = st.getValue(k).jsonArray.map { it.jsonPrimitive.double }
        val o = v3("p1"); val u = v3("u"); val v = v3("v")
        val dv = pts.map { p -> (0..2).sumOf { (p[it] - o[it]) * v[it] } }; val du = pts.map { p -> (0..2).sumOf { (p[it] - o[it]) * u[it] } }
        val lo = dv.min(); val hi = dv.max(); val h = du.max() - du.min()
        val lips = pts.indices.filter { abs(du[it]) > h / 2 - 30 && abs(du[it]) < h / 2 - 5 }.map { dv[it] }
        return when { lips.isNotEmpty() && lips.all { it > (lo + hi) / 2 } -> Side(-1, lo); lips.isNotEmpty() && lips.all { it < (lo + hi) / 2 } -> Side(1, hi); else -> Side(0, null) }
    }
    private fun pts(m: Mesh) = (0 until m.vertexCount).map { i -> DoubleArray(3) { m.positions[i * 3 + it].toDouble() } }

    @Test fun purlinBodiesEqualR11() {
        assumeTrue("host JNI not built (use -PhostJni)", System.getProperty("sscore.hostJni") == "1")
        val doc = Json.parseToJsonElement(GZIPInputStream(File(root, "tests/steel/models/Frame_28x53m_R11.ss").inputStream()).readBytes().decodeToString()).jsonObject.getValue("doc").jsonObject
        val purl = doc.getValue("bodies").jsonArray.map { it.jsonObject }.filter { b -> val st = b["st"] as? JsonObject
            st?.get("kind")?.jsonPrimitive?.content == "member" && Regex("^(C|CC|Z|ZZ)\\d").containsMatchIn(st["prof"]?.jsonPrimitive?.content ?: "") }
        val t = AppGoldenTest(); val m = t.build(JsonObject(emptyMap()))
        val keep = setOf("kind", "name", "prof", "mat", "cls", "p1", "p2", "rot", "rAng", "plane", "depth", "pOff", "dOff", "dx1", "dx2")
        val bad = ArrayList<String>(); var ok = 0
        for (b in purl) {
            val st = b.getValue("st").jsonObject; val id = b["id"]!!.jsonPrimitive.content
            val vp = b.getValue("shape").jsonObject.getValue("vp").jsonArray.map { it.jsonPrimitive.double }; val r = (vp.indices step 3).map { doubleArrayOf(vp[it], vp[it + 1], vp[it + 2]) }
            val mesh = m.parts.mesh(JsonObject(st.filterKeys { it in keep }))
            val a = pts(mesh); val sr = side(r, st); val sa = side(a, st)
            val br = (0..2).map { k -> r.minOf { it[k] } } + (0..2).map { k -> r.maxOf { it[k] } }; val ba = (0..2).map { k -> a.minOf { it[k] } } + (0..2).map { k -> a.maxOf { it[k] } }
            val e = mutableListOf<String>()
            if (sr.web == 0 || sr.web != sa.web) e += "web side app ${sa.web} vs R11 ${sr.web}"
            if (sr.face != null && sa.face != null && abs(sr.face - sa.face) > 0.1) e += "web face ${"%.2f".format(sa.face)} vs ${"%.2f".format(sr.face)}"
            if ((0..5).any { abs(br[it] - ba[it]) > 0.1 }) e += "box ${ba.map { Math.round(it * 10) / 10.0 }} vs ${br.map { Math.round(it * 10) / 10.0 }}"
            if (e.isEmpty()) ok++ else bad += "purlin $id ${st["prof"]} rAng ${st["rAng"]}: " + e.joinToString("; ")
        }
        val rep = "R11 C / Z purlin bodies, app vs R11 (web side, web face 0.1 mm, box 0.1 mm): $ok / ${purl.size}\n" + bad.take(50).joinToString("\n")
        File(root, "build").mkdirs(); File(root, "build/r11-purlin-bodies.txt").writeText(rep + "\n"); println(rep.lines().take(10).joinToString("\n"))
        assertTrue(rep, bad.isEmpty() && purl.size == 220)
    }
}
