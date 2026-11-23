package com.solidsketchapp.steel

import com.solidsketchapp.core.api.BodyId
import com.solidsketchapp.core.api.Mesh
import com.solidsketchapp.core.io.MemoryDocument
import com.solidsketchapp.core.view.ManifoldBooleanEngine
import com.solidsketchapp.steel.comp.Components
import com.solidsketchapp.steel.comp.Js
import com.solidsketchapp.steel.comp.MemoryStore
import com.solidsketchapp.steel.comp.TkCheck
import com.solidsketchapp.steel.comp.TkUi
import com.solidsketchapp.steel.conn.StRegen
import com.solidsketchapp.steel.data.SteelData
import com.solidsketchapp.steel.geom.StBuild
import com.solidsketchapp.steel.model.StParts
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
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
import kotlin.math.max

/**
 * S8 / S9 acceptance: every catalog component case in the JSON files of tests/steel/golden/comp (tools/golden/comp_cases.js) is
 * rebuilt through the app's framework (members by StParts, the component by Components.add = v69 __st.connect) and
 * compared with v69 body by body in v69's order: members after their cuts, the component's plates and bolt groups
 * (volume 1e-3, box 0.1 mm), the plates' name / role / prof / mat / cls / asm, the bolt groups (points in order 0.1 mm,
 * n, d, d0, L, axis), the welds (name, a, len, type, ws, around) and the rule-check status with its messages.
 * Cases listed in a known*.json file (case → reason) are reported, not failed; they are the NOT VERIFIED list.
 * Writes build/comp-golden-report.txt (per family: cases passed / total). Manifold on the build machine: -PhostJni.
 */
class CompGoldenTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
    private val data by lazy { SteelData.load { File(root, "steel/src/main/assets/steel/$it").readText() } }
    private val ui by lazy { TkUi.parse(File(root, "steel/src/main/assets/steel/tk_ui.json").readText()) }
    private val engine by lazy { ManifoldBooleanEngine() }
    @Before fun needsHostLib() = assumeTrue("host JNI not built (use -PhostJni)", System.getProperty("sscore.hostJni") == "1")

    private fun box(m: Mesh): List<Double> { val lo = DoubleArray(3) { Double.MAX_VALUE }; val hi = DoubleArray(3) { -Double.MAX_VALUE }
        for (i in 0 until m.vertexCount) for (k in 0..2) { val v = m.positions[i * 3 + k].toDouble(); lo[k] = minOf(lo[k], v); hi[k] = maxOf(hi[k], v) }; return lo.toList() + hi.toList() }
    private fun s(o: JsonObject?, k: String) = o?.get(k)?.let { Js.str(it) } ?: ""

    /** Compares one case; returns the differences (empty = equal to v69). */
    fun check(name: String, g: JsonObject): List<String> {
        val bad = ArrayList<String>()
        if (g["error"] != null) return listOf("$name: v69 error ${g["error"]}")
        val doc = MemoryDocument(); val build = StBuild(data.profiles, union = { engine.union(it) })
        val parts = StParts(data, doc, null, build); val regen = StRegen(build, engine, data.profiles, data.tk)
        val ids = g.getValue("members").jsonArray.map { m -> val (set, o) = m.jsonArray
            parts.add(JsonObject(LinkedHashMap<String, JsonElement>().apply { put("kind", JsonPrimitive("member")); putAll(data.propertySet(set.jsonPrimitive.content)!!.raw); putAll(o.jsonObject) }), "m") }
        val comps = Components(ui, doc, null, MemoryStore(), regen); val k = g.getValue("k").jsonPrimitive.content
        val c = comps.add(k, ids, JsonObject(LinkedHashMap(comps.cq(k)).apply { putAll(g["q"]?.jsonObject ?: JsonObject(emptyMap())) }))
        val gb = g.getValue("bodies").jsonArray.map { it.jsonObject }
        val ob = doc.bodyIds().map { id -> id to Json.parseToJsonElement(doc.bodyJson(id)!!).jsonObject["st"]?.jsonObject }
        if (gb.size != ob.size) bad += "$name: ${ob.size} bodies vs ${gb.size} (${ob.map { s(it.second, "name") }} vs ${gb.map { s(it, "name") }})"
        for (i in 0 until minOf(gb.size, ob.size)) { val gbi = gb[i]; val (id, st) = ob[i]; val tag = "$name: body $i ${s(gbi, "name")}"
            if (s(st, "kind") != s(gbi, "kind") || s(st, "name") != s(gbi, "name")) { bad += "$tag: is ${s(st, "kind")} ${s(st, "name")}"; continue }
            val m = doc.bodyMesh(id); if (m == null) { bad += "$tag: no solid"; continue }
            gbi["volume"]?.jsonPrimitive?.double?.let { gv -> val v = engine.volume(m); if (abs(v - gv) > max(1.0, abs(gv) * 1e-3)) bad += "$tag: volume $v vs $gv" }
            gbi["bbox"]?.jsonArray?.map { it.jsonPrimitive.double }?.let { gbx -> val bx = box(m); if ((0..5).any { abs(bx[it] - gbx[it]) > 0.1 }) bad += "$tag: box $bx vs $gbx" }
            if (s(gbi, "kind") == "conn") { for (f in listOf("role", "prof", "mat", "cls")) if (s(st, f) != s(gbi, f)) bad += "$tag: $f ${s(st, f)} vs ${s(gbi, f)}"
                // asm = the body the plate is welded to; v69's ids keep counting between cases, so compare which body (by order)
                val oa = ob.indexOfFirst { it.first.value == s(st, "asm") }; val ga = gb.indexOfFirst { s(it, "id") == s(gbi, "asm") }
                if (oa != ga) bad += "$tag: welded to body $oa vs $ga" }
            if (s(gbi, "kind") == "bolts") {
                for (f in listOf("n", "d", "d0", "L", "grade")) if (s(st, f) != s(gbi, f)) bad += "$tag: $f ${s(st, f)} vs ${s(gbi, f)}"
                val ax = st!!.getValue("ax").jsonArray.map { it.jsonPrimitive.double }; val gax = gbi.getValue("ax").jsonArray.map { it.jsonPrimitive.double }
                if ((0..2).any { abs(ax[it] - gax[it]) > 1e-3 }) bad += "$tag: axis $ax vs $gax"
                val p1 = st.getValue("pts").jsonArray.map { p -> p.jsonArray.map { it.jsonPrimitive.double } }; val p2 = gbi.getValue("pts").jsonArray.map { p -> p.jsonArray.map { it.jsonPrimitive.double } }
                if (p1.size != p2.size || p1.indices.any { j -> (0..2).any { abs(p1[j][it] - p2[j][it]) > 0.1 } }) bad += "$tag: bolt points $p1 vs $p2" } }
        val c2 = comps.conn(c.id)!!
        val ow = (c2.raw["welds"] as? JsonArray ?: JsonArray(emptyList())).map { w -> w.jsonObject.let { listOf("name", "a", "len", "type", "ws", "around").joinToString("|") { f -> s(it, f) } } }
        val gw = g.getValue("welds").jsonArray.map { w -> w.jsonObject.let { listOf("name", "a", "len", "type", "ws", "around").joinToString("|") { f -> s(it, f) } } }
        if (ow != gw) bad += "$name: welds $ow vs $gw"
        val r = TkCheck(comps, doc, engine, data.profiles).status(c2)
        val gs = g["status"]?.jsonPrimitive?.int; val gm = g["msgs"]?.jsonArray?.map { it.jsonPrimitive.content }
        if (gs != null && (r.s != gs || r.msgs != gm)) bad += "$name: status ${r.s} ${r.msgs} vs $gs $gm"
        return bad
    }

    @Test fun componentsEqualV69() {
        val dir = File(root, "tests/steel/golden/comp"); val files = dir.listFiles { f -> f.name.endsWith(".json") && !f.name.startsWith("known") }?.sortedBy { it.name } ?: emptyList()
        assumeTrue("no component golden files", files.isNotEmpty())
        // known*.json: case -> reason (one file per family, e.g. known_s8_bp.json), merged
        val known = (dir.listFiles { f -> f.name.startsWith("known") && f.name.endsWith(".json") } ?: emptyArray()).flatMap { f -> Json.parseToJsonElement(f.readText()).jsonObject.entries.map { it.key to it.value.jsonPrimitive.content } }.toMap()
        val fails = ArrayList<String>(); val report = StringBuilder(); val fam = LinkedHashMap<String, IntArray>()   // family -> passed, total, known
        val only = (System.getProperty("comp.only") ?: System.getenv("COMP_ONLY"))?.takeIf { it.isNotBlank() }   // run one case file: COMP_ONLY=s8_bp
        for (f in files) { if (only != null && !f.name.startsWith(only)) continue
            for ((name, g0) in Json.parseToJsonElement(f.readText()).jsonObject.getValue("cases").jsonObject) { val g = g0.jsonObject
                val k = g.getValue("k").jsonPrimitive.content; val fm = ui.conn[k]?.fam ?: "?"; val cnt = fam.getOrPut(fm) { IntArray(3) }; cnt[1]++
                val bad = runCatching { check(name, g) }.getOrElse { listOf("$name: exception $it") }
                when { bad.isEmpty() -> cnt[0]++
                    name in known -> { cnt[2]++; report.append("KNOWN $name: ${known[name]}\n") }
                    else -> { fails += bad.take(6); report.append("FAIL $name\n") } } } }
        report.insert(0, fam.entries.joinToString("\n", postfix = "\n") { (k, v) -> "$k ${v[0]}/${v[1]} passed${if (v[2] > 0) ", ${v[2]} known" else ""}" })
        File(root, "build").mkdirs(); File(root, "build/comp-golden-report.txt").writeText(report.toString())
        println(report)
        assertTrue(fails.joinToString("\n"), fails.isEmpty())
    }
}
