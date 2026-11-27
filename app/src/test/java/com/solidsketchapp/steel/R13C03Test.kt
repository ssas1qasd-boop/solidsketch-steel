package com.solidsketchapp.steel

import com.solidsketchapp.core.api.BodyId
import com.solidsketchapp.core.api.Mesh
import com.solidsketchapp.core.io.SsCodec
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

/**
 * C03 Apex haunch as Tekla 106 (docs/steel/PARITY.md "C03"): the app rebuilds R12's 24 apexes (row 3) with the R12 inputs and checks every
 * R12 part of them within 0.1 mm (box, 6 numbers): the end plates FLT20*400, the haunch webs FLT8*130 and flanges FLT10*150 (ends plumb),
 * the end plate stiffeners FLT10*60; and their weight against Tekla's (st.tkW). Needs Manifold: -PhostJni. Writes build/r13-c03-report.txt.
 */
class R13C03Test {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
    private fun box(v: List<DoubleArray>) = (0..2).map { k -> v.minOf { it[k] } } + (0..2).map { k -> v.maxOf { it[k] } }
    private fun pts(m: Mesh) = (0 until m.vertexCount).map { i -> DoubleArray(3) { m.positions[i * 3 + it].toDouble() } }

    @Test fun c03EqualR12() {
        assumeTrue("host JNI not built (use -PhostJni)", System.getProperty("sscore.hostJni") == "1")
        val inp = Json.parseToJsonElement(File(root, "tests/steel/golden/r13/rebuild_inputs.json").readText()).jsonObject
        val r12 = SsCodec.read(File(root, "tests/steel/models/Frame_28x53m_R13.ss").inputStream())
        val t = AppGoldenTest(); val m = t.build(JsonObject(emptyMap()))
        val members = inp.getValue("members").jsonArray.map { it.jsonObject }
        val steps = inp.getValue("steps").jsonArray.map { it.jsonObject }
        steps.first { it["op"]!!.jsonPrimitive.content == "grid" }.let { m.doc.setField("stGrid", it.getValue("grid").toString()) }
        val list = steps.first { it["op"]!!.jsonPrimitive.content == "connectMany" }.getValue("list").jsonArray.map { it.jsonArray }.filter { it[0].jsonPrimitive.content == "apex" && it[3].jsonPrimitive.content == "3" }
        val need = list.flatMap { e -> e[1].jsonArray.map { it.jsonPrimitive.int } }.toSortedSet()
        val ids = HashMap<Int, BodyId>(); for (i in need) { val st = members[i].getValue("st").jsonObject; ids[i] = m.parts.addBuilt(st, m.parts.mesh(st)) }
        m.comps.addMany(list.map { e -> Triple(e[0].jsonPrimitive.content, e[1].jsonArray.map { ids.getValue(it.jsonPrimitive.int) }, e[2].jsonObject) }, merge = true)
        assertEquals(list.size, m.comps.conns().size)
        val engine = com.solidsketchapp.core.view.ManifoldBooleanEngine(); val tw = com.solidsketchapp.steel.model.TeklaWeight(t.data.profiles, engine)
        class P(val st: JsonObject, val box: List<Double>, val vol: Double, val mesh: Mesh)
        val app = m.doc.bodyIds().mapNotNull { id -> val st = Json.parseToJsonElement(m.doc.bodyJson(id)!!).jsonObject["st"]?.jsonObject ?: return@mapNotNull null
            if (st["kind"]?.jsonPrimitive?.content != "conn") null else m.doc.bodyMesh(id)!!.let { P(st, box(pts(it)), abs(engine.volume(it)), it) } }
        // the app's rod holes (gross weight: the base and cast plates weighed without them)
        val holes = app.mapNotNull { p -> (p.st["hole"] as? JsonObject)?.let { h -> com.solidsketchapp.steel.model.TeklaWeight.Bolts(h.getValue("pts").jsonArray.map { q -> q.jsonArray.map { it.jsonPrimitive.double }.let { com.solidsketchapp.steel.geom.D3(it[0], it[1], it[2]) } },
            h.getValue("ax").jsonArray.map { it.jsonPrimitive.double }.let { com.solidsketchapp.steel.geom.D3(it[0], it[1], it[2]) }, h.getValue("d0").jsonPrimitive.double, h.getValue("L").jsonPrimitive.double, 0.0, h["poly"]?.jsonPrimitive?.double?.toInt() ?: 0) } }
        val boltSts = m.doc.bodyIds().mapNotNull { id -> Json.parseToJsonElement(m.doc.bodyJson(id)!!).jsonObject["st"]?.jsonObject?.takeIf { it["kind"]?.jsonPrimitive?.content == "bolts" } }
        fun d3(e: kotlinx.serialization.json.JsonElement) = e.jsonArray.map { it.jsonPrimitive.double }.let { com.solidsketchapp.steel.geom.D3(it[0], it[1], it[2]) }
        val allHoles = holes + boltSts.map { b -> com.solidsketchapp.steel.model.TeklaWeight.Bolts(b.getValue("pts").jsonArray.map { d3(it) }, d3(b.getValue("ax")), b["d0"]?.jsonPrimitive?.double ?: (b.getValue("d").jsonPrimitive.double + 2), b.getValue("L").jsonPrimitive.double) }   // gross: the end plates' bolt holes filled
        val apexes = list.map { e -> val st = members[e[1].jsonArray[0].jsonPrimitive.int].getValue("st").jsonObject; val o = members[e[1].jsonArray[1].jsonPrimitive.int].getValue("st").jsonObject
            fun v(j: JsonObject, k: String) = j.getValue(k).jsonArray.map { it.jsonPrimitive.double }
            listOf(v(st, "p1"), v(st, "p2")).minBy { p -> listOf(v(o, "p1"), v(o, "p2")).minOf { q -> (0..2).sumOf { (p[it] - q[it]) * (p[it] - q[it]) } } } }
        val names = setOf("End Plate", "Web", "Bottom flange", "Stiff.")
        val cnt = sortedMapOf<String, IntArray>(); val bad = ArrayList<String>(); var wR12 = 0.0; var wApp = 0.0
        for (id in r12.bodyIds()) { val st = Json.parseToJsonElement(r12.bodyJson(id) ?: continue).jsonObject["st"]?.jsonObject ?: continue
            val nm = st["name"]?.jsonPrimitive?.content?.trim() ?: ""; if (nm !in names) continue
            val rv = pts(r12.bodyMesh(id) ?: continue); val rb = box(rv); val c = (0..2).map { (rb[it] + rb[it + 3]) / 2 }
            val tp = st["tprof"]?.jsonPrimitive?.content ?: ""; if (tp !in setOf("FLT20*400", "FLT8*130", "FLT10*150", "FLT10*60") || (0..2).maxOf { rb[it + 3] - rb[it] } > 2100) continue
            if (apexes.none { b -> abs(b[0] - c[0]) < 1100 && abs(b[1] - c[1]) < 300 && abs(b[2] - c[2]) < 700 }) continue
            val what = "$nm ${st["tprof"]?.jsonPrimitive?.content ?: ""}"; val n = cnt.getOrPut(what) { IntArray(2) }; n[1]++
            val hit = app.firstOrNull { p -> (0..5).all { abs(p.box[it] - rb[it]) <= 0.1 } }
            val volOk = hit != null && abs(hit.vol / abs(engine.volume(r12.bodyMesh(id)!!)) - 1) < 1e-2
            if (hit != null && volOk) n[0]++ else bad += "R12 ${id.value} $what: ${if (hit == null) "no app part within 0.1 mm of ${rb.map { Math.round(it * 10) / 10.0 }}" else "volume ${hit.vol}"}"
            if (hit != null) { wR12 += st["tkW"]?.jsonPrimitive?.double ?: 0.0; wApp += tw.part(hit.st, hit.mesh, false, { null }, allHoles).weight } }
        val rep = cnt.entries.joinToString("\n") { (k, n) -> "$k: ${n[0]} / ${n[1]}" } + "\nweight of these parts: app %.2f kg, Tekla %.2f kg".format(java.util.Locale.ROOT, wApp, wR12) + (if (bad.isEmpty()) "" else "\n" + bad.take(30).joinToString("\n"))
        File(root, "build").mkdirs(); File(root, "build/r13-c03-report.txt").writeText(rep + "\n"); println(rep.lines().take(14).joinToString("\n"))
        for ((_, n) in cnt) assertEquals(rep, n[1], n[0]); assertEquals(rep, wR12, wApp, 0.05)
    }
}
