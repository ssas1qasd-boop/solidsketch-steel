package com.solidsketchapp.steel.geom

import com.solidsketchapp.core.api.Mesh
import com.solidsketchapp.steel.data.SteelData
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.max

/**
 * S5: member and plate solids equal web v69 within 0.1 mm (golden: tests/steel/golden/s5/members.json, from the
 * live stBuild chain). Same vertex set both ways, same volume and bounding box; every mesh closed and outward.
 */
class StBuildTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
    private val data by lazy { SteelData.load { File(root, "steel/src/main/assets/steel/$it").readText() } }
    private val cases by lazy { Json.parseToJsonElement(File(root, "tests/steel/golden/s5/members.json").readText()).jsonObject.getValue("cases").jsonObject }

    private fun verts(m: Mesh): List<DoubleArray> {
        val seen = HashMap<String, DoubleArray>()
        for (i in 0 until m.vertexCount) { val v = doubleArrayOf(m.positions[i * 3].toDouble(), m.positions[i * 3 + 1].toDouble(), m.positions[i * 3 + 2].toDouble()); seen.putIfAbsent(v.joinToString { "%.3f".format(java.util.Locale.ROOT, it) }, v) }
        return seen.values.toList()
    }
    private fun near(a: DoubleArray, list: List<DoubleArray>) = list.minOf { b -> max(abs(a[0] - b[0]), max(abs(a[1] - b[1]), abs(a[2] - b[2]))) }

    /** Every edge used by exactly two triangles, in opposite directions: a closed, consistently oriented surface. */
    private fun closedAndOutward(m: Mesh): Boolean {
        val key = HashMap<String, Int>(); val pts = verts(m)
        fun id(i: Int): Int { val v = doubleArrayOf(m.positions[i * 3].toDouble(), m.positions[i * 3 + 1].toDouble(), m.positions[i * 3 + 2].toDouble()); return pts.indexOfFirst { abs(it[0] - v[0]) < 1e-3 && abs(it[1] - v[1]) < 1e-3 && abs(it[2] - v[2]) < 1e-3 } }
        val ids = IntArray(m.vertexCount) { id(it) }
        for (t in 0 until m.triangleCount) for (e in 0..2) {
            val a = ids[m.indices[t * 3 + e]]; val b = ids[m.indices[t * 3 + (e + 1) % 3]]
            key.merge("$a>$b", 1, Int::plus)
        }
        return key.all { (k, n) -> val (a, b) = k.split(">"); n == 1 && key["$b>$a"] == 1 } && Prism.signedVolume(m) > 0
    }

    @Test
    fun memberSolidsEqualV69() {
        val b = StBuild(data.profiles)
        assertEquals(40, cases.size)
        val bad = mutableListOf<String>()
        var worst = 0.0
        for ((name, c0) in cases) {
            val c = c0.jsonObject
            // The part as v69 built it: the property set from ST_SETS (app assets), then the case's overrides; plates drop p1/p2.
            val st = JsonObject(LinkedHashMap(data.propertySet(c.getValue("set").jsonPrimitive.content)!!.raw).apply { putAll(c.getValue("st").jsonObject); if (this["kind"]?.jsonPrimitive?.content == "plate") { remove("p1"); remove("p2") } })
            val m = b.build(st)
            val gv = c.getValue("verts").jsonArray.map { v -> v.jsonArray.map { it.jsonPrimitive.double }.toDoubleArray() }
            val ov = verts(m)
            val dGold = gv.maxOf { near(it, ov) }; val dOurs = ov.maxOf { near(it, gv) }
            worst = max(worst, max(dGold, dOurs))
            val vol = abs(Prism.signedVolume(m)); val gvol = c.getValue("volume").jsonPrimitive.double
            val bb = c.getValue("bbox").jsonArray.map { it.jsonPrimitive.double }
            val obb = (0..2).map { k -> ov.minOf { it[k] } } + (0..2).map { k -> ov.maxOf { it[k] } }
            val problems = buildList {
                if (ov.size != gv.size) add("${ov.size} vertices, v69 ${gv.size}")
                if (dGold > 0.1 || dOurs > 0.1) add("vertex distance ${"%.3f".format(java.util.Locale.ROOT, max(dGold, dOurs))} mm")
                // Positions are float32 (about 0.001 mm at 13 m from the origin), so volumes agree to about 1e-5 of the whole.
                if (abs(vol - gvol) > max(1.0, gvol * 1e-4)) add("volume $vol vs $gvol")
                if ((0..5).any { abs(obb[it] - bb[it]) > 0.1 }) add("bbox $obb vs $bb")
                if (!closedAndOutward(m)) add("not a closed outward solid")
            }
            if (problems.isNotEmpty()) bad += "$name: $problems"
        }
        println("member solids: ${cases.size - bad.size}/${cases.size} equal to v69, worst vertex distance ${"%.4f".format(java.util.Locale.ROOT, worst)} mm")
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
    }

    @Test
    fun columnsUseTheTeklaFrame() {
        // v69: a column's section "up" (eh) is −X before rotation; Front turns it 90° about the column axis.
        val b = StBuild(data.profiles)
        val st = Json.parseToJsonElement("""{"kind":"member","prof":"HEA300","p1":[0,0,0],"p2":[0,0,7500],"rot":"Front","plane":"Middle","depth":"Middle"}""").jsonObject as JsonObject
        val v = verts(b.build(st))
        assertEquals(150.0, v.maxOf { it[0] }, 1e-6); assertEquals(145.0, v.maxOf { it[1] }, 1e-6)   // flanges ±150 along X, depth ±145 along Y
    }
}
