package com.solidsketchapp.steel

import com.solidsketchapp.core.api.BodyId
import com.solidsketchapp.core.api.Mesh
import com.solidsketchapp.core.view.ManifoldBooleanEngine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

/**
 * C04 purlin cleat (Tcl), v70 · Tekla parity (docs/steel/PARITY.md "C04"): the app rebuilds R10's 264 purlin cleats with the
 * R10 inputs (tests/steel/golden/r10/rebuild_inputs.json: the 835 members, then only the 264 Tcl entries, new components =
 * 'Cleat location' Front, the Tekla default) and every cleat, stiffener and bolt must sit where R10 has it
 * (tests/steel/golden/r10/c04_r10.json, tools/golden/r10_c04.py): box within 0.1 mm on all six numbers, volume within 2 %
 * (R10's solids carry no bolt holes), every bolt position within 0.1 mm. Then the same cleats with 'Back' (old models,
 * v69) must all be elsewhere. Needs Manifold: -PhostJni. Writes build/r10-c04-report.txt.
 */
class R10C04Test {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
    private val engine by lazy { ManifoldBooleanEngine() }
    private fun box(m: Mesh): List<Double> { val lo = DoubleArray(3) { Double.MAX_VALUE }; val hi = DoubleArray(3) { -Double.MAX_VALUE }
        for (i in 0 until m.vertexCount) for (k in 0..2) { val v = m.positions[i * 3 + k].toDouble(); lo[k] = minOf(lo[k], v); hi[k] = maxOf(hi[k], v) }; return lo.toList() + hi.toList() }

    private class Result(val matched: Int, val bolts: Int, val bad: List<String>)

    private fun run(loc: String?): Result {
        val inp = Json.parseToJsonElement(File(root, "tests/steel/golden/r10/rebuild_inputs.json").readText()).jsonObject
        val gold = Json.parseToJsonElement(File(root, "tests/steel/golden/r10/c04_r10.json").readText()).jsonObject.getValue("cleats").jsonArray.map { it.jsonObject }
        val t = AppGoldenTest(); val m = t.build(JsonObject(emptyMap()))
        val members = inp.getValue("members").jsonArray.map { it.jsonObject.getValue("st").jsonObject }
        val ids = ArrayList<BodyId>()
        val steps = inp.getValue("steps").jsonArray.map { it.jsonObject }
        steps.first { it["op"]!!.jsonPrimitive.content == "grid" }.let { m.doc.setField("stGrid", it.getValue("grid").toString()) }
        for (i in members.indices) ids += m.parts.addBuilt(members[i], m.parts.mesh(members[i]))
        val list = steps.first { it["op"]!!.jsonPrimitive.content == "connectMany" }.getValue("list").jsonArray
        // loc "old": the settings stored without 'loc' (a model saved before v70: merge = false keeps q as given, no CONN def)
        val q = if (loc == null || loc == "old") JsonObject(emptyMap()) else JsonObject(mapOf("loc" to kotlinx.serialization.json.JsonPrimitive(loc)))
        m.comps.addMany(gold.map { g -> val e = list[g.getValue("entry").jsonPrimitive.int].jsonArray
            Triple("Tcl", g.getValue("members").jsonArray.map { ids[it.jsonPrimitive.int] }, JsonObject(LinkedHashMap(e[2].jsonObject).apply { putAll(q) })) }, merge = loc != "old")
        val conns = m.comps.conns(); assertEquals(gold.size, conns.size)
        val kids = HashMap<String, MutableList<Pair<BodyId, JsonObject>>>()
        for (id in m.doc.bodyIds()) { val st = Json.parseToJsonElement(m.doc.bodyJson(id)!!).jsonObject["st"]?.jsonObject ?: continue
            st["conn"]?.jsonPrimitive?.content?.let { kids.getOrPut(it) { ArrayList() } += id to st } }
        var matched = 0; var nb = 0; val bad = ArrayList<String>()
        for ((g, c) in gold.zip(conns)) {
            val k = kids[c.id].orEmpty(); val tag = "R10 cleat ${g.getValue("cleat").jsonObject["id"]}"
            for ((key, name) in listOf("cleat" to "CLEAT", "stiff" to "CLEAT STIFFENER")) {
                val r = g.getValue(key).jsonObject; val body = k.firstOrNull { it.second["name"]?.jsonPrimitive?.content == name }
                if (body == null) { bad += "$tag: no $name"; continue }
                val mesh = m.doc.bodyMesh(body.first)!!; val bx = box(mesh); val gb = r.getValue("box").jsonArray.map { it.jsonPrimitive.double }
                val gv = r.getValue("volume").jsonPrimitive.double; val v = engine.volume(mesh)
                if ((0..5).all { abs(bx[it] - gb[it]) <= 0.1 } && abs(v - gv) <= 0.02 * gv) matched++
                else bad += "$tag: $name box ${bx.map { Math.round(it * 10) / 10.0 }} vs $gb, volume ${Math.round(v)} vs ${Math.round(gv)}"
            }
            val pts = k.filter { it.second["kind"]?.jsonPrimitive?.content == "bolts" }.flatMap { b -> b.second.getValue("pts").jsonArray.map { p -> p.jsonArray.map { it.jsonPrimitive.double } } }
            for (gp in g.getValue("bolts").jsonArray.map { p -> p.jsonArray.map { it.jsonPrimitive.double } })
                if (pts.any { p -> (0..2).all { abs(p[it] - gp[it]) <= 0.1 } }) nb++ else bad += "$tag: no bolt at $gp"
        }
        return Result(matched, nb, bad)
    }

    @Test fun c04FrontEqualsR10() {
        assumeTrue("host JNI not built (use -PhostJni)", System.getProperty("sscore.hostJni") == "1")
        val r = run(null)   // new components: the Tekla default (Front)
        val rep = "C04 purlin cleat, app (Cleat location Front) vs R10: ${r.matched} / 528 parts, ${r.bolts} / 1056 bolts within 0.1 mm\n" + r.bad.take(100).joinToString("\n")
        File(root, "build").mkdirs(); File(root, "build/r10-c04-report.txt").writeText(rep + "\n"); println(rep.lines().take(20).joinToString("\n"))
        assertEquals(r.bad.take(20).joinToString("\n"), 528, r.matched); assertEquals(1056, r.bolts)
    }

    @Test fun c04BackKeepsV69() {
        assumeTrue("host JNI not built (use -PhostJni)", System.getProperty("sscore.hostJni") == "1")
        for (loc in listOf("back", "old")) {   // v69 placement: behind the web, mirrored about the purlin
            val r = run(loc); println("C04, app (Cleat location $loc) vs R10: ${r.matched} / 528 parts, ${r.bolts} / 1056 bolts")
            assertTrue("$loc: ${r.matched} parts, ${r.bolts} bolts", r.matched == 0 && r.bolts == 0)
        }
    }
}
