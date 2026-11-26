package com.solidsketchapp.steel

import com.solidsketchapp.core.api.BodyId
import com.solidsketchapp.core.api.Mesh
import com.solidsketchapp.core.io.MemoryDocument
import com.solidsketchapp.core.view.ManifoldBooleanEngine
import com.solidsketchapp.steel.conn.StRegen
import com.solidsketchapp.steel.data.SteelData
import com.solidsketchapp.steel.geom.StBuild
import com.solidsketchapp.steel.model.StParts
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.max

/**
 * S7 acceptance: End plate 144 through the component framework equals the S1 golden cases (tests/steel/golden/cases/t144_*:
 * standard, odd profile with 3 rows and stiffeners, sloped rafter): every body's volume and box, the bolt group (points in
 * v69's order, n, L, d), the welds, and the plates' part fields. Manifold on the build machine: -PhostJni.
 */
class S7T144Test {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
    private val data by lazy { SteelData.load { File(root, "steel/src/main/assets/steel/$it").readText() } }
    private val engine by lazy { ManifoldBooleanEngine() }
    @Before fun needsHostLib() = assumeTrue("host JNI not built (use -PhostJni)", System.getProperty("sscore.hostJni") == "1")

    private fun box(m: Mesh): List<Double> { val lo = DoubleArray(3) { Double.MAX_VALUE }; val hi = DoubleArray(3) { -Double.MAX_VALUE }
        for (i in 0 until m.vertexCount) for (k in 0..2) { val v = m.positions[i * 3 + k].toDouble(); lo[k] = minOf(lo[k], v); hi[k] = maxOf(hi[k], v) }; return lo.toList() + hi.toList() }

    @Test
    fun t144EqualsGolden() {
        val bad = mutableListOf<String>()
        for (case in listOf("t144_standard", "t144_odd_profile", "t144_sloped", "t144_c09_r12", "t144_c09_hea160_ipe300_5deg", "t144_c09_ipe200_ipe360_10deg_auto_convex", "t144_c09_concave_level", "t144_c02_r12", "teh_r12", "teh_hea300_ipe360_10deg", "teh_hea240_ipe300_15deg_tp25", "teh_ipe400_5deg_no144", "t144_c13_r12", "t144_c13_hea300_ipe360_stiff", "t144_c13_hem300_ipe450_sloped_gaps", "t144_c13_heb400_ipe500_perp_concave", "t186_r12_c2020", "t186_r12_egc2002", "t186_hea300_ipe300", "t186_heb240_ipe240_tekla_defaults", "t186_hea260_ipe270_skew15")) {
            val gold = Json.parseToJsonElement(File(root, "tests/steel/golden/cases/$case/dump.json").readText()).jsonObject
            val input = Json.parseToJsonElement(File(root, "tests/steel/golden/cases/$case/case.json").readText()).jsonObject
            val doc = MemoryDocument(); val build = StBuild(data.profiles, union = { engine.union(it) })
            val parts = StParts(data, doc, null, build)
            for (m in input.getValue("members").jsonArray) { val o = m.jsonObject
                parts.add(JsonObject(LinkedHashMap<String, kotlinx.serialization.json.JsonElement>().apply { put("kind", JsonPrimitive("member")); putAll(data.propertySet(o.getValue("set").jsonPrimitive.content)!!.raw); putAll(o.getValue("extra").jsonObject) }), "m") }
            val gc = gold.getValue("conns").jsonArray[0].jsonObject
            // every component of the case (C02h cases: End plate 144 + Eaves haunch 102), as the golden has them
            doc.setField("stConns", JsonArray(gold.getValue("conns").jsonArray.map { it.jsonObject }.map { g -> JsonObject(linkedMapOf("id" to g.getValue("id"), "type" to g.getValue("type"), "parts" to g.getValue("parts"), "q" to g.getValue("q"))) }).toString())
            StRegen(build, engine, data.profiles, data.tk).also { it.ui = com.solidsketchapp.steel.comp.TkUi.parse(File(root, "steel/src/main/assets/steel/tk_ui.json").readText()) }.regen(doc, listOf(BodyId("1"), BodyId("2")))
            // Bodies (members, plates): volume and box.
            for (gp in gold.getValue("parts").jsonArray) { val g = gp.jsonObject; val id = BodyId(g.getValue("id").jsonPrimitive.content)
                val m = doc.bodyMesh(id); if (m == null) { bad += "$case: body ${id.value} missing"; continue }
                val gv = g.getValue("volume").jsonPrimitive.double; val v = engine.volume(m)
                val gb = g.getValue("bbox").jsonObject.let { b -> b.getValue("min").jsonArray.map { it.jsonPrimitive.double } + b.getValue("max").jsonArray.map { it.jsonPrimitive.double } }
                if (abs(v - gv) > max(1.0, gv * 1e-3)) bad += "$case: body ${id.value} volume $v vs $gv"
                if ((0..5).any { abs(box(m)[it] - gb[it]) > 0.1 }) bad += "$case: body ${id.value} box ${box(m)} vs $gb"
                if (g["kind"]?.jsonPrimitive?.content == "conn") { val st = Json.parseToJsonElement(doc.bodyJson(id)!!).jsonObject.getValue("st").jsonObject
                    for (f in listOf("name", "prof", "mat", "cls", "asm")) if (st[f]?.jsonPrimitive?.content != g[f]?.jsonPrimitive?.content) bad += "$case: plate ${id.value} $f ${st[f]} vs ${g[f]}" } }
            // Bolt group: points in v69's order, n, L, d.
            for (gb0 in gold.getValue("bolts").jsonArray) { val gb = gb0.jsonObject; val id = BodyId(gb.getValue("id").jsonPrimitive.content)
                val st = doc.bodyJson(id)?.let { Json.parseToJsonElement(it).jsonObject["st"]?.jsonObject }; if (st == null) { bad += "$case: bolts ${id.value} missing"; continue }
                for (f in listOf("n", "L", "d")) if (abs(st.getValue(f).jsonPrimitive.double - gb.getValue(f).jsonPrimitive.double) > 1e-9) bad += "$case: bolts $f ${st[f]} vs ${gb[f]}"
                val op = st.getValue("pts").jsonArray.map { p -> p.jsonArray.map { it.jsonPrimitive.double } }; val gpts = gb.getValue("pts").jsonArray.map { p -> p.jsonArray.map { it.jsonPrimitive.double } }
                if (op.size != gpts.size || op.indices.any { i -> (0..2).any { abs(op[i][it] - gpts[i][it]) > 0.1 } }) bad += "$case: bolt points $op vs $gpts" }
            // Welds.
            val ocs = Json.parseToJsonElement(doc.field("stConns")!!).jsonArray; val gcs = gold.getValue("conns").jsonArray
            for (i in 0 until gcs.size) {
                val ow = (ocs[i].jsonObject["welds"]?.jsonArray ?: JsonArray(emptyList())).map { it.jsonObject.let { w -> Triple(w.getValue("name").jsonPrimitive.content, w.getValue("a").jsonPrimitive.double, Math.round(w.getValue("len").jsonPrimitive.double * 10) / 10.0) } }
                val gw = (gcs[i].jsonObject["welds"]?.jsonArray ?: JsonArray(emptyList())).map { it.jsonObject.let { w -> Triple(w.getValue("name").jsonPrimitive.content, w.getValue("a").jsonPrimitive.double, Math.round(w.getValue("len").jsonPrimitive.double * 10) / 10.0) } }
                if (ow != gw) bad += "$case: welds ${gcs[i].jsonObject["type"]} $ow vs $gw" }
        }
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
    }
}
