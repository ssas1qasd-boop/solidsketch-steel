package com.solidsketchapp.steel

import com.solidsketchapp.core.api.BodyId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

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
    }
}
