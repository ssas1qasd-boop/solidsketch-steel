package com.solidsketchapp.steel

import com.solidsketchapp.core.io.MemoryDocument
import com.solidsketchapp.core.view.ManifoldBooleanEngine
import com.solidsketchapp.steel.comp.Components
import com.solidsketchapp.steel.comp.MemoryStore
import com.solidsketchapp.steel.comp.TkCheck
import com.solidsketchapp.steel.comp.TkUi
import com.solidsketchapp.steel.conn.StRegen
import com.solidsketchapp.steel.data.SteelData
import com.solidsketchapp.steel.geom.StBuild
import com.solidsketchapp.steel.model.StParts
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
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.math.abs

/**
 * S7 acceptance (rule checks and symbols): v69's tkStatus on End plate 144 in 8 cases (OK; bolt edge distance; bolt
 * spacing; weld too big; bolts outside the plates; plate overlapping the column; sloped OK; sloped with two warnings):
 * the same status (OK / warning / error), the same messages word for word, and the symbol at the same place (stSymPos).
 */
class S7StatusTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
    private val data by lazy { SteelData.load { File(root, "steel/src/main/assets/steel/$it").readText() } }
    private val ui by lazy { TkUi.parse(File(root, "steel/src/main/assets/steel/tk_ui.json").readText()) }
    private val engine by lazy { ManifoldBooleanEngine() }
    @Before fun needsHostLib() = assumeTrue("host JNI not built (use -PhostJni)", System.getProperty("sscore.hostJni") == "1")

    @Test fun statusEqualsV69() {
        val gold = Json.parseToJsonElement(File(root, "tests/steel/golden/s7/status.json").readText()).jsonObject.getValue("cases").jsonObject
        val bad = mutableListOf<String>()
        for ((name, g0) in gold) { val g = g0.jsonObject
            val doc = MemoryDocument(); val build = StBuild(data.profiles, union = { engine.union(it) })
            val parts = StParts(data, doc, null, build); val regen = StRegen(build, engine, data.profiles, data.tk)
            val ids = g.getValue("members").jsonArray.map { m -> val (set, o) = m.jsonArray
                parts.add(JsonObject(LinkedHashMap<String, JsonElement>().apply { put("kind", JsonPrimitive("member")); putAll(data.propertySet(set.jsonPrimitive.content)!!.raw); putAll(o.jsonObject) }), "m") }
            val comps = Components(ui, doc, null, MemoryStore(), regen); val k = g.getValue("k").jsonPrimitive.content
            val c = comps.add(k, ids, JsonObject(LinkedHashMap(comps.cq(k)).apply { putAll(g.getValue("q").jsonObject) }))
            val chk = TkCheck(comps, doc, engine, data.profiles); val r = chk.status(c)
            val gs = g.getValue("status").jsonPrimitive.int; val gm = g.getValue("msgs").jsonArray.map { it.jsonPrimitive.content }
            if (r.s != gs) bad += "$name: status ${r.s} vs $gs"
            if (r.msgs != gm) bad += "$name: messages\n  app  ${r.msgs}\n  v69  $gm"
            val p = chk.symPos(c); val gp = g.getValue("symPos").jsonArray.map { it.jsonPrimitive.double }
            if (p == null || abs(p.x - gp[0]) > 0.1 || abs(p.y - gp[1]) > 0.1 || abs(p.z - gp[2]) > 0.1) bad += "$name: symbol at $p vs $gp"
        }
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
    }
}
