package com.solidsketchapp.steel

import com.solidsketchapp.core.api.BodyId
import com.solidsketchapp.core.io.MemoryDocument
import com.solidsketchapp.core.view.ManifoldBooleanEngine
import com.solidsketchapp.steel.comp.CompDialog
import com.solidsketchapp.steel.comp.Components
import com.solidsketchapp.steel.comp.Js
import com.solidsketchapp.steel.comp.MemoryStore
import com.solidsketchapp.steel.comp.TkUi
import com.solidsketchapp.steel.conn.StRegen
import com.solidsketchapp.steel.data.SteelData
import com.solidsketchapp.steel.geom.StBuild
import com.solidsketchapp.steel.model.StParts
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
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
 * S7 acceptance (dialog): v69's live component dialog driven through its own DOM (tools/golden/s7_dialog.js): values
 * typed into the picture's boxes (incl. the negated "1"/"2" and the gauge / pitch lists), pickers, an unticked row, the
 * Parts and Welds tables, then Modify or Apply + a new component. The app's dialog does the same steps: the component's
 * settings (c.q), welds, plates and bolt group must equal v69's. Manifold on the build machine: -PhostJni.
 */
class S7DialogTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
    private val data by lazy { SteelData.load { File(root, "steel/src/main/assets/steel/$it").readText() } }
    private val ui by lazy { TkUi.parse(File(root, "steel/src/main/assets/steel/tk_ui.json").readText()) }
    private val engine by lazy { ManifoldBooleanEngine() }
    @Before fun needsHostLib() = assumeTrue("host JNI not built (use -PhostJni)", System.getProperty("sscore.hostJni") == "1")

    /** Canonical JSON (sorted keys, JavaScript number text) for comparing settings objects. */
    private fun canon(e: JsonElement?): String = when (e) {
        null -> "undefined"; is JsonNull -> "null"; is JsonObject -> e.keys.sorted().joinToString(",", "{", "}") { "$it:${canon(e[it])}" }
        is JsonArray -> e.joinToString(",", "[", "]") { canon(it) }; is JsonPrimitive -> if (e.isString) "\"${e.content}\"" else Js.str(e)
    }

    @Test fun dialogEqualsV69() {
        val gold = Json.parseToJsonElement(File(root, "tests/steel/golden/s7/dialog.json").readText()).jsonObject.getValue("cases").jsonObject
        val bad = mutableListOf<String>()
        for ((name, g0) in gold) { val g = g0.jsonObject; val k = g.getValue("k").jsonPrimitive.content
            val doc = MemoryDocument(); val build = StBuild(data.profiles, union = { engine.union(it) })
            val regen = StRegen(build, engine, data.profiles, data.tk); val parts = StParts(data, doc, null, build)
            val ids = g.getValue("members").jsonArray.map { m -> val (set, o) = m.jsonArray
                parts.add(JsonObject(LinkedHashMap<String, JsonElement>().apply { put("kind", JsonPrimitive("member")); putAll(data.propertySet(set.jsonPrimitive.content)!!.raw); putAll(o.jsonObject) }), "m") }
            val comps = Components(ui, doc, null, MemoryStore(), regen)
            val modify = g.getValue("action").jsonPrimitive.content == "Modify"
            var conn = if (modify) comps.add(k, ids, comps.cq(k)) else null
            val d = CompDialog(comps, k, conn)
            for (st in g.getValue("steps").jsonArray) { val (tab, kind, lbl, v) = st.jsonArray; val t = d.spec.tabs.first { it.name == tab.jsonPrimitive.content }; val l = lbl.jsonPrimitive.content
                val input = when (kind.jsonPrimitive.content) {
                    "dim" -> d.picFields(t).first { it.lbl == l }.let { f -> d.inputs.first { it.f === f } }
                    "row", "off" -> t.groups.flatMap { it.second }.first { it.lbl == l }.let { f -> d.inputs.first { it.f === f } }
                    "weld" -> d.inputs.first { it.f.k == "W.$l.a" }
                    else -> l.split(".").let { (role, x) -> d.inputs.first { it.f.k == "P.$role.$x" } }
                }
                when { kind.jsonPrimitive.content == "off" -> input.on = false
                    input.f.type == "sel" || input.f.type == "chk" -> input.value = v
                    else -> d.setText(input, Js.str(v)) } }
            if (modify) d.modify(false) else { d.apply(); conn = comps.add(k, ids, comps.cq(k)) }
            val c2 = comps.conn(conn!!.id)!!
            if (canon(c2.q) != canon(g["q"])) bad += "$name: q\n  app  ${canon(c2.q)}\n  v69  ${canon(g["q"])}"
            val ow = (c2.raw["welds"] as? JsonArray ?: JsonArray(emptyList())).map { w -> w.jsonObject.let { "${Js.str(it["name"])}|${Js.str(it["a"])}|${Js.str(it["len"])}|${Js.str(it["around"])}|${Js.str(it["type"])}|${Js.str(it["ws"])}" } }
            val gw = g.getValue("welds").jsonArray.map { w -> w.jsonObject.let { "${Js.str(it["name"])}|${Js.str(it["a"])}|${Js.str(it["len"])}|${Js.str(it["around"])}|${Js.str(it["type"])}|${Js.str(it["ws"])}" } }
            if (ow != gw) bad += "$name: welds $ow vs $gw"
            val kids = doc.bodyIds().mapNotNull { id -> doc.bodyJson(id)?.let { Json.parseToJsonElement(it).jsonObject["st"]?.jsonObject } }.filter { Js.str(it["conn"]) == c2.id }
            val op = kids.filter { Js.str(it["kind"]) == "conn" }.map { "${Js.str(it["name"])}|${Js.str(it["prof"])}|${Js.str(it["mat"])}|${Js.str(it["cls"])}" }
            val gp = g.getValue("plates").jsonArray.map { p -> p.jsonObject.let { "${Js.str(it["name"])}|${Js.str(it["prof"])}|${Js.str(it["mat"])}|${Js.str(it["cls"])}" } }
            if (op != gp) bad += "$name: plates $op vs $gp"
            val b = kids.firstOrNull { Js.str(it["kind"]) == "bolts" }; val gb = g["bolts"] as? JsonObject
            if ((b == null) != (gb == null)) bad += "$name: bolts ${b != null} vs ${gb != null}" else if (b != null && gb != null) {
                for (f in listOf("n", "d", "L")) if (Js.str(b[f]) != Js.str(gb[f])) bad += "$name: bolts $f ${b[f]} vs ${gb[f]}"
                val p1 = b.getValue("pts").jsonArray.map { p -> p.jsonArray.map { it.jsonPrimitive.double } }; val p2 = gb.getValue("pts").jsonArray.map { p -> p.jsonArray.map { it.jsonPrimitive.double } }
                if (p1.size != p2.size || p1.indices.any { i -> (0..2).any { abs(p1[i][it] - p2[i][it]) > 0.1 } }) bad += "$name: bolt points $p1 vs $p2" }
        }
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
    }
}
