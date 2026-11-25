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
 * S10.6: the R10 frame rebuilt with the same inputs in the app and in v69 (tests/steel/golden/r10/rebuild_inputs.json,
 * tools/golden/r10_inputs.py; v69's result tests/steel/golden/r10/rebuild_v69.json, tools/golden/r10_rebuild.js): the grid,
 * the 835 members in one step (v69 stAddMany), the 1 272 components in one step with one regen (v69 stConnectMany).
 * The app's model must equal v69's body by body (AppGoldenTest.compare: kind, name, volume, box, every member field, plates'
 * fields and owners, bolt groups, connections with their q and welds). How both compare with R10 itself:
 * docs/steel/R10-REPORT.md (tools/golden/r10_compare.py). Needs Manifold: -PhostJni. Writes build/r10-rebuild-report.txt.
 */
class R10RebuildTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }

    @Test fun r10RebuildEqualsV69() {
        assumeTrue("host JNI not built (use -PhostJni)", System.getProperty("sscore.hostJni") == "1")
        val inF = File(root, "tests/steel/golden/r10/rebuild_inputs.json"); val outF = File(root, "tests/steel/golden/r10/rebuild_v69.json")
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
        val bad = t.compare("r10", m, g)
        val rep = "R10 rebuild, app vs v69: ${m.doc.bodyIds().size} bodies vs ${g.getValue("bodies").jsonArray.size}, ${m.comps.conns().size} connections vs ${(g["conns"] as JsonArray).size}, " +
            "${bad.size} differences, built in ${"%.1f".format(java.util.Locale.ROOT, secs)} s\n" + bad.take(200).joinToString("\n")
        File(root, "build").mkdirs(); File(root, "build/r10-rebuild-report.txt").writeText(rep + "\n"); println(rep.lines().take(30).joinToString("\n"))
        assertTrue(bad.take(60).joinToString("\n"), bad.isEmpty())
    }
}
