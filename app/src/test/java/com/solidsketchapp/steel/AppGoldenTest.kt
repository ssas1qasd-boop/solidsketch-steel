package com.solidsketchapp.steel

import com.solidsketchapp.core.api.BodyId
import com.solidsketchapp.core.api.Mesh
import com.solidsketchapp.core.io.MemoryDocument
import com.solidsketchapp.core.view.ManifoldBooleanEngine
import com.solidsketchapp.steel.comp.Components
import com.solidsketchapp.steel.comp.Js
import com.solidsketchapp.steel.comp.MemoryStore
import com.solidsketchapp.steel.comp.TkUi
import com.solidsketchapp.steel.conn.Classic
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
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.max

/**
 * S10 acceptance: every application case in tests/steel/golden/app (tools/golden/app_cases.js) is rebuilt through the app
 * (members by StParts, then the application: a connection (Components.add = v69 __st.connect), the portal frame, stairs,
 * railing or a GEN application) and the whole model is compared with v69 body by body in v69's order:
 *  - kind and name of every body; volume (1e-3, at least 1 mm³) and box (0.1 mm);
 *  - members: every field of v69's st (numbers 1e-3, points 0.1 mm), so profile, points, rotation, plane, depth, offsets,
 *    class, name, material, numbering series and the generator flag all match;
 *  - plates: name, role, prof, mat, cls, pp, ps, fin and the part they are welded to (by order);
 *  - bolt groups: n, d, d0, L, grade, std, thr, sw, axis, points in order (0.1 mm);
 *  - connections: count, type, parts (by order), every key of q, the welds (name, a, len, type, ws, around), nSteps.
 * Cases in tests/steel/golden/app/known*.json (case → reason) are reported, not failed. Needs Manifold: -PhostJni.
 * APP_ONLY=s10_conns runs one case file, APP_CASE=<regex> some cases. Writes build/app-golden-report.txt.
 */
class AppGoldenTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
    private val data by lazy { SteelData.load { File(root, "steel/src/main/assets/steel/$it").readText() } }
    private val ui by lazy { TkUi.parse(File(root, "steel/src/main/assets/steel/tk_ui.json").readText()) }
    private val engine by lazy { ManifoldBooleanEngine() }
    private fun hostJni() = System.getProperty("sscore.hostJni") == "1"

    private fun box(m: Mesh): List<Double> { val lo = DoubleArray(3) { Double.MAX_VALUE }; val hi = DoubleArray(3) { -Double.MAX_VALUE }
        for (i in 0 until m.vertexCount) for (k in 0..2) { val v = m.positions[i * 3 + k].toDouble(); lo[k] = minOf(lo[k], v); hi[k] = maxOf(hi[k], v) }; return lo.toList() + hi.toList() }
    private fun s(o: JsonObject?, k: String) = o?.get(k)?.let { Js.str(it) } ?: ""

    /** A model built the app's way, for one case. */
    class Model(val doc: MemoryDocument, val parts: StParts, val comps: Components, val ids: List<BodyId>)

    fun build(g: JsonObject): Model {
        val doc = MemoryDocument(); val b = StBuild(data.profiles, union = { engine.union(it) })
        val parts = StParts(data, doc, null, b); val regen = StRegen(b, engine, data.profiles, data.tk).also { it.ui = ui }
        (g["stGen0"] as? JsonObject)?.let { doc.setField("stGen", it.toString()) }
        val ids = (g["members"] as? JsonArray ?: JsonArray(emptyList())).map { m -> val (set, o) = m.jsonArray
            parts.add(JsonObject(LinkedHashMap<String, JsonElement>().apply { put("kind", JsonPrimitive("member")); putAll(data.propertySet(set.jsonPrimitive.content)!!.raw); putAll(o.jsonObject) }), "m") }
        val comps = Components(ui, doc, null, MemoryStore(), regen)
        return Model(doc, parts, comps, ids)
    }

    /** Runs the case's application on the model (S10.2: connections; the applications follow in S10.3 / S10.4). */
    fun run(m: Model, g: JsonObject) {
        val r = g["run"] as? JsonObject ?: return
        (r["connect"] as? JsonArray)?.let { (k, ix, q) -> val kk = k.jsonPrimitive.content
            m.comps.add(kk, ix.jsonArray.map { m.ids[it.jsonPrimitive.int] }, JsonObject(LinkedHashMap(m.comps.cq(kk)).apply { putAll((q as? JsonObject) ?: JsonObject(emptyMap())) })); return }
        error("application not ported yet: ${r.keys}")
    }

    private fun num(e: JsonElement?): Double? = (e as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull
    /** v69's value vs the app's (numbers 1e-3 relative / 0.1 absolute for coordinates, nested arrays and objects element by element). */
    private fun same(a: JsonElement?, b: JsonElement?, tol: Double): Boolean {
        if (a == null || a is JsonNull) return b == null || b is JsonNull || (b is JsonPrimitive && b.content == "")
        if (a is JsonArray) return b is JsonArray && a.size == b.size && a.indices.all { same(a[it], b[it], tol) }
        if (a is JsonObject) return b is JsonObject && a.keys.all { same(a[it], b[it], tol) }
        val x = num(a); val y = num(b)
        if (x != null && y != null) return abs(x - y) <= max(tol, abs(x) * 1e-6)
        return Js.str(a) == Js.str(b ?: JsonPrimitive(""))
    }

    /** Compares one case; returns the differences (empty = equal to v69). */
    fun check(name: String, g: JsonObject): List<String> {
        val bad = ArrayList<String>()
        val m = build(g); run(m, g)
        val gb = g.getValue("bodies").jsonArray.map { it.jsonObject }
        val ob = m.doc.bodyIds().map { id -> id to Json.parseToJsonElement(m.doc.bodyJson(id)!!).jsonObject["st"]?.jsonObject }
        val gIx = gb.withIndex().associate { s(it.value, "id") to it.index }; val oIx = ob.withIndex().associate { it.value.first.value to it.index }
        if (gb.size != ob.size) bad += "$name: ${ob.size} bodies vs ${gb.size} (${ob.groupingBy { s(it.second, "kind") + " " + s(it.second, "name") }.eachCount()} vs ${gb.groupingBy { s(it["st"]?.jsonObject, "kind") + " " + s(it["st"]?.jsonObject, "name") }.eachCount()})"
        for (i in 0 until minOf(gb.size, ob.size)) {
            val gbi = gb[i]; val gst = gbi["st"] as? JsonObject; val (id, st) = ob[i]; val tag = "$name: body $i ${s(gst, "kind")} ${s(gst, "name")}"
            if (s(st, "kind") != s(gst, "kind") || s(st, "name") != s(gst, "name")) { bad += "$tag: is ${s(st, "kind")} ${s(st, "name")}"; continue }
            val mesh = m.doc.bodyMesh(id)
            if (hostJni() && mesh != null) {
                gbi["volume"]?.jsonPrimitive?.double?.let { gv -> val v = engine.volume(mesh); if (abs(v - gv) > max(1.0, abs(gv) * 1e-3)) bad += "$tag: volume $v vs $gv" }
                gbi["bbox"]?.jsonArray?.map { it.jsonPrimitive.double }?.let { gbx -> val bx = box(mesh); if ((0..5).any { abs(bx[it] - gbx[it]) > 0.1 }) bad += "$tag: box ${bx.map { Math.round(it * 10) / 10.0 }} vs $gbx" } }
            when (s(gst, "kind")) {
                "member" -> for ((k, v) in gst!!) if (!same(v, st!![k], if (k == "p1" || k == "p2") 0.1 else 1e-3)) bad += "$tag: $k ${st[k]} vs $v"
                "conn" -> { for (f in listOf("role", "prof", "mat", "cls", "pp", "ps", "fin")) if (!same(gst!![f], st!![f], 1e-9)) bad += "$tag: $f ${s(st, f)} vs ${s(gst, f)}"
                    if (oIx[s(st, "asm")] != gIx[s(gst, "asm")]) bad += "$tag: welded to body ${oIx[s(st, "asm")]} vs ${gIx[s(gst, "asm")]}" }
                "bolts" -> for (f in listOf("n", "d", "d0", "L", "grade", "std", "thr", "sw", "ax", "pts")) if (!same(gst!![f], st!![f], if (f == "pts") 0.1 else 1e-3)) bad += "$tag: $f ${st[f]} vs ${gst[f]}"
            }
        }
        // connections: type, parts (by order), q, welds, nSteps
        val gc = g.getValue("conns").jsonArray.map { it.jsonObject }; val oc = m.comps.conns()
        if (gc.size != oc.size) bad += "$name: ${oc.size} connections vs ${gc.size} (${oc.groupingBy { it.type }.eachCount()} vs ${gc.groupingBy { s(it, "type") }.eachCount()})"
        for (i in 0 until minOf(gc.size, oc.size)) { val a = gc[i]; val c = oc[i]; val tag = "$name: connection $i ${s(a, "type")}"
            if (c.type != s(a, "type")) { bad += "$tag: is ${c.type}"; continue }
            val gp = a.getValue("parts").jsonArray.map { gIx[Js.str(it)] }; val op = c.parts.map { oIx[it] }; if (gp != op) bad += "$tag: parts $op vs $gp"
            for ((k, v) in a.getValue("q").jsonObject) if (!same(v, c.q[k], 1e-6)) bad += "$tag: q.$k ${c.q[k]} vs $v"
            val wl = { l: JsonArray? -> (l ?: JsonArray(emptyList())).map { w -> w.jsonObject.let { listOf("name", "a", "len", "type", "ws", "around").joinToString("|") { f -> s(it, f) } } } }
            val ow = wl(c.raw["welds"] as? JsonArray); val gw = wl(a["welds"] as? JsonArray); if (ow != gw) bad += "$tag: welds $ow vs $gw"
            a["nSteps"]?.takeIf { it !is JsonNull }?.let { if (!same(it, c.raw["nSteps"], 1e-9)) bad += "$tag: nSteps ${c.raw["nSteps"]} vs $it" } }
        return bad
    }

    @Test fun classicDefaultsEqualV69() {
        val f = File(root, "tests/steel/golden/app/conn_defs.json"); assumeTrue(f.exists())
        val conn = Json.parseToJsonElement(f.readText()).jsonObject.getValue("conn").jsonObject
        for ((k, d) in Classic.DEFS) { val v = conn.getValue(k).jsonObject; assertEquals("$k def", v.getValue("def").jsonObject.toString(), d.toString())
            assertEquals("$k label", Js.str(v["label"]), Classic.LABEL[k]) }
    }

    @Test fun applicationsEqualV69() {
        assumeTrue("host JNI not built (use -PhostJni)", hostJni())
        val dir = File(root, "tests/steel/golden/app"); val files = dir.listFiles { f -> f.name.startsWith("s10_") && f.name.endsWith(".json") }?.sortedBy { it.name } ?: emptyList()
        assumeTrue("no application golden files", files.isNotEmpty())
        val known = (dir.listFiles { f -> f.name.startsWith("known") && f.name.endsWith(".json") } ?: emptyArray()).flatMap { f -> Json.parseToJsonElement(f.readText()).jsonObject.entries.map { it.key to it.value.jsonPrimitive.content } }.toMap()
        val only = System.getenv("APP_ONLY")?.takeIf { it.isNotBlank() }; val pick = System.getenv("APP_CASE")?.takeIf { it.isNotBlank() }?.let(::Regex)
        val fails = ArrayList<String>(); val report = StringBuilder(); val tally = LinkedHashMap<String, IntArray>()
        for (f in files) { if (only != null && !f.name.startsWith(only)) continue
            for ((name, g0) in Json.parseToJsonElement(f.readText()).jsonObject.getValue("cases").jsonObject) { if (pick != null && !pick.containsMatchIn(name)) continue
                val g = g0.jsonObject; val grp = f.nameWithoutExtension + ":" + name.substringBefore('_'); val cnt = tally.getOrPut(grp) { IntArray(3) }; cnt[1]++
                val bad = runCatching { check(name, g) }.getOrElse { listOf("$name: exception $it ${it.stackTrace.take(4).joinToString(" < ")}") }
                when { bad.isEmpty() -> cnt[0]++
                    name in known -> { cnt[2]++; report.append("KNOWN $name: ${known[name]}\n") }
                    else -> { fails += bad.take(8); report.append("FAIL $name: ${bad.take(3).joinToString(" | ")}\n") } } } }
        report.insert(0, tally.entries.joinToString("\n", postfix = "\n") { (k, v) -> "$k ${v[0]}/${v[1]} passed${if (v[2] > 0) ", ${v[2]} known" else ""}" })
        File(root, "build").mkdirs(); File(root, "build/app-golden-report.txt").writeText(report.toString())
        println(report.lines().take(60).joinToString("\n"))
        assertTrue(fails.take(80).joinToString("\n"), fails.isEmpty())
    }
}
