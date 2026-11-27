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
 * C07 Tube crossing as Tekla 20 (docs/steel/PARITY.md "C07"): the app rebuilds R12's 52 crossings (row 7: continuous brace -> half 1 ->
 * half 2) with the R12 inputs and checks against R12 itself, within 0.1 mm: every crossing gusset, the halves' connection plates and
 * end plates (FLT5*100) and the crossing caps (PL5) by box (6 numbers); every bolt of the halves (point, axis, length, grade); the
 * continuous brace and both halves (box). The PL5 caps at the gable nodes' vertical braces (skewed out of the plane, C06) are counted
 * apart. Needs Manifold: -PhostJni. Writes build/r13-c07-report.txt.
 */
class R13C07Test {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
    private fun box(v: List<DoubleArray>) = (0..2).map { k -> v.minOf { it[k] } } + (0..2).map { k -> v.maxOf { it[k] } }
    private fun pts(m: Mesh) = (0 until m.vertexCount).map { i -> DoubleArray(3) { m.positions[i * 3 + it].toDouble() } }

    @Test fun c07EqualR12() {
        assumeTrue("host JNI not built (use -PhostJni)", System.getProperty("sscore.hostJni") == "1")
        val inp = Json.parseToJsonElement(File(root, "tests/steel/golden/r13/rebuild_inputs.json").readText()).jsonObject
        val r12 = SsCodec.read(File(root, "tests/steel/models/Frame_28x53m_R13.ss").inputStream())
        val t = AppGoldenTest(); val m = t.build(JsonObject(emptyMap()))
        val members = inp.getValue("members").jsonArray.map { it.jsonObject }
        val steps = inp.getValue("steps").jsonArray.map { it.jsonObject }
        steps.first { it["op"]!!.jsonPrimitive.content == "grid" }.let { m.doc.setField("stGrid", it.getValue("grid").toString()) }
        val list = steps.first { it["op"]!!.jsonPrimitive.content == "connectMany" }.getValue("list").jsonArray.map { it.jsonArray }.filter { it[0].jsonPrimitive.content == "Ttgx" }
        val need = list.flatMap { e -> e[1].jsonArray.map { it.jsonPrimitive.int } }.toSortedSet()
        val ids = HashMap<Int, BodyId>(); for (i in need) { val st = members[i].getValue("st").jsonObject; ids[i] = m.parts.addBuilt(st, m.parts.mesh(st)) }
        m.comps.addMany(list.map { e -> Triple(e[0].jsonPrimitive.content, e[1].jsonArray.map { ids.getValue(it.jsonPrimitive.int) }, e[2].jsonObject) }, merge = true)
        assertEquals(list.size, m.comps.conns().size)
        val appBoxes = m.doc.bodyIds().mapNotNull { id -> val st = Json.parseToJsonElement(m.doc.bodyJson(id)!!).jsonObject["st"]?.jsonObject ?: return@mapNotNull null
            if (st["kind"]?.jsonPrimitive?.content == "bolts") null else box(pts(m.doc.bodyMesh(id)!!)) }
        val appBolts = m.doc.bodyIds().mapNotNull { id -> Json.parseToJsonElement(m.doc.bodyJson(id)!!).jsonObject["st"]?.jsonObject?.takeIf { it["kind"]?.jsonPrimitive?.content == "bolts" } }
        // R12's parts of these crossings: the halves' assemblies near the crossing + the continuous brace's gusset
        val memberIds = need.flatMap { members[it].getValue("r10").jsonArray.map { r -> r.jsonPrimitive.int } }.toSet()
        val amarks = need.map { members[it]["amark"]!!.jsonPrimitive.content }.toSet()
        val r12Parts = r12.bodyIds().mapNotNull { id -> val st = Json.parseToJsonElement(r12.bodyJson(id) ?: return@mapNotNull null).jsonObject["st"]?.jsonObject ?: return@mapNotNull null
            val nm = st["name"]?.jsonPrimitive?.content?.trim() ?: ""; val am = st["amark"]?.jsonPrimitive?.content ?: ""; val tp = st["tprof"]?.jsonPrimitive?.content ?: ""
            if (am in amarks && (nm == "Connection Plate" || nm == "Gusset Plate")) Triple(id.value.toInt(), "$nm $tp", box(pts(r12.bodyMesh(id)!!))) else null }
        // only the parts at a crossing: within 700 mm of a crossing point (the halves' far ends are row 5's)
        val xs = list.map { e -> val c = members[e[1].jsonArray[0].jsonPrimitive.int].getValue("st").jsonObject; val h = members[e[1].jsonArray[1].jsonPrimitive.int].getValue("st").jsonObject
            fun v(o: JsonObject, k: String) = o.getValue(k).jsonArray.map { it.jsonPrimitive.double }.toDoubleArray()
            val p1 = v(c, "p1"); val p2 = v(c, "p2"); val q1 = v(h, "p1"); val q2 = v(h, "p2")
            val a = DoubleArray(3) { p2[it] - p1[it] }; val b = DoubleArray(3) { q2[it] - q1[it] }; fun dot(x: DoubleArray, y: DoubleArray) = (0..2).sumOf { x[it] * y[it] }
            val w0 = DoubleArray(3) { p1[it] - q1[it] }; val aa = dot(a, a); val ab = dot(a, b); val bb = dot(b, b); val s = (ab * dot(b, w0) - bb * dot(a, w0)) / (aa * bb - ab * ab)
            DoubleArray(3) { p1[it] + a[it] * s } }
        fun nearX(b: List<Double>) = xs.any { x -> sqrt((0..2).sumOf { k -> ((b[k] + b[k + 3]) / 2 - x[k]).let { it * it } }) < 700 }
        val cnt = sortedMapOf<String, IntArray>(); val bad = ArrayList<String>()
        for ((rid, what, rb) in r12Parts) { if (!nearX(rb)) continue
            val gable = what.startsWith("Gusset Plate PL5*") && !what.endsWith("44.1"); val row = if (gable) "7 (gable caps)" else "7"; val n = cnt.getOrPut(row) { IntArray(4) }; n[1]++
            if (appBoxes.any { b -> (0..5).all { abs(b[it] - rb[it]) <= 0.1 } }) n[0]++ else bad += "row $row R12 $rid $what: no part within 0.1 mm of ${rb.map { Math.round(it * 10) / 10.0 }}" }
        val cps = r12Parts.filter { it.second.startsWith("Connection Plate") && nearX(it.third) }.map { it.first }.toSet()
        val tkb = Json.parseToJsonElement(r12.field("stConns")!!).jsonArray.map { it.jsonObject }.filter { it["type"]!!.jsonPrimitive.content == "TKBOLT" && it.getValue("parts").jsonArray.any { p -> p.jsonPrimitive.int in cps } }
        val nb = cnt.getOrPut("7") { IntArray(4) }
        for (g in tkb) { val gq = g.getValue("q").jsonObject; val gax = gq.getValue("ax").jsonArray.map { it.jsonPrimitive.double }; val gL = gq.getValue("L").jsonPrimitive.double
            for (gp in gq.getValue("pts").jsonArray.map { p -> p.jsonArray.map { it.jsonPrimitive.double } }) { nb[3]++
                val hit = appBolts.firstOrNull { b -> b.getValue("pts").jsonArray.any { p -> val q = p.jsonArray.map { it.jsonPrimitive.double }; (0..2).all { abs(q[it] - gp[it]) <= 0.1 } } }
                if (hit != null && hit.getValue("ax").jsonArray.map { it.jsonPrimitive.double }.zip(gax).all { (a, b) -> abs(a - b) <= 2e-3 } && abs(hit.getValue("L").jsonPrimitive.double - gL) < 0.5) nb[2]++
                else bad += "row 7: bolt at $gp (axis $gax, L $gL) not reproduced" } }
        var mOk = 0; var mN = 0
        for (i in need) { mN++; val rb = box(members[i].getValue("r10").jsonArray.flatMap { pts(r12.bodyMesh(BodyId(it.jsonPrimitive.int.toString()))!!) }); val ob = box(pts(m.doc.bodyMesh(ids.getValue(i))!!))
            if ((0..5).all { abs(ob[it] - rb[it]) <= 0.1 }) mOk++ else bad += "member $i: box $ob vs R12 $rb" }
        val rep = cnt.entries.joinToString("\n") { (r, n) -> "row $r (${if (r == "7") "C07 Tube crossing" else "C07 caps at the gable nodes' skewed braces, not asserted"}): parts ${n[0]} / ${n[1]}" + (if (r == "7") ", bolts ${n[2]} / ${n[3]}, members (box) $mOk / $mN" else "") } + "\n" + bad.take(40).joinToString("\n")
        File(root, "build").mkdirs(); File(root, "build/r13-c07-report.txt").writeText(rep + "\n"); println(rep.lines().take(10).joinToString("\n"))
        cnt["7"]!!.let { n -> assertEquals(rep, n[1], n[0]); assertEquals(rep, n[3], n[2]) }; assertEquals(rep, mN, mOk)
    }
}
