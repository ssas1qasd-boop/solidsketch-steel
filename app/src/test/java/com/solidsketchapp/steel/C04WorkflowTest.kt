package com.solidsketchapp.steel

import com.solidsketchapp.core.api.BodyId
import com.solidsketchapp.core.api.Mesh
import com.solidsketchapp.steel.comp.GenRun
import com.solidsketchapp.steel.data.SteelData
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * C04, the owner's own workflow (2026-11-25), in the app: two rafters from the default RAFTER property set (IPE360, depth Behind) on a
 * 12.8° slope, purlins by "Generation of purlins (50)" (T50p, profile CC200-2-20-60 saved as the dialog default), then the purlin
 * cleat (Tcl) on rafter A and the middle purlin; the same as tools/golden/c04_workflow.js in web v70. Dumps build/c04-workflow-app.json
 * (measured by tools/golden/c04_measure.py) and asserts: every purlin's underside lies on rafter A's top, perpendicular to the rafter
 * (both bottom corners within 0.1 mm), and the profile is the one chosen. Needs Manifold: -PhostJni.
 */
class C04WorkflowTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
    private fun f(v: Double) = "%.4f".format(java.util.Locale.ROOT, v)
    private fun meshJson(m: Mesh) = "{\"v\":[${(0 until m.vertexCount).joinToString(",") { i -> "[${f(m.positions[i * 3].toDouble())},${f(m.positions[i * 3 + 1].toDouble())},${f(m.positions[i * 3 + 2].toDouble())}]" }}],\"t\":[${(0 until m.indices.size).joinToString(",") { m.indices[it].toString() }}]}"

    @Test fun purlinsOnTheRafterTop() {
        assumeTrue("host JNI not built (use -PhostJni)", System.getProperty("sscore.hostJni") == "1")
        val data = SteelData.load { File(root, "steel/src/main/assets/steel/$it").readText() }
        val t = AppGoldenTest(); val m = t.build(JsonObject(emptyMap()))
        val store = com.solidsketchapp.steel.comp.MemoryStore().apply { put("ss.connDef", "{\"T50p\":{\"prof\":\"CC200-2-20-60\"}}") }   // the T50p dialog: Profile CC200-2-20-60, saved as default
        val comps = com.solidsketchapp.steel.comp.Components(com.solidsketchapp.steel.comp.TkUi.parse(File(root, "steel/src/main/assets/steel/tk_ui.json").readText()), m.doc, null, store, m.regen)
        fun raf(p1: List<Double>, p2: List<Double>): BodyId { val st = JsonObject(LinkedHashMap<String, JsonElement>(data.propertySet("RAFTER")!!.raw).apply {
            put("kind", JsonPrimitive("member")); put("p1", JsonArray(p1.map { JsonPrimitive(it) })); put("p2", JsonArray(p2.map { JsonPrimitive(it) })) }); return m.parts.add(st, "w") }
        val a = raf(listOf(0.0, 0.0, 7500.0), listOf(6468.0, 0.0, 8970.0)); val b = raf(listOf(0.0, 5570.0, 7500.0), listOf(6468.0, 5570.0, 8970.0))
        GenRun(data, m.doc, null, m.parts, comps, m.regen).run("T50p", listOf(a, b))
        val stOf = { id: BodyId -> Json.parseToJsonElement(m.doc.bodyJson(id)!!).jsonObject["st"]?.jsonObject }
        val pur = m.doc.bodyIds().filter { stOf(it)?.get("name")?.jsonPrimitive?.content == "PURLIN" }
        comps.add("Tcl", listOf(a, pur[pur.size / 2]), comps.cq("Tcl"))
        val keep = setOf("prof", "p1", "p2", "rot", "rAng", "plane", "depth", "conn", "pts", "ax", "d", "L", "n", "grade")
        val bodies = m.doc.bodyIds().map { id -> val st = stOf(id) ?: JsonObject(emptyMap())
            "{\"id\":\"${id.value}\",\"name\":${st["name"] ?: "null"},\"kind\":${st["kind"] ?: "null"},\"st\":${JsonObject(st.filterKeys { it in keep })},\"m\":${meshJson(m.doc.bodyMesh(id)!!)}}" }
        val conns = comps.conns().map { c -> "{\"id\":\"${c.id}\",\"type\":\"${c.type}\",\"parts\":${JsonArray(c.parts.map { JsonPrimitive(it) })},\"q\":${c.q}}" }
        File(root, "build").mkdirs()
        File(root, "build/c04-workflow-app.json").writeText("{\"A\":\"${a.value}\",\"B\":\"${b.value}\",\"bodies\":[${bodies.joinToString(",")}],\"conns\":[${conns.joinToString(",")}]}")
        // purlin underside on rafter A's top, perpendicular to the rafter
        val sa = stOf(a)!!; val p1 = sa.getValue("p1").jsonArray.map { it.jsonPrimitive.double }; val p2 = sa.getValue("p2").jsonArray.map { it.jsonPrimitive.double }
        val ax = (0..2).map { p2[it] - p1[it] }.let { v -> val l = sqrt(v.sumOf { it * it }); v.map { it / l } }
        val n = listOf(-ax[0] * ax[2], -ax[1] * ax[2], 1 - ax[2] * ax[2]).let { v -> val l = sqrt(v.sumOf { it * it }); v.map { it / l } }
        val ma = m.doc.bodyMesh(a)!!; val top = (0 until ma.vertexCount).maxOf { i -> (0..2).sumOf { ma.positions[i * 3 + it] * n[it] } }
        val rep = StringBuilder("C04 workflow (app): rafter top %.2f\n".format(java.util.Locale.ROOT, top)); var ok = true
        for (p in pur) { val pm = m.doc.bodyMesh(p)!!; val s = (0 until pm.vertexCount).map { i -> (0..2).sumOf { pm.positions[i * 3 + it] * n[it] } to (0..2).sumOf { pm.positions[i * 3 + it] * ax[it] } }
            val lo = s.minOf { it.first }; val acrossLo = s.minBy { it.first }.second; val hi = s.filter { abs(it.second - acrossLo) > 20 }.minOfOrNull { it.first } ?: lo
            rep.append("purlin ${p.value} ${stOf(p)!!["prof"]}: underside - rafter top %+.2f / %+.2f mm\n".format(java.util.Locale.ROOT, lo - top, hi - top))
            if (abs(lo - top) > 0.1 || abs(hi - top) > 0.1 || stOf(p)!!["prof"]?.jsonPrimitive?.content != "CC200-2-20-60") ok = false }
        File(root, "build/c04-workflow-app.txt").writeText(rep.toString()); println(rep)
        assertTrue(rep.toString(), ok)
    }

    /** The emulator's case (C04RecordingTest): three rafters, two purlin runs (A-B, B-C), Tcl on B and the two purlins over it; one cleat per joint. */
    @Test fun threeRaftersOneCleatPerJoint() {
        assumeTrue("host JNI not built (use -PhostJni)", System.getProperty("sscore.hostJni") == "1")
        val data = SteelData.load { File(root, "steel/src/main/assets/steel/$it").readText() }
        val t = AppGoldenTest(); val m = t.build(JsonObject(emptyMap()))
        val store = com.solidsketchapp.steel.comp.MemoryStore().apply { put("ss.connDef", "{\"T50p\":{\"prof\":\"CC200-2-20-60\"}}") }
        val comps = com.solidsketchapp.steel.comp.Components(com.solidsketchapp.steel.comp.TkUi.parse(File(root, "steel/src/main/assets/steel/tk_ui.json").readText()), m.doc, null, store, m.regen)
        val raf = listOf(0.0, 5570.0, 11140.0).map { y -> val st = JsonObject(LinkedHashMap<String, JsonElement>(data.propertySet("RAFTER")!!.raw).apply {
            put("kind", JsonPrimitive("member")); put("p1", JsonArray(listOf(0.0, y, 7500.0).map { JsonPrimitive(it) })); put("p2", JsonArray(listOf(6468.0, y, 8970.0).map { JsonPrimitive(it) })) }); m.parts.add(st, "w") }
        val gr = GenRun(data, m.doc, null, m.parts, comps, m.regen); gr.run("T50p", listOf(raf[0], raf[1])); gr.run("T50p", listOf(raf[1], raf[2]))
        val stOf = { id: BodyId -> Json.parseToJsonElement(m.doc.bodyJson(id)!!).jsonObject["st"]?.jsonObject }
        fun p1(id: BodyId) = stOf(id)!!.getValue("p1").jsonArray.map { it.jsonPrimitive.double }
        val pur = m.doc.bodyIds().filter { stOf(it)?.get("name")?.jsonPrimitive?.content == "PURLIN" }.sortedWith(compareBy({ p1(it)[0] }, { p1(it)[1] }))
        val x0 = p1(pur[0])[0]; val two = pur.filter { abs(p1(it)[0] - x0) < 1 }
        comps.add("Tcl", listOf(raf[1]) + two, comps.cq("Tcl"))
        val keep = setOf("prof", "p1", "p2", "rot", "rAng", "plane", "depth", "conn", "pts", "ax", "d", "L", "n", "grade")
        val bodies = m.doc.bodyIds().map { id -> val st = stOf(id) ?: JsonObject(emptyMap())
            "{\"id\":\"${id.value}\",\"name\":${st["name"] ?: "null"},\"kind\":${st["kind"] ?: "null"},\"st\":${JsonObject(st.filterKeys { it in keep })},\"m\":${meshJson(m.doc.bodyMesh(id)!!)}}" }
        val conns = comps.conns().map { c -> "{\"id\":\"${c.id}\",\"type\":\"${c.type}\",\"parts\":${JsonArray(c.parts.map { JsonPrimitive(it) })},\"q\":${c.q}}" }
        File(root, "build").mkdirs()
        File(root, "build/c04-workflow3-app.json").writeText("{\"A\":\"${raf[1].value}\",\"B\":\"${raf[2].value}\",\"bodies\":[${bodies.joinToString(",")}],\"conns\":[${conns.joinToString(",")}]}")
        // one cleat per rafter-B joint: every purlin end on rafter B has exactly one cleat (Tpc or Tcl)
        val cs = comps.conns(); val onB = cs.filter { (it.type == "Tpc" || it.type == "Tcl") && it.parts[0] == raf[1].value }
        // the joints on rafter B: the purlins' positions along the rafter (both runs' purlins butt there); each joint has exactly one cleat
        val joints = pur.groupBy { Math.round(p1(it)[0]) }
        val perJoint = joints.values.map { ids -> onB.count { c -> ids.any { it.value in c.parts } } }
        assertTrue("cleats per joint on rafter B: $perJoint", perJoint.all { it == 1 } && onB.count { it.type == "Tcl" } == 1)
    }
}
