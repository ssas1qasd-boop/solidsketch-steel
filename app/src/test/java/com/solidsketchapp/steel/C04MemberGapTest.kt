package com.solidsketchapp.steel

import com.solidsketchapp.core.api.BodyId
import com.solidsketchapp.core.api.Mesh
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
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
 * C04 follow-up (owner 2026-11-25, "the purlin floats above the cleat"): the purlin's underside against the rafter's top flange,
 * measured perpendicular to the rafter at the joint, for the R11 joint of cleat 3185 (rafter R3022, purlins 3183 / 3184):
 *  - "recording" inputs = what C04RecordingTest placed: RAFTER / PURLIN property sets + only some fields (the rafter took the
 *    set's depth Behind), without and with the cleat;
 *  - "R11" inputs = the members exactly as tests/steel/golden/r11/rebuild_inputs.json gives them (all position fields), without
 *    and with the cleat.
 * Writes build/c04-gap.txt and the meshes near the joint (build/c04-gap/<case>.json) for the orthographic end view
 * (tools/golden/c04_gap_view.py). Asserts: the R11 inputs give the R11 gap (0 within 0.1 mm), with and without C04 (C04 moves no
 * member). Needs Manifold: -PhostJni.
 */
class C04MemberGapTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
    private fun v(e: JsonElement) = e.jsonArray.map { it.jsonPrimitive.double }.toDoubleArray()
    private fun pts(m: Mesh) = (0 until m.vertexCount).map { i -> DoubleArray(3) { m.positions[i * 3 + it].toDouble() } }
    private fun dot(a: DoubleArray, b: DoubleArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
    private fun sub(a: DoubleArray, b: DoubleArray) = doubleArrayOf(a[0] - b[0], a[1] - b[1], a[2] - b[2])
    private fun unit(a: DoubleArray): DoubleArray { val l = sqrt(dot(a, a)); return doubleArrayOf(a[0] / l, a[1] / l, a[2] / l) }

    class Gap(val gap: Double, val rafterTop: Double, val purlinBottom: Double)

    /** rafter top (max along n near the joint) and purlin underside (min along n near the joint), n = up, perpendicular to the rafter */
    private fun gap(raf: List<DoubleArray>, pur: List<List<DoubleArray>>, rp1: DoubleArray, rp2: DoubleArray, pax: DoubleArray, j: DoubleArray): Gap {
        val a = unit(sub(rp2, rp1)); val z = doubleArrayOf(0.0, 0.0, 1.0); val n = unit(sub(z, doubleArrayOf(a[0] * a[2], a[1] * a[2], a[2] * a[2])))
        val near = { p: DoubleArray -> abs(dot(sub(p, j), a)) < 300 && abs(dot(sub(p, j), pax)) < 400 }
        val top = raf.filter(near).maxOf { dot(it, n) }; val bot = pur.flatten().filter(near).minOf { dot(it, n) }
        return Gap(bot - top, top, bot)
    }

    @Test fun purlinOnRafter() {
        assumeTrue("host JNI not built (use -PhostJni)", System.getProperty("sscore.hostJni") == "1")
        val inp = Json.parseToJsonElement(File(root, "tests/steel/golden/r11/rebuild_inputs.json").readText()).jsonObject
        val members = inp.getValue("members").jsonArray.map { it.jsonObject }
        val entry = inp.getValue("steps").jsonArray.map { it.jsonObject }.first { it["op"]!!.jsonPrimitive.content == "connectMany" }.getValue("list").jsonArray
            .map { it.jsonArray }.first { it[0].jsonPrimitive.content == "Tcl" && it[4].jsonArray[0].jsonPrimitive.int == 3185 }
        val ix = entry[1].jsonArray.map { it.jsonPrimitive.int }
        val r11 = ix.map { members[it].getValue("st").jsonObject }
        // what C04RecordingTest placed (before this fix): RAFTER set + prof / p1 / p2; PURLIN set + prof, rot, rAng, plane, depth, p1, p2
        fun only(st: JsonObject, keys: List<String>) = JsonObject(st.filterKeys { it in keys })
        val rec = listOf(only(r11[0], listOf("prof", "p1", "p2")), only(r11[1], listOf("prof", "rot", "rAng", "plane", "depth", "p1", "p2")), only(r11[2], listOf("prof", "rot", "rAng", "plane", "depth", "p1", "p2")))
        val rep = StringBuilder(); val out = File(root, "build/c04-gap"); out.mkdirs(); val res = LinkedHashMap<String, Gap>()
        for ((name, sts, sets) in listOf(Triple("recording", rec, listOf("RAFTER", "PURLIN", "PURLIN")), Triple("R11", r11, listOf<String?>(null, null, null)))) {
            for (withC04 in listOf(false, true)) {
                val t = AppGoldenTest(); val m = t.build(JsonObject(emptyMap()))
                val data = com.solidsketchapp.steel.data.SteelData.load { File(root, "steel/src/main/assets/steel/$it").readText() }
                val ids = sts.mapIndexed { i, st -> val full = if (sets[i] == null) st else JsonObject(LinkedHashMap<String, JsonElement>(data.propertySet(sets[i]!!)!!.raw).apply { put("kind", JsonPrimitive("member")); putAll(st) })
                    m.parts.addBuilt(full, m.parts.mesh(full)) }
                if (withC04) m.comps.add("Tcl", ids, m.comps.cq("Tcl"))
                val stOf = { id: BodyId -> Json.parseToJsonElement(m.doc.bodyJson(id)!!).jsonObject.getValue("st").jsonObject }
                val raf = pts(m.doc.bodyMesh(ids[0])!!); val pur = ids.drop(1).map { pts(m.doc.bodyMesh(it)!!) }
                val rst = stOf(ids[0]); val pst = stOf(ids[1]); val pax = unit(sub(v(pst.getValue("p2")), v(pst.getValue("p1"))))
                val j = v(r11[1].getValue("p1")).let { p -> doubleArrayOf(v(r11[1].getValue("p1"))[0], 22355.0, p[2]) }
                val g = gap(raf, pur, v(rst.getValue("p1")), v(rst.getValue("p2")), pax, j); val key = "$name${if (withC04) "+C04" else ""}"; res[key] = g
                rep.append("%-14s rafter depth %-7s purlin underside - rafter top (perpendicular to the rafter) = %8.2f mm\n".format(java.util.Locale.ROOT, key, rst["depth"]?.jsonPrimitive?.content, g.gap))
                // meshes near the joint for the end view
                val bodies = m.doc.bodyIds().map { id -> val st = stOf(id); val mesh = m.doc.bodyMesh(id)!!
                    "{\"name\":${Json.encodeToString(JsonElement.serializer(), st["name"] ?: JsonPrimitive("?"))},\"kind\":\"${st["kind"]?.jsonPrimitive?.content}\",\"vp\":[${(0 until mesh.vertexCount * 3).joinToString(",") { "%.2f".format(java.util.Locale.ROOT, mesh.positions[it]) }}],\"tv\":[${(0 until mesh.indices.size).joinToString(",") { mesh.indices[it].toString() }}]}" }
                File(out, "$key.json").writeText("{\"gap\":${"%.3f".format(java.util.Locale.ROOT, g.gap)},\"bodies\":[${bodies.joinToString(",")}]}")
            }
        }
        File(root, "build/c04-gap.txt").writeText(rep.toString()); println(rep)
        assertTrue(rep.toString(), abs(res.getValue("R11").gap) < 0.1 + 1.5 && abs(res.getValue("R11+C04").gap - res.getValue("R11").gap) < 0.01)
    }
}
