package com.solidsketchapp.steel.conn

import com.solidsketchapp.core.api.Mesh
import com.solidsketchapp.steel.data.SteelData
import com.solidsketchapp.steel.geom.Prism
import com.solidsketchapp.steel.geom.StBuild
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.max

/**
 * S6: TKBOLT (v58 bolt options) and TKCUT geometry equal v69 (golden: tests/steel/golden/s6/bolts.json, from the live
 * stConnGeom chain). Without booleans: bounding boxes (0.1 mm), bolt order / count / length, and hole volumes where the
 * holes do not overlap. Volumes of unions (bolt + nut + washer, slots) are checked with Manifold in :app (-PhostJni).
 */
class StBoltsTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
    private val data = SteelData.load { File(root, "steel/src/main/assets/steel/$it").readText() }
    private val cases = Json.parseToJsonElement(File(root, "tests/steel/golden/s6/bolts.json").readText()).jsonObject.getValue("cases").jsonObject

    private fun bbox(ms: List<Mesh>): List<Double> {
        val lo = DoubleArray(3) { Double.MAX_VALUE }; val hi = DoubleArray(3) { -Double.MAX_VALUE }
        for (m in ms) for (i in 0 until m.vertexCount) for (k in 0..2) { val v = m.positions[i * 3 + k].toDouble(); lo[k] = minOf(lo[k], v); hi[k] = maxOf(hi[k], v) }
        return lo.toList() + hi.toList()
    }
    private fun near(a: List<Double>, b: List<Double>, tol: Double = 0.1) = a.size == b.size && a.indices.all { abs(a[it] - b[it]) <= tol }

    @Test
    fun boltGroupsAndCutsEqualV69() {
        val build = StBuild(data.profiles); val bad = mutableListOf<String>(); var checked = 0
        for ((name, c0) in cases) {
            val c = c0.jsonObject; val q = c.getValue("q").jsonObject
            val type = if (c.containsKey("cut")) "TKCUT" else "TKBOLT"
            val g = TkConns.geom(type, if (type == "TKCUT") listOf("5") else listOf("1", "2"), q, build, StRegen.DEFS.getValue(type))
            if (type == "TKCUT") {
                val gold = c.getValue("cut").jsonArray.single().jsonObject; val m = g!!.cuts.getValue("5").single()
                if (!near(bbox(listOf(m)), gold.getValue("bbox").jsonArray.map { it.jsonPrimitive.double })) bad += "$name: cut bbox"
                if (abs(abs(Prism.signedVolume(m)) - gold.getValue("volume").jsonPrimitive.double) > gold.getValue("volume").jsonPrimitive.double * 1e-4) bad += "$name: cut volume"
                checked++; continue
            }
            for ((k, id) in listOf("holes1" to "1", "holes2" to "2")) {
                val gold = c[k]; val ours = g?.cuts?.get(id)
                if (gold == null || gold is JsonNull) { if (ours != null && ours.isNotEmpty()) bad += "$name: $k should be empty"; continue }
                val gh = gold.jsonArray
                if (gh.isEmpty()) { if (!ours.isNullOrEmpty()) bad += "$name: $k should be empty"; continue }
                if (ours.isNullOrEmpty()) { bad += "$name: $k missing"; continue }
                val gb = gh.single().jsonObject
                if (!near(bbox(ours), gb.getValue("bbox").jsonArray.map { it.jsonPrimitive.double })) bad += "$name: $k bbox ${bbox(ours)} vs ${gb["bbox"]}"
                // Holes are separate cylinders; when they do not overlap, their volumes add up to v69's union.
                val sum = ours.sumOf { abs(Prism.signedVolume(it)) }; val gv = gb.getValue("volume").jsonPrimitive.double
                if (!q.containsKey("htype") && abs(sum - gv) > max(1.0, gv * 1e-4)) bad += "$name: $k volume $sum vs $gv"
            }
            val gp = c["part"]
            if (gp == null || gp is JsonNull) { if (g?.parts?.isNotEmpty() == true) bad += "$name: no bolt part expected" }
            else {
                val gpo = gp.jsonObject; val p = g?.parts?.singleOrNull()
                if (p == null) bad += "$name: bolt part missing" else {
                    if (p.name != gpo.getValue("name").jsonPrimitive.content) bad += "$name: name ${p.name}"
                    if (!near(bbox(p.pieces), gpo.getValue("bbox").jsonArray.map { it.jsonPrimitive.double })) bad += "$name: bolt bbox ${bbox(p.pieces)} vs ${gpo["bbox"]}"
                    val gb = gpo.getValue("bolts").jsonObject
                    for (f in listOf("n", "d", "L")) if (abs(p.bolts!![f]!!.jsonPrimitive.double - gb.getValue(f).jsonPrimitive.double) > 1e-9) bad += "$name: bolts.$f"
                    val gpts = gb.getValue("pts").jsonArray.map { e -> e.jsonArray.map { it.jsonPrimitive.double } }
                    val opts = p.bolts!!.getValue("pts").jsonArray.map { e -> e.jsonArray.map { it.jsonPrimitive.double } }
                    if (gpts.size != opts.size || gpts.indices.any { !near(gpts[it], opts[it], 1e-6) }) bad += "$name: bolt points / order $opts vs $gpts"
                    if (!near(gb.getValue("ax").jsonArray.map { it.jsonPrimitive.double }, p.bolts!!.getValue("ax").jsonArray.map { it.jsonPrimitive.double }, 1e-9)) bad += "$name: axis"
                }
            }
            checked++
        }
        println("bolt / cut cases: ${checked - bad.size}/$checked equal to v69")
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
    }
}
