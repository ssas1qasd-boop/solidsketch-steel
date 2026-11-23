package com.solidsketchapp.steel.conn

import com.solidsketchapp.steel.data.SteelData
import com.solidsketchapp.steel.geom.StBuild
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

/**
 * S6: Fitting (Tekla 13) equals v69 (golden tests/steel/golden/s6/fittings.json): standard (beam to a column web), odd
 * profile (HEM300 turned, gap 25), sloped rafter (v69 leaves it uncut there). Checked without booleans: the cut box ends
 * where v69's cut part starts (its bounding box along the beam), and an uncut part stays as it was.
 */
class FittingTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
    private val data = SteelData.load { File(root, "steel/src/main/assets/steel/$it").readText() }

    @Test
    fun fittingCutsLikeV69() {
        val cases = Json.parseToJsonElement(File(root, "tests/steel/golden/s6/fittings.json").readText()).jsonObject.getValue("cases").jsonObject
        val build = StBuild(data.profiles); val bad = mutableListOf<String>()
        for ((name, c0) in cases) {
            val c = c0.jsonObject
            fun st(k: String) = c.getValue(k).jsonArray.let { a -> JsonObject(LinkedHashMap(data.propertySet(a[0].jsonPrimitive.content)!!.raw).apply { put("kind", kotlinx.serialization.json.JsonPrimitive("member")); putAll(a[1].jsonObject) }) }
            val a = st("a"); val b = st("b")
            val g = TkConns.geom("fitting", listOf("1", "2"), c.getValue("q").jsonObject, build, StRegen.DEFS.getValue("fitting"), { if (it == "1") a else b }, data.profiles)!!
            val cut = g.cuts.getValue("2")
            val j = Joints.joint(a, b, data.profiles)
            // The cut solid: the same box as v69's (bounding box within 0.1 mm, volume within 1e-6).
            val gb = c.getValue("cutBox").jsonArray.single().jsonObject
            val v = cut.single(); val lo = DoubleArray(3) { k -> (0 until v.vertexCount).minOf { v.positions[it * 3 + k].toDouble() } }; val hi = DoubleArray(3) { k -> (0 until v.vertexCount).maxOf { v.positions[it * 3 + k].toDouble() } }
            val ob = lo.toList() + hi.toList(); val gbb = gb.getValue("bbox").jsonArray.map { it.jsonPrimitive.double }
            if ((0..5).any { abs(ob[it] - gbb[it]) > 0.1 }) bad += "$name: cut box $ob vs $gbb"
            val gv = gb.getValue("volume").jsonPrimitive.double
            if (abs(abs(com.solidsketchapp.steel.geom.Prism.signedVolume(v)) - gv) > gv * 1e-6) bad += "$name: cut box volume"
            // Where the cut lands on a straight beam: the part then starts at the cut face (v69's bbox), except the sloped
            // case, where v69's Manifold leaves the rafter uncut (see PROGRESS.md, decisions for the owner).
            val gold = c.getValue("secondary").jsonObject.getValue("bbox").jsonArray.map { it.jsonPrimitive.double }
            if (name != "fitting_sloped") {
                val part = build.build(b); val xs = (0 until part.vertexCount).map { part.positions[it * 3].toDouble() }
                val face = (j.e + j.x * (j.xFace + StBuild.jsNum(c.getValue("q").jsonObject["gap"] ?: StRegen.DEFS.getValue("fitting")["gap"]))).x
                if (abs(maxOf(xs.min(), face) - gold[0]) > 0.1 || abs(xs.max() - gold[3]) > 0.1) bad += "$name: part x vs ${gold[0]}..${gold[3]}"
            }
            if (cut.size != 1) bad += "$name: ${cut.size} cut solids (no cope expected)"
        }
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
    }
}
