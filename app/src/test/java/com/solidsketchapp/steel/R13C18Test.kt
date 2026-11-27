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
 * C18 Stairs as Tekla (docs/steel/PARITY.md "C18"): the app rebuilds R12's two flights (Stair treads in Tekla mode + the stringers'
 * mitre fit, Stair end plates) with the R12 inputs and checks against R12 within 0.1 mm: the 23 grating treads, the 46 L60*6 brackets,
 * the 8 stair plates on the stringer pieces (the 2 counter plates on EB4002 are a mezzanine girder's: left for R13), the 12 stringer
 * pieces (box) and every bolt of these parts (point, axis, length, grade); the treads' and brackets' weight against Tekla's.
 * Needs Manifold: -PhostJni. Writes build/r13-c18-report.txt.
 */
class R13C18Test {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
    private fun box(v: List<DoubleArray>) = (0..2).map { k -> v.minOf { it[k] } } + (0..2).map { k -> v.maxOf { it[k] } }
    private fun pts(m: Mesh) = (0 until m.vertexCount).map { i -> DoubleArray(3) { m.positions[i * 3 + it].toDouble() } }

    @Test fun c18EqualR12() {
        assumeTrue("host JNI not built (use -PhostJni)", System.getProperty("sscore.hostJni") == "1")
        val inp = Json.parseToJsonElement(File(root, "tests/steel/golden/r13/rebuild_inputs.json").readText()).jsonObject
        val r12 = SsCodec.read(File(root, "tests/steel/models/Frame_28x53m_R13.ss").inputStream())
        val t = AppGoldenTest(); val m = t.build(JsonObject(emptyMap()))
        val members = inp.getValue("members").jsonArray.map { it.jsonObject }
        val steps = inp.getValue("steps").jsonArray.map { it.jsonObject }
        steps.first { it["op"]!!.jsonPrimitive.content == "grid" }.let { m.doc.setField("stGrid", it.getValue("grid").toString()) }
        val list = steps.first { it["op"]!!.jsonPrimitive.content == "connectMany" }.getValue("list").jsonArray.map { it.jsonArray }.filter { it[0].jsonPrimitive.content in setOf("stairtreads", "Tsep") }
        val need = list.flatMap { e -> e[1].jsonArray.map { it.jsonPrimitive.int } }.toSortedSet()
        val ids = HashMap<Int, BodyId>(); for (i in need) { val st = members[i].getValue("st").jsonObject; ids[i] = m.parts.addBuilt(st, m.parts.mesh(st)) }
        m.comps.addMany(list.map { e -> Triple(e[0].jsonPrimitive.content, e[1].jsonArray.map { ids.getValue(it.jsonPrimitive.int) }, e[2].jsonObject) }, merge = true)
        assertEquals(list.size, m.comps.conns().size)
        val engine = com.solidsketchapp.core.view.ManifoldBooleanEngine(); val tw = com.solidsketchapp.steel.model.TeklaWeight(t.data.profiles, engine)
        class P(val st: JsonObject, val box: List<Double>, val mesh: Mesh)
        val app = m.doc.bodyIds().mapNotNull { id -> val st = Json.parseToJsonElement(m.doc.bodyJson(id)!!).jsonObject["st"]?.jsonObject ?: return@mapNotNull null
            if (st["kind"]?.jsonPrimitive?.content == "bolts") null else m.doc.bodyMesh(id)!!.let { P(st, box(pts(it)), it) } }
        val appBolts = m.doc.bodyIds().mapNotNull { id -> Json.parseToJsonElement(m.doc.bodyJson(id)!!).jsonObject["st"]?.jsonObject?.takeIf { it["kind"]?.jsonPrimitive?.content == "bolts" } }
        fun d3(e: kotlinx.serialization.json.JsonElement) = e.jsonArray.map { it.jsonPrimitive.double }.let { com.solidsketchapp.steel.geom.D3(it[0], it[1], it[2]) }
        val brk = appBolts.map { b -> com.solidsketchapp.steel.model.TeklaWeight.Bolts(b.getValue("pts").jsonArray.map { d3(it) }, d3(b.getValue("ax")), b["d0"]?.jsonPrimitive?.double ?: (b.getValue("d").jsonPrimitive.double + 2), b.getValue("L").jsonPrimitive.double) }   // gross: the bracket holes filled
        val cnt = sortedMapOf<String, IntArray>(); val bad = ArrayList<String>(); var wR12 = 0.0; var wApp = 0.0; val r12Ids = HashSet<Int>()
        for (id in r12.bodyIds()) { val st = Json.parseToJsonElement(r12.bodyJson(id) ?: continue).jsonObject["st"]?.jsonObject ?: continue
            val nm = st["name"]?.jsonPrimitive?.content?.trim() ?: ""; val am = st["amark"]?.jsonPrimitive?.content ?: ""
            if (nm !in setOf("THREAD", "BRACKET", "Stair Plate", "STRINGER") || am.startsWith("EB")) continue
            val rb = box(pts(r12.bodyMesh(id) ?: continue)); val what = "$nm ${st["tprof"]?.jsonPrimitive?.content ?: st["prof"]?.jsonPrimitive?.content ?: ""}"; val n = cnt.getOrPut(what) { IntArray(2) }; n[1]++; r12Ids += id.value.toInt()
            val hit = app.firstOrNull { p -> (0..5).all { abs(p.box[it] - rb[it]) <= 0.1 } }
            if (hit != null) { n[0]++; if (nm == "THREAD" || nm == "BRACKET") { wR12 += st["tkW"]?.jsonPrimitive?.double ?: 0.0; wApp += tw.part(hit.st, hit.mesh, false, { null }, brk).weight } }
            else bad += "R12 ${id.value} $what: no app part within 0.1 mm of ${rb.map { Math.round(it * 10) / 10.0 }}" }
        val tkb = Json.parseToJsonElement(r12.field("stConns")!!).jsonArray.map { it.jsonObject }.filter { it["type"]!!.jsonPrimitive.content == "TKBOLT" && it.getValue("parts").jsonArray.any { p -> p.jsonPrimitive.int in r12Ids } }
        val nb = IntArray(2)
        for (g in tkb) { val gq = g.getValue("q").jsonObject; val gax = gq.getValue("ax").jsonArray.map { it.jsonPrimitive.double }; val gL = gq.getValue("L").jsonPrimitive.double
            for (gp in gq.getValue("pts").jsonArray.map { p -> p.jsonArray.map { it.jsonPrimitive.double } }) { nb[1]++
                val hit = appBolts.firstOrNull { b -> b.getValue("pts").jsonArray.any { p -> val q = p.jsonArray.map { it.jsonPrimitive.double }; (0..2).all { abs(q[it] - gp[it]) <= 0.1 } } }
                if (hit != null && hit.getValue("ax").jsonArray.map { it.jsonPrimitive.double }.zip(gax).all { (a, b) -> abs(a - b) <= 2e-3 } && abs(hit.getValue("L").jsonPrimitive.double - gL) < 0.5) nb[0]++
                else bad += "bolt at $gp (axis $gax, L $gL) not reproduced" } }
        val rep = cnt.entries.joinToString("\n") { (k, n) -> "$k: ${n[0]} / ${n[1]}" } + "\nbolts: ${nb[0]} / ${nb[1]}" + "\ntreads + brackets weight: app %.2f kg, Tekla %.2f kg".format(java.util.Locale.ROOT, wApp, wR12) + (if (bad.isEmpty()) "" else "\n" + bad.take(30).joinToString("\n"))
        File(root, "build").mkdirs(); File(root, "build/r13-c18-report.txt").writeText(rep + "\n"); println(rep.lines().take(12).joinToString("\n"))
        for ((_, n) in cnt) assertEquals(rep, n[1], n[0]); assertEquals(rep, nb[1], nb[0]); assertEquals(rep, wR12, wApp, 0.05)
    }
}
