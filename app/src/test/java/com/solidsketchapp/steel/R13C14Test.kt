package com.solidsketchapp.steel

import com.solidsketchapp.core.api.BodyId
import com.solidsketchapp.core.api.Mesh
import com.solidsketchapp.core.io.SsCodec
import com.solidsketchapp.core.view.ManifoldBooleanEngine
import com.solidsketchapp.steel.conn.Joints
import com.solidsketchapp.steel.geom.D3
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
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * C14 Shear plate (146), joist <- girder, v70 · Tekla parity (docs/steel/PARITY.md "C14"): the app rebuilds R12's 68 joist joints with the
 * R12 inputs (tests/steel/golden/r13/rebuild_inputs.json: every member, then the 68 row-14 T146 entries: Bolts tab 'Top' 77.2, edge 30,
 * horizontal edge 35, gap 20) and checks against R12 itself (Tekla's IFC), within 0.1 mm:
 *   - every shear tab (box, 6 numbers),
 *   - every bolt (point, axis, length, grade: 272),
 *   - the joist's and the girder's solid inside a box 300 mm round each tab (symmetric difference <= 0.1 mm x surface area; two girder
 *     ends are cut by the column's own connection, not built here, so the girder pass mark is 66 / 68).
 * Needs Manifold: -PhostJni. Writes build/r13-c14-report.txt.
 */
class R13C14Test {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
    private val engine by lazy { ManifoldBooleanEngine() }
    private fun box(v: List<DoubleArray>) = (0..2).map { k -> v.minOf { it[k] } } + (0..2).map { k -> v.maxOf { it[k] } }
    private fun pts(m: Mesh) = (0 until m.vertexCount).map { i -> DoubleArray(3) { m.positions[i * 3 + it].toDouble() } }
    private fun area(m: Mesh): Double { var a = 0.0; val p = m.positions; val ix = m.indices
        for (t in 0 until m.triangleCount) { val i = 3 * ix[3 * t]; val j = 3 * ix[3 * t + 1]; val k = 3 * ix[3 * t + 2]
            val ux = (p[j] - p[i]).toDouble(); val uy = (p[j + 1] - p[i + 1]).toDouble(); val uz = (p[j + 2] - p[i + 2]).toDouble()
            val vx = (p[k] - p[i]).toDouble(); val vy = (p[k + 1] - p[i + 1]).toDouble(); val vz = (p[k + 2] - p[i + 2]).toDouble()
            a += sqrt((uy * vz - uz * vy).let { it * it } + (uz * vx - ux * vz).let { it * it } + (ux * vy - uy * vx).let { it * it }) / 2 }; return a }

    @Test fun c14EqualsR12() {
        assumeTrue("host JNI not built (use -PhostJni)", System.getProperty("sscore.hostJni") == "1")
        val inp = Json.parseToJsonElement(File(root, "tests/steel/golden/r13/rebuild_inputs.json").readText()).jsonObject
        val r12 = SsCodec.read(File(root, "tests/steel/models/Frame_28x53m_R13.ss").inputStream())
        val rst = { id: Int -> Json.parseToJsonElement(r12.bodyJson(BodyId(id.toString()))!!).jsonObject }
        val t = AppGoldenTest(); val m = t.build(JsonObject(emptyMap()))
        val members = inp.getValue("members").jsonArray.map { it.jsonObject }
        val steps = inp.getValue("steps").jsonArray.map { it.jsonObject }
        steps.first { it["op"]!!.jsonPrimitive.content == "grid" }.let { m.doc.setField("stGrid", it.getValue("grid").toString()) }
        val list = steps.first { it["op"]!!.jsonPrimitive.content == "connectMany" }.getValue("list").jsonArray.map { it.jsonArray }
            .filter { it[0].jsonPrimitive.content == "T146" && it[3].jsonPrimitive.content == "14" }
        val need = list.flatMap { e -> e[1].jsonArray.map { it.jsonPrimitive.int } }.toSortedSet()
        val ids = HashMap<Int, BodyId>(); for (i in need) { val st = members[i].getValue("st").jsonObject; ids[i] = m.parts.addBuilt(st, m.parts.mesh(st)) }
        m.comps.addMany(list.map { e -> Triple("T146", e[1].jsonArray.map { ids.getValue(it.jsonPrimitive.int) }, e[2].jsonObject) }, merge = true)
        val conns = m.comps.conns(); assertEquals(list.size, conns.size)
        val kids = HashMap<String, MutableList<Pair<BodyId, JsonObject>>>()
        for (id in m.doc.bodyIds()) { val st = Json.parseToJsonElement(m.doc.bodyJson(id)!!).jsonObject["st"]?.jsonObject ?: continue
            st["conn"]?.jsonPrimitive?.content?.let { kids.getOrPut(it) { ArrayList() } += id to st } }
        val tkb = Json.parseToJsonElement(r12.field("stConns")!!).jsonArray.map { it.jsonObject }.filter { it["type"]!!.jsonPrimitive.content == "TKBOLT" }
        var tabs = 0; var bolts = 0; var nb = 0; var joists = 0; var girders = 0; val bad = ArrayList<String>()
        for ((e, c) in list.zip(conns)) {
            val tabId = e[4].jsonArray[0].jsonPrimitive.int; val k = kids[c.id].orEmpty(); val tag = "R12 tab $tabId"
            val rtab = box(pts(r12.bodyMesh(BodyId(tabId.toString()))!!))
            val tab = k.firstOrNull { it.second["name"]?.jsonPrimitive?.content == "SHEAR TAB" }
            if (tab == null) bad += "$tag: no tab" else { val b = box(pts(m.doc.bodyMesh(tab.first)!!)); if ((0..5).all { abs(b[it] - rtab[it]) <= 0.1 }) tabs++ else bad += "$tag: tab ${b.map { Math.round(it * 10) / 10.0 }} vs $rtab" }
            // bolts: point, axis, length, grade
            val g = tkb.first { tabId in it.getValue("parts").jsonArray.map { p -> p.jsonPrimitive.int } }; val gq = g.getValue("q").jsonObject
            val gax = gq.getValue("ax").jsonArray.map { it.jsonPrimitive.double }; val gL = gq.getValue("L").jsonPrimitive.double; val gGrade = gq.getValue("grade").jsonPrimitive.content
            val ours = k.filter { it.second["kind"]?.jsonPrimitive?.content == "bolts" }
            for (gp in gq.getValue("pts").jsonArray.map { p -> p.jsonArray.map { it.jsonPrimitive.double } }) {
                nb++
                val hit = ours.firstOrNull { b -> b.second.getValue("pts").jsonArray.any { p -> val q = p.jsonArray.map { it.jsonPrimitive.double }; (0..2).all { abs(q[it] - gp[it]) <= 0.1 } } }
                val ok = hit != null && hit.second.getValue("ax").jsonArray.map { it.jsonPrimitive.double }.zip(gax).all { (a, b) -> abs(a - b) <= 1e-3 } &&
                    abs(hit.second.getValue("L").jsonPrimitive.double - gL) < 0.5 && hit.second.getValue("grade").jsonPrimitive.content == gGrade
                if (ok) bolts++ else bad += "$tag: bolt at $gp (axis $gax, L $gL, $gGrade) not reproduced"
            }
            // member bodies in a box 300 mm round the tab
            val lo = D3(rtab[0] - 300, rtab[1] - 300, rtab[2] - 300); val hi = D3(rtab[3] + 300, rtab[4] + 300, rtab[5] + 300)
            val cube = Joints.box3(lo, D3(1.0, 0.0, 0.0), D3(0.0, 1.0, 0.0), 0.0, hi.x - lo.x, 0.0, hi.y - lo.y, 0.0, hi.z - lo.z)
            for ((role, mi) in listOf("girder" to e[1].jsonArray[0].jsonPrimitive.int, "joist" to e[1].jsonArray[1].jsonPrimitive.int)) {
                val plates = members[mi].getValue("r10").jsonArray.map { r12.bodyMesh(BodyId(it.jsonPrimitive.int.toString()))!! }
                val rr = engine.intersect(engine.union(plates), cube); val oo = engine.intersect(m.doc.bodyMesh(ids.getValue(mi))!!, cube)
                val x = engine.volume(engine.subtract(rr, listOf(oo))) + engine.volume(engine.subtract(oo, listOf(rr)))
                if (x <= 0.1 * area(rr)) { if (role == "joist") joists++ else girders++ } else bad += "$tag: $role differs by %.0f mm³ (%.3f mm over its faces)".format(java.util.Locale.ROOT, x, x / area(rr))
            }
        }
        val rep = "C14 Shear plate 146 (joist <- girder), app vs R12: tabs $tabs / ${list.size}, bolts $bolts / $nb, joists $joists / ${list.size}, girders $girders / ${list.size}\n" + bad.take(40).joinToString("\n")
        File(root, "build").mkdirs(); File(root, "build/r13-c14-report.txt").writeText(rep + "\n"); println(rep.lines().take(12).joinToString("\n"))
        assertEquals(rep, list.size, tabs); assertEquals(rep, nb, bolts); assertEquals(rep, list.size, joists); assertEquals(rep, list.size - 2, girders)
    }
}
