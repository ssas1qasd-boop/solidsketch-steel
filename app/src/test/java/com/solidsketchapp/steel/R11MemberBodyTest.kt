package com.solidsketchapp.steel

import com.solidsketchapp.core.api.BodyId
import com.solidsketchapp.core.api.Mesh
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.zip.GZIPInputStream
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Member bodies at every C04 joint vs R11 (owner 2026-11-25: "528 / 528 parts" checked connection parts only and missed a purlin
 * floating 110 mm above the rafter in a test case). The app builds R11's 835 members from tests/steel/golden/r11/rebuild_inputs.json
 * and the 264 purlin cleats; at each joint it checks, against R11's own meshes, within 0.1 mm:
 *   - each purlin's box (2 per joint where there are two),
 *   - the rafter's top at the joint (max along n = from the support's axis toward the purlin, perpendicular to the support; constant
 *     along a straight member, so over the whole body; R11: the rafter assembly's web and flange plates),
 *   - the purlins' underside (min along n), i.e. the gap purlin - rafter.
 * Needs Manifold: -PhostJni. Writes build/r11-member-bodies.txt.
 */
class R11MemberBodyTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
    private fun pts(m: Mesh) = (0 until m.vertexCount).map { i -> DoubleArray(3) { m.positions[i * 3 + it].toDouble() } }
    private fun dot(a: DoubleArray, b: DoubleArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
    private fun sub(a: DoubleArray, b: DoubleArray) = doubleArrayOf(a[0] - b[0], a[1] - b[1], a[2] - b[2])
    private fun unit(a: DoubleArray): DoubleArray { val l = sqrt(dot(a, a)); return doubleArrayOf(a[0] / l, a[1] / l, a[2] / l) }
    private fun box(p: List<DoubleArray>) = (0..2).map { k -> p.minOf { it[k] } } + (0..2).map { k -> p.maxOf { it[k] } }

    @Test fun memberBodiesAtC04JointsEqualR11() {
        assumeTrue("host JNI not built (use -PhostJni)", System.getProperty("sscore.hostJni") == "1")
        val inp = Json.parseToJsonElement(File(root, "tests/steel/golden/r11/rebuild_inputs.json").readText()).jsonObject
        val r11 = Json.parseToJsonElement(GZIPInputStream(File(root, "tests/steel/models/Frame_28x53m_R11.ss").inputStream()).readBytes().decodeToString()).jsonObject.getValue("doc").jsonObject
        val rb = r11.getValue("bodies").jsonArray.map { it.jsonObject }.associateBy { it["id"]!!.jsonPrimitive.int }
        fun rpts(id: Int) = rb.getValue(id).getValue("shape").jsonObject.getValue("vp").jsonArray.map { it.jsonPrimitive.double }.chunked(3).map { it.toDoubleArray() }
        val members = inp.getValue("members").jsonArray.map { it.jsonObject }
        val t = AppGoldenTest(); val m = t.build(JsonObject(emptyMap()))
        val steps = inp.getValue("steps").jsonArray.map { it.jsonObject }
        steps.first { it["op"]!!.jsonPrimitive.content == "grid" }.let { m.doc.setField("stGrid", it.getValue("grid").toString()) }
        val ids = ArrayList<BodyId>(); for (mm in members) { val st = mm.getValue("st").jsonObject; ids += m.parts.addBuilt(st, m.parts.mesh(st)) }
        val tcl = steps.first { it["op"]!!.jsonPrimitive.content == "connectMany" }.getValue("list").jsonArray.map { it.jsonArray }.filter { it[0].jsonPrimitive.content == "Tcl" }
        m.comps.addMany(tcl.map { e -> Triple("Tcl", e[1].jsonArray.map { ids[it.jsonPrimitive.int] }, e[2].jsonObject) }, merge = true)
        var ok = 0; var n = 0; val bad = ArrayList<String>()
        for (e in tcl) {
            val ix = e[1].jsonArray.map { it.jsonPrimitive.int }; val raf = members[ix[0]]; val rst = raf.getValue("st").jsonObject
            val p1 = rst.getValue("p1").jsonArray.map { it.jsonPrimitive.double }.toDoubleArray(); val p2 = rst.getValue("p2").jsonArray.map { it.jsonPrimitive.double }.toDoubleArray()
            val a = unit(sub(p2, p1))
            val pst = members[ix[1]].getValue("st").jsonObject; val q1 = pst.getValue("p1").jsonArray.map { it.jsonPrimitive.double }.toDoubleArray()
            val pax = unit(sub(pst.getValue("p2").jsonArray.map { it.jsonPrimitive.double }.toDoubleArray(), q1))
            // the joint: the purlin axis point nearest the rafter axis
            val w0 = sub(p1, q1); val b = dot(a, pax); val dd = dot(a, w0); val ee = dot(pax, w0); val s = (ee - b * dd) / ((1 - b * b).takeIf { it != 0.0 } ?: 1e-9)
            val j = doubleArrayOf(q1[0] + pax[0] * s, q1[1] + pax[1] * s, q1[2] + pax[2] * s)
            // n: from the support's axis toward the purlin, perpendicular to the support (a sloped rafter: up; a column: out to its face)
            val wj = sub(j, p1); val nn = unit(sub(wj, doubleArrayOf(a[0] * dot(wj, a), a[1] * dot(wj, a), a[2] * dot(wj, a))))
            val near = { p: DoubleArray -> abs(dot(sub(p, j), a)) < 300 && abs(dot(sub(p, j), pax)) < 400 }
            val tag = "cleat ${e[4].jsonArray[0]}"; val errs = ArrayList<String>()
            // purlins: box
            for (i in ix.drop(1)) { val r = box(rpts(members[i].getValue("r10").jsonArray[0].jsonPrimitive.int)); val ap = box(pts(m.doc.bodyMesh(ids[i])!!))
                if ((0..5).any { abs(r[it] - ap[it]) > 0.1 }) errs += "purlin ${members[i]["r10"]} box ${ap.map { Math.round(it * 10) / 10.0 }} vs ${r.map { Math.round(it * 10) / 10.0 }}" }
            // rafter top and purlin underside at the joint
            val rTop = members[ix[0]].getValue("r10").jsonArray.map { it.jsonPrimitive.int }.flatMap { rpts(it) }.maxOf { dot(it, nn) }
            val aTop = pts(m.doc.bodyMesh(ids[ix[0]])!!).maxOf { dot(it, nn) }
            val rBot = ix.drop(1).flatMap { rpts(members[it].getValue("r10").jsonArray[0].jsonPrimitive.int) }.minOf { dot(it, nn) }
            val aBot = ix.drop(1).flatMap { pts(m.doc.bodyMesh(ids[it])!!) }.minOf { dot(it, nn) }
            if (abs(rTop - aTop) > 0.1) errs += "rafter top %.2f vs R11 %.2f".format(java.util.Locale.ROOT, aTop, rTop)
            if (abs(rBot - aBot) > 0.1) errs += "purlin underside %.2f vs R11 %.2f (gap app %.2f, R11 %.2f)".format(java.util.Locale.ROOT, aBot, rBot, aBot - aTop, rBot - rTop)
            n++; if (errs.isEmpty()) ok++ else bad += "$tag: " + errs.joinToString("; ")
        }
        val rep = "Member bodies at the C04 joints, app vs R11 (purlin boxes, rafter top, purlin underside, 0.1 mm): $ok / $n joints\n" + bad.take(60).joinToString("\n")
        File(root, "build").mkdirs(); File(root, "build/r11-member-bodies.txt").writeText(rep + "\n"); println(rep.lines().take(15).joinToString("\n"))
        assertEquals(bad.take(10).joinToString("\n"), n, ok)
    }
}
