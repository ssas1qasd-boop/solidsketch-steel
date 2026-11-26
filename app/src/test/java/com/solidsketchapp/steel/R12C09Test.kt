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
 * C02 + C09 + C13 End plate (144), v70 · Tekla parity (docs/steel/PARITY.md "C02 / C09"): the app rebuilds R12's 144 joints of rows 2 (eave,
 * column <- rafter, 48) and 9 (monitor post on the rafter, 24) with the R12 inputs (tests/steel/golden/r12/rebuild_inputs.json: Haunch tab
 * top / bottom plate t 8, b 60, h 60, chamfers 15 line, creation 'both'; Bolts tab bolt length Tekla, bolting direction 2) and checks
 * against R12 itself (Tekla's IFC), within 0.1 mm:
 *   - every end plate and haunch plate (box, 6 numbers),
 *   - every bolt (point, axis, length, grade),
 *   - the two members' solids inside a box 300 mm round the end plate: symmetric difference <= 0.1 mm x surface area, or (built-up
 *     members whose own section differs from Tekla's: R12-REPORT "INPUT EQUAL, SOLID DIFFERS") no larger than the same member with no
 *     connection, with what the connection removes (end cut, holes) outside Tekla's part.
 * Row 2's tapered eave haunch (Web FLT8*80 + Bottom flange FLT10*150, 48 + 48) is C02h, the Eave haunch (Teh: Haunch length
 * 2,000, depth 82.91; row "2h"): each part's box within 0.1 mm, and R12's solid covered by ours (missing volume <= 0.1 mm x its surface).
 * Needs Manifold: -PhostJni. Writes build/r12-c09-report.txt.
 */
class R12C09Test {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
    private val engine by lazy { ManifoldBooleanEngine() }
    private fun box(v: List<DoubleArray>) = (0..2).map { k -> v.minOf { it[k] } } + (0..2).map { k -> v.maxOf { it[k] } }
    private fun pts(m: Mesh) = (0 until m.vertexCount).map { i -> DoubleArray(3) { m.positions[i * 3 + it].toDouble() } }
    private fun area(m: Mesh): Double { var a = 0.0; val p = m.positions; val ix = m.indices
        for (t in 0 until m.triangleCount) { val i = 3 * ix[3 * t]; val j = 3 * ix[3 * t + 1]; val k = 3 * ix[3 * t + 2]
            val ux = (p[j] - p[i]).toDouble(); val uy = (p[j + 1] - p[i + 1]).toDouble(); val uz = (p[j + 2] - p[i + 2]).toDouble()
            val vx = (p[k] - p[i]).toDouble(); val vy = (p[k + 1] - p[i + 1]).toDouble(); val vz = (p[k + 2] - p[i + 2]).toDouble()
            a += sqrt((uy * vz - uz * vy).let { it * it } + (uz * vx - ux * vz).let { it * it } + (ux * vy - uy * vx).let { it * it }) / 2 }; return a }

