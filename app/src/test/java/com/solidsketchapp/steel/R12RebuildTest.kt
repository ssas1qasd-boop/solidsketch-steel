package com.solidsketchapp.steel

import com.solidsketchapp.core.api.BodyId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

/**
 * R12 (the reference since 2026-11-26, docs/steel/PARITY.md): the R12 frame rebuilt from tests/steel/golden/r12/rebuild_inputs.json
 * (tools/golden/r10_inputs.py) in the app and in web v70 (tests/steel/golden/r12/rebuild_v69.json, tools/golden/r10_rebuild.js): the grid,
 * every member in one step, every component in one step. The app's model must equal the web's body by body (AppGoldenTest.compare).
 * The app's model is saved as build/R12_app.ss (owner delivery: open it on the phone). How both compare with R12 itself:
 * docs/steel/R12-REPORT.md. Needs Manifold: -PhostJni. Writes build/r12-rebuild-report.txt.
 */
class R12RebuildTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }

    @Test fun r12RebuildEqualsWeb() {
        assumeTrue("host JNI not built (use -PhostJni)", System.getProperty("sscore.hostJni") == "1")
        val inF = File(root, "tests/steel/golden/r12/rebuild_inputs.json"); val outF = File(root, "tests/steel/golden/r12/rebuild_v69.json")
        assumeTrue(inF.exists() && outF.exists())
        val inp = Json.parseToJsonElement(inF.readText()).jsonObject; val g = Json.parseToJsonElement(outF.readText()).jsonObject
        val t = AppGoldenTest(); val m = t.build(JsonObject(emptyMap()))
        val members = inp.getValue("members").jsonArray.map { it.jsonObject.getValue("st").jsonObject }
        val ids = ArrayList<BodyId>(); val t0 = System.nanoTime()
        for (step in inp.getValue("steps").jsonArray.map { it.jsonObject }) when (step.getValue("op").jsonPrimitive.content) {
            "grid" -> m.doc.setField("stGrid", step.getValue("grid").toString())
            "addMany" -> for (i in step.getValue("from").jsonPrimitive.int until step.getValue("to").jsonPrimitive.int) ids += m.parts.addBuilt(members[i], m.parts.mesh(members[i]))
            "connectMany" -> m.comps.addMany(step.getValue("list").jsonArray.map { e -> val l = e.jsonArray
                Triple(l[0].jsonPrimitive.content, l[1].jsonArray.map { ids[it.jsonPrimitive.int] }, (l.getOrNull(2) as? JsonObject) ?: JsonObject(emptyMap())) }, merge = true)
        }
        val secs = (System.nanoTime() - t0) / 1e9
        val all = t.compare("r12", m, g)
        // known, not part of the C02 / C09 delivery: the app's Cap (39) bolt groups (row 15) are laid out unlike the web's (open item, R12-REPORT row 15)
        val t39 = m.comps.conns().filter { it.type == "T39" }.map { it.id }.toSet()
        val idsAll = m.doc.bodyIds()   // the report names bodies by their index in the model
        fun connOf(ix: String) = idsAll.getOrNull(ix.toInt())?.let { m.doc.bodyJson(it) }?.let { runCatching { Json.parseToJsonElement(it).jsonObject["st"]?.jsonObject?.get("conn")?.jsonPrimitive?.content }.getOrNull() }
        val known = all.filter { l -> Regex("^r12: body (\\d+) bolts").find(l)?.groupValues?.get(1)?.let { connOf(it) in t39 } == true }
        val bad = all - known.toSet()
        val rep = "R12 rebuild, app vs web (v69 + v70): ${m.doc.bodyIds().size} bodies vs ${g.getValue("bodies").jsonArray.size}, ${m.comps.conns().size} connections vs ${(g["conns"] as JsonArray).size}, " +
            "${bad.size} differences (+ ${known.size} known: Cap 39 bolt layout), built in ${"%.1f".format(java.util.Locale.ROOT, secs)} s\n" + (bad + known).take(200).joinToString("\n")
        File(root, "build").mkdirs(); File(root, "build/r12-rebuild-report.txt").writeText(rep + "\n"); println(rep.lines().take(30).joinToString("\n"))
        File(root, "build/R12_app.ss").outputStream().use { com.solidsketchapp.core.io.SsCodec.write(m.doc as com.solidsketchapp.core.io.MemoryDocument, it, name = "Frame_28x53m_R12_app") }
        assertTrue(bad.take(60).joinToString("\n"), bad.isEmpty())
        // weights (owner 2026-11-27: gross, no bolt holes taken off; grout 0): the app's TeklaWeight against web v70's partWeight, part by part
        val engine = com.solidsketchapp.core.view.ManifoldBooleanEngine(); val tw = com.solidsketchapp.steel.model.TeklaWeight(t.data.profiles, engine)
        val sts = idsAll.associateWith { id -> m.doc.bodyJson(id)?.let { Json.parseToJsonElement(it).jsonObject["st"] as? JsonObject } }
        fun d3(e: kotlinx.serialization.json.JsonElement?) = (e as JsonArray).map { it.jsonPrimitive.double }.let { com.solidsketchapp.steel.geom.D3(it[0], it[1], it[2]) }
        val boltsByConn = HashMap<String, MutableList<com.solidsketchapp.steel.model.TeklaWeight.Bolts>>()
        for ((_, st0) in sts) { val h = st0?.get("hole") as? JsonObject   // v70 C01: anchor rods made as parts carry their hole
            val st = if (st0 != null && h != null && st0["kind"]?.jsonPrimitive?.content == "conn") JsonObject(LinkedHashMap(h).apply { put("kind", kotlinx.serialization.json.JsonPrimitive("bolts")); put("conn", st0["conn"]!!) }) else st0
            if (st == null || st["kind"]?.jsonPrimitive?.content != "bolts" || st["pts"] == null) continue
            boltsByConn.getOrPut(st["conn"]!!.jsonPrimitive.content) { ArrayList() } += com.solidsketchapp.steel.model.TeklaWeight.Bolts((st["pts"] as JsonArray).map { d3(it) }, d3(st["ax"]),
                st["d0"]?.jsonPrimitive?.double ?: (st["d"]!!.jsonPrimitive.double + 2), st["L"]?.jsonPrimitive?.double ?: 60.0,
                (st["sl1"]?.jsonPrimitive?.doubleOrNull ?: 0.0).coerceAtLeast(0.0) + (st["sl2"]?.jsonPrimitive?.doubleOrNull ?: 0.0).coerceAtLeast(0.0), st["poly"]?.jsonPrimitive?.doubleOrNull?.toInt() ?: 0) }
        val connParts = m.comps.conns().associate { it.id to it.parts }
        val gb = g.getValue("bodies").jsonArray; var wApp = 0.0; var wWeb = 0.0; val wBad = ArrayList<String>()
        for ((i, id) in idsAll.withIndex()) { val st = sts[id] ?: continue; if (st["kind"]?.jsonPrimitive?.content == "bolts") continue
            val bs = tw.candidates(id.value, st["conn"]?.jsonPrimitive?.content, connParts, boltsByConn)
            val wr = tw.part(st, m.doc.bodyMesh(id)!!, false, { null }, bs); val w = wr.weight; val wj = gb[i].jsonObject["w"] as? JsonObject; val web = wj?.get("weight")?.jsonPrimitive?.double ?: 0.0
            wApp += w; wWeb += web; if (abs(w - web) > 0.011 && wBad.size < 20) wBad += "body $i ${st["name"]?.jsonPrimitive?.content} ${st["prof"]?.jsonPrimitive?.content}: app $w web $web" +
                (wr.subs?.let { ss -> " · app plates " + ss.joinToString { "${it.role} ${it.weight} (holes ${it.holesFilled})" } + " · web plates " + ((wj?.get("subs") as? JsonArray)?.joinToString { it.jsonObject["weight"].toString() + " (holes " + it.jsonObject["holesFilledKg"] + ")" } ?: "-") + " · bolts ${bs.size}" + (ss.firstOrNull { it.holesFilled > 0 && it.role == "w" }?.mesh?.let { wm -> " · web holes from " + bs.filter { b -> tw.holeFill(wm, listOf(b)) > 0 }.joinToString { b -> "pts0 ${b.pts[0]} ax ${b.ax} d0 ${b.d0} L ${b.len} n ${b.pts.size}" } } ?: "") } ?: "") }
        val wrep = "R12 rebuild weight (gross, grout 0): app %.2f kg, web %.2f kg, Tekla st.tkW 63,705.82 kg, Part List 63,711.7 kg".format(java.util.Locale.ROOT, wApp, wWeb)
        File(root, "build/r12-rebuild-weight.txt").writeText(wrep + "\n" + wBad.joinToString("\n") + "\n"); println(wrep)
        assertTrue(wrep + "\n" + wBad.joinToString("\n"), wBad.isEmpty())
    }
}
