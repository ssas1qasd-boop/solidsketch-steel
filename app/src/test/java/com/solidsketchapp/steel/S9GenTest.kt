package com.solidsketchapp.steel

import com.solidsketchapp.core.api.Mesh
import com.solidsketchapp.core.io.MemoryDocument
import com.solidsketchapp.core.view.ManifoldBooleanEngine
import com.solidsketchapp.steel.comp.Components
import com.solidsketchapp.steel.comp.GenRun
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
 * S9 acceptance for the GEN components (v69 stGenRun): every case of tests/steel/golden/gen (tools/golden/gen_cases.js) is
 * rebuilt through [GenRun] (members by StParts, q saved as the defaults for new components) and compared with v69: the toast,
 * every body in order (kind, name; members' prof, p1 / p2 0.1 mm, type; volume 1e-3, box 0.1 mm; component plates' role / prof /
 * mat / cls / welded-to; bolt groups as CompGoldenTest), and every component (type, parts by body index, q, welds).
 * Cases in known*.json (case -> reason) are reported, not failed. Writes build/gen-golden-report.txt. Manifold: -PhostJni.
 */
class S9GenTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
    private val data by lazy { SteelData.load { File(root, "steel/src/main/assets/steel/$it").readText() } }
    private val ui by lazy { TkUi.parse(File(root, "steel/src/main/assets/steel/tk_ui.json").readText()) }
    private val engine by lazy { ManifoldBooleanEngine() }
    @Before fun needsHostLib() = assumeTrue("host JNI not built (use -PhostJni)", System.getProperty("sscore.hostJni") == "1")

    private fun box(m: Mesh): List<Double> { val lo = DoubleArray(3) { Double.MAX_VALUE }; val hi = DoubleArray(3) { -Double.MAX_VALUE }
        for (i in 0 until m.vertexCount) for (k in 0..2) { val v = m.positions[i * 3 + k].toDouble(); lo[k] = minOf(lo[k], v); hi[k] = maxOf(hi[k], v) }; return lo.toList() + hi.toList() }
    private fun s(o: JsonObject?, k: String) = o?.get(k)?.let { Js.str(it) } ?: ""
    private fun nums(e: JsonElement?) = (e as? JsonArray)?.map { it.jsonPrimitive.double } ?: emptyList()
    private fun weld(w: JsonElement) = w.jsonObject.let { listOf("name", "a", "len", "type", "ws", "around").joinToString("|") { f -> s(it, f) } }

    fun check(name: String, g: JsonObject): List<String> {
        val bad = ArrayList<String>()
        if (g["error"] != null) return listOf("$name: v69 error ${g["error"]}")
        val doc = MemoryDocument(); val build = StBuild(data.profiles, union = { engine.union(it) })
        val regen = StRegen(build, engine, data.profiles, data.tk); val parts = StParts(data, doc, null, build, regen)
        val ids = g.getValue("members").jsonArray.map { m -> val (set, o) = m.jsonArray
            parts.add(JsonObject(LinkedHashMap<String, JsonElement>().apply { put("kind", JsonPrimitive("member")); putAll(data.propertySet(set.jsonPrimitive.content)!!.raw); putAll(o.jsonObject) }), "m") }
        val comps = Components(ui, doc, null, MemoryStore(), regen); val k = g.getValue("k").jsonPrimitive.content
        (g["q"] as? JsonObject)?.takeIf { it.isNotEmpty() }?.let { comps.setDefaults(k, it) }
        val pts = g["pts"]?.jsonArray?.map { p -> nums(p).toDoubleArray() } ?: emptyList()
        val toast = GenRun(data, doc, null, parts, comps, regen).run(k, ids, pts) ?: ""
        if (toast != s(g, "toast")) bad += "$name: toast '$toast' vs '${s(g, "toast")}'"
        val gb = g.getValue("bodies").jsonArray.map { it.jsonObject }
        val ob = doc.bodyIds().map { id -> Triple(id, Json.parseToJsonElement(doc.bodyJson(id)!!).jsonObject, Json.parseToJsonElement(doc.bodyJson(id)!!).jsonObject["st"]?.jsonObject) }
        if (gb.size != ob.size) bad += "$name: ${ob.size} bodies vs ${gb.size} (${ob.map { s(it.third, "name") }} vs ${gb.map { s(it, "name") }})"
        for (i in 0 until minOf(gb.size, ob.size)) { val gbi = gb[i]; val (id, body, st) = ob[i]; val tag = "$name: body $i ${s(gbi, "name")}"
            if (s(st, "kind") != s(gbi, "kind") || s(st, "name") != s(gbi, "name")) { bad += "$tag: is ${s(st, "kind")} ${s(st, "name")}"; continue }
            val m = doc.bodyMesh(id); if (m == null) { bad += "$tag: no solid"; continue }
            gbi["volume"]?.jsonPrimitive?.double?.let { gv -> val v = engine.volume(m); if (abs(v - gv) > max(1.0, abs(gv) * 1e-3)) bad += "$tag: volume $v vs $gv" }
            gbi["bbox"]?.jsonArray?.map { it.jsonPrimitive.double }?.let { gbx -> val bx = box(m); if ((0..5).any { abs(bx[it] - gbx[it]) > 0.1 }) bad += "$tag: box $bx vs $gbx" }
            if (s(gbi, "kind") == "member") {
                for (f in listOf("prof", "depth", "rot")) if (s(st, f) != s(gbi, f)) bad += "$tag: $f ${s(st, f)} vs ${s(gbi, f)}"
                if (s(body, "type") != s(gbi, "type")) bad += "$tag: type ${s(body, "type")} vs ${s(gbi, "type")}"
                for (f in listOf("p1", "p2")) { val a = nums(st!![f]); val b = nums(gbi[f]); if (a.size != 3 || (0..2).any { abs(a[it] - b[it]) > 0.1 }) bad += "$tag: $f $a vs $b" } }
            if (s(gbi, "kind") == "conn") { for (f in listOf("role", "prof", "mat", "cls")) if (s(st, f) != s(gbi, f)) bad += "$tag: $f ${s(st, f)} vs ${s(gbi, f)}"
                val oa = ob.indexOfFirst { it.first.value == s(st, "asm") }; val ga = gb.indexOfFirst { s(it, "id") == s(gbi, "asm") }
                if (oa != ga) bad += "$tag: welded to body $oa vs $ga" }
            if (s(gbi, "kind") == "bolts") {
                for (f in listOf("n", "d", "d0", "L", "grade")) if (s(st, f) != s(gbi, f)) bad += "$tag: $f ${s(st, f)} vs ${s(gbi, f)}"
                val ax = nums(st!!["ax"]); val gax = nums(gbi["ax"]); if ((0..2).any { abs(ax[it] - gax[it]) > 1e-3 }) bad += "$tag: axis $ax vs $gax"
                val p1 = st.getValue("pts").jsonArray.map(::nums); val p2 = gbi.getValue("pts").jsonArray.map(::nums)
                if (p1.size != p2.size || p1.indices.any { j -> (0..2).any { abs(p1[j][it] - p2[j][it]) > 0.1 } }) bad += "$tag: bolt points $p1 vs $p2" } }
        val oc = comps.conns(); val gc = g.getValue("conns").jsonArray.map { it.jsonObject }
        if (oc.size != gc.size) bad += "$name: ${oc.size} components vs ${gc.size}"
        for (i in 0 until minOf(oc.size, gc.size)) { val c = oc[i]; val gci = gc[i]; val tag = "$name: component $i ${s(gci, "type")}"
            if (c.type != s(gci, "type")) bad += "$tag: type ${c.type}"
            val pix = c.parts.map { p -> ob.indexOfFirst { it.first.value == p } }; val gpix = gci.getValue("parts").jsonArray.map { it.jsonPrimitive.int }
            if (pix != gpix) bad += "$tag: parts $pix vs $gpix"
            val gq = gci.getValue("q").jsonObject; if (gq.keys != c.q.keys || gq.keys.any { Js.str(gq[it]) != Js.str(c.q[it]) }) bad += "$tag: q ${c.q} vs $gq"
            val ow = (c.raw["welds"] as? JsonArray ?: JsonArray(emptyList())).map(::weld); val gw = gci.getValue("welds").jsonArray.map(::weld)
            if (ow != gw) bad += "$tag: welds $ow vs $gw" }
        return bad
    }

    @Test fun generatorsEqualV69() {
        val dir = File(root, "tests/steel/golden/gen"); val files = dir.listFiles { f -> f.name.endsWith(".json") && !f.name.startsWith("known") }?.sortedBy { it.name } ?: emptyList()
        assumeTrue("no generator golden files", files.isNotEmpty())
        val known = (dir.listFiles { f -> f.name.startsWith("known") && f.name.endsWith(".json") } ?: emptyArray()).flatMap { f -> Json.parseToJsonElement(f.readText()).jsonObject.entries.map { it.key to it.value.jsonPrimitive.content } }.toMap()
        val fails = ArrayList<String>(); val report = StringBuilder(); val per = LinkedHashMap<String, IntArray>()
        for (f in files) for ((name, g0) in Json.parseToJsonElement(f.readText()).jsonObject.getValue("cases").jsonObject) { val g = g0.jsonObject
            val cnt = per.getOrPut(g.getValue("k").jsonPrimitive.content) { IntArray(3) }; cnt[1]++
            val bad = runCatching { check(name, g) }.getOrElse { listOf("$name: exception $it") }
            when { bad.isEmpty() -> cnt[0]++
                name in known -> { cnt[2]++; report.append("KNOWN $name: ${known[name]}\n") }
                else -> { fails += bad.take(6); report.append("FAIL $name\n") } } }
        report.insert(0, per.entries.joinToString("\n", postfix = "\n") { (k, v) -> "$k ${v[0]}/${v[1]} passed${if (v[2] > 0) ", ${v[2]} known" else ""}" })
        File(root, "build").mkdirs(); File(root, "build/gen-golden-report.txt").writeText(report.toString())
        println(report)
        assertTrue(fails.joinToString("\n"), fails.isEmpty())
    }
}