    @Test fun c02c09EqualR12() {
        assumeTrue("host JNI not built (use -PhostJni)", System.getProperty("sscore.hostJni") == "1")
        val inp = Json.parseToJsonElement(File(root, "tests/steel/golden/r12/rebuild_inputs.json").readText()).jsonObject
        val r12 = SsCodec.read(File(root, "tests/steel/models/Frame_28x53m_R12.ss").inputStream())
        val t = AppGoldenTest(); val m = t.build(JsonObject(emptyMap()))
        val members = inp.getValue("members").jsonArray.map { it.jsonObject }
        val steps = inp.getValue("steps").jsonArray.map { it.jsonObject }
        steps.first { it["op"]!!.jsonPrimitive.content == "grid" }.let { m.doc.setField("stGrid", it.getValue("grid").toString()) }
        val list = steps.first { it["op"]!!.jsonPrimitive.content == "connectMany" }.getValue("list").jsonArray.map { it.jsonArray }
            .filter { it[0].jsonPrimitive.content in setOf("T144", "Teh") && it[3].jsonPrimitive.content in setOf("2", "9", "13") }
        val need = list.flatMap { e -> e[1].jsonArray.map { it.jsonPrimitive.int } }.toSortedSet()
        val ids = HashMap<Int, BodyId>(); for (i in need) { val st = members[i].getValue("st").jsonObject; ids[i] = m.parts.addBuilt(st, m.parts.mesh(st)) }
        m.comps.addMany(list.map { e -> Triple(e[0].jsonPrimitive.content, e[1].jsonArray.map { ids.getValue(it.jsonPrimitive.int) }, e[2].jsonObject) }, merge = true)
        val conns = m.comps.conns(); assertEquals(list.size, conns.size)
        val kids = HashMap<String, MutableList<Pair<BodyId, JsonObject>>>()
        for (id in m.doc.bodyIds()) { val st = Json.parseToJsonElement(m.doc.bodyJson(id)!!).jsonObject["st"]?.jsonObject ?: continue
            st["conn"]?.jsonPrimitive?.content?.let { kids.getOrPut(it) { ArrayList() } += id to st } }
        val tkb = Json.parseToJsonElement(r12.field("stConns")!!).jsonArray.map { it.jsonObject }.filter { it["type"]!!.jsonPrimitive.content == "TKBOLT" }
        val cnt = HashMap<String, IntArray>()   // row -> [plates ok, plates, bolts ok, bolts, members ok, members]
        val bad = ArrayList<String>(); val diag = ArrayList<String>()
        for ((e, c) in list.zip(conns)) {
            val row = e[3].jsonPrimitive.content + (if (e[0].jsonPrimitive.content == "Teh") "h" else ""); val n = cnt.getOrPut(row) { IntArray(6) }; val refs = e[4].jsonArray.map { it.jsonPrimitive.int }
            val k = kids[c.id].orEmpty().filter { it.second["kind"]?.jsonPrimitive?.content != "bolts" }.map { pts(m.doc.bodyMesh(it.first)!!) }.map(::box)
            if (row == "2h") {   // each R12 haunch part: our part with the same box (0.1 mm) and the same solid (symmetric difference <= 0.1 mm x its surface)
                val ours = kids[c.id].orEmpty().filter { it.second["kind"]?.jsonPrimitive?.content != "bolts" }.map { m.doc.bodyMesh(it.first)!! }
                for (r in refs) { n[1]++; n[5]++; val rm = r12.bodyMesh(BodyId(r.toString()))!!; val rb = box(pts(rm))
                    val hit = ours.firstOrNull { o -> val b = box(pts(o)); (0..5).all { abs(b[it] - rb[it]) <= 0.1 } }
                    if (hit == null) { bad += "row $row R12 $r: no haunch part within 0.1 mm of ${rb.map { Math.round(it * 10) / 10.0 }}; ours ${ours.map { o -> box(pts(o)).map { Math.round(it * 10) / 10.0 } }}"; continue }
                    n[0]++; val x = engine.volume(engine.subtract(rm, listOf(hit))) + engine.volume(engine.subtract(hit, listOf(rm)))
                    if (x <= 0.1 * area(rm)) n[4]++ else bad += "row $row R12 $r: solid differs by %.1f mm³".format(java.util.Locale.ROOT, x) }
                continue }
            fun nameOf(r: Int) = Json.parseToJsonElement(r12.bodyJson(BodyId(r.toString()))!!).jsonObject["st"]!!.jsonObject["name"]!!.jsonPrimitive.content.trim()
            val epId = refs.firstOrNull { r -> nameOf(r).lowercase().let { "end plate" in it || it == "plate" } } ?: refs.first { r -> nameOf(r) != "HAUNCH_PLATE" }
            for (r in refs) { n[1]++; val rb = box(pts(r12.bodyMesh(BodyId(r.toString()))!!))
                if (k.any { b -> (0..5).all { abs(b[it] - rb[it]) <= 0.1 } }) n[0]++ else bad += "row $row R12 $r: no plate within 0.1 mm of ${rb.map { Math.round(it * 10) / 10.0 }}" }
            val ours = kids[c.id].orEmpty().filter { it.second["kind"]?.jsonPrimitive?.content == "bolts" }
            for (g in tkb.filter { epId in it.getValue("parts").jsonArray.map { p -> p.jsonPrimitive.int } }) { val gq = g.getValue("q").jsonObject
                val gax = gq.getValue("ax").jsonArray.map { it.jsonPrimitive.double }; val gL = gq.getValue("L").jsonPrimitive.double; val gGrade = gq.getValue("grade").jsonPrimitive.content
                for (gp in gq.getValue("pts").jsonArray.map { p -> p.jsonArray.map { it.jsonPrimitive.double } }) { n[3]++
                    val hit = ours.firstOrNull { b -> b.second.getValue("pts").jsonArray.any { p -> val q = p.jsonArray.map { it.jsonPrimitive.double }; (0..2).all { abs(q[it] - gp[it]) <= 0.1 } } }
                    val ok = hit != null && hit.second.getValue("ax").jsonArray.map { it.jsonPrimitive.double }.zip(gax).all { (a, b) -> abs(a - b) <= 2e-3 } &&
                        abs(hit.second.getValue("L").jsonPrimitive.double - gL) < 0.5 && hit.second.getValue("grade").jsonPrimitive.content == gGrade
                    if (ok) n[2]++ else bad += "row $row R12 $epId: bolt at $gp (axis $gax, L $gL, $gGrade) not reproduced (${hit?.second?.get("L")}, ${hit?.second?.get("ax")})" } }
            val rep = box(pts(r12.bodyMesh(BodyId(epId.toString()))!!))
            val lo = D3(rep[0] - 300, rep[1] - 300, rep[2] - 300); val hi = D3(rep[3] + 300, rep[4] + 300, rep[5] + 300)
            val cube = Joints.box3(lo, D3(1.0, 0.0, 0.0), D3(0.0, 1.0, 0.0), 0.0, hi.x - lo.x, 0.0, hi.y - lo.y, 0.0, hi.z - lo.z)
            for (mi in e[1].jsonArray.map { it.jsonPrimitive.int }) { n[5]++
                val plates = members[mi].getValue("r10").jsonArray.map { r12.bodyMesh(BodyId(it.jsonPrimitive.int.toString()))!! }
                val rr = engine.intersect(engine.union(plates), cube); val oo = engine.intersect(m.doc.bodyMesh(ids.getValue(mi))!!, cube)
                val x = engine.volume(engine.subtract(rr, listOf(oo))) + engine.volume(engine.subtract(oo, listOf(rr)))
                // the same member with no connection: what differs there is the member's own section (built-up web 1.04 mm off its
                // flanges, flange plates not parallel to the axis: R12-REPORT "INPUT EQUAL, SOLID DIFFERS"), not the connection
                val o0 = engine.intersect(m.parts.mesh(members[mi].getValue("st").jsonObject), cube)
                val x0 = engine.volume(engine.subtract(rr, listOf(o0))) + engine.volume(engine.subtract(o0, listOf(rr)))
                val cut = engine.subtract(o0, listOf(oo)); val none = cut.vertexCount == 0   // nothing removed in this box (Manifold rejects an empty operand)
                val cutVol = if (none) 0.0 else engine.volume(cut); val cutInTekla = if (none) 0.0 else engine.volume(engine.intersect(cut, rr))
                val ok = x <= 0.1 * area(rr) || (x <= x0 + 1.0 && cutInTekla <= 0.1 * (if (none) 0.0 else area(cut)).coerceAtLeast(1.0))
                if (ok) n[4]++ else bad += "row $row R12 $epId: member $mi differs by %.0f mm³ (%.3f mm over its faces); with no connection %.0f; the connection removes %.0f mm³, %.0f of it inside Tekla's part".format(java.util.Locale.ROOT, x, x / area(rr), x0, cutVol, cutInTekla)
                diag += "row $row member $mi: diff %.0f (%.3f mm), no-conn %.0f, removed %.0f, removed inside Tekla %.1f".format(java.util.Locale.ROOT, x, x / area(rr), x0, cutVol, cutInTekla) }
        }
        val rep = cnt.toSortedMap().entries.joinToString("\n") { (r, n) -> "row $r (${when (r) { "2" -> "C02 eave, End plate 144"; "2h" -> "C02h Eave haunch"; "13" -> "C13 mezzanine girder, End plate 144"; else -> "C09 monitor post" }}): plates ${n[0]} / ${n[1]}, bolts ${n[2]} / ${n[3]}, member bodies ${n[4]} / ${n[5]}" } + "\n" + bad.take(40).joinToString("\n")
        File(root, "build").mkdirs(); File(root, "build/r12-c09-report.txt").writeText(rep + "\n\n" + diag.joinToString("\n") + "\n"); println(rep.lines().take(14).joinToString("\n"))
        for ((_, n) in cnt) { assertEquals(rep, n[1], n[0]); assertEquals(rep, n[3], n[2]); assertEquals(rep, n[5], n[4]) }
    }
}
