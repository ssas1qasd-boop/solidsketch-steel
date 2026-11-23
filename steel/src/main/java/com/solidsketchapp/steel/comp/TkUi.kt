package com.solidsketchapp.steel.comp

import com.solidsketchapp.core.api.JsNumber
import com.solidsketchapp.steel.model.StGrid
import com.solidsketchapp.steel.model.jsNum
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** v69 `stLS` (localStorage, JSON values). The app keeps it in SharedPreferences; tests use [MemoryStore]. */
interface SteelStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
}
class MemoryStore : SteelStore { private val m = HashMap<String, String>(); override fun get(key: String) = m[key]; override fun put(key: String, value: String) { m[key] = value } }

/** One CONN entry of a catalog component (v40 L14881: label, Tekla name, pick prompts, family, group, def, read-only options). */
class CompDef(val key: String, val label: String, val tekla: String, val parts: List<String>, val fam: String?, val grp: String?, val gen: Boolean, val def: JsonObject, val ro: List<String>)
class TkOpt(val v: JsonElement, val t: String, val ic: String?)
/** A dialog field (v58 TKF): q key, label, type num / text / list / sel / chk, options, default, placeholder, note, off reason, tip, place on the picture (%), accessor. */
class TkField(val k: String, val lbl: String, val type: String, val opts: List<TkOpt>, val dv: JsonElement?, val ph: String?, val note: String?, val off: String?, val tip: String?, val at: Pair<Double, Double>?, val acc: String?)
class TkPartRow(val lbl: String, val role: String, val dm: Map<String, String>)
class TkTab(val name: String, val pic: String?, val dims: List<TkField>, val anchors: List<TkField>, val groups: List<Pair<String, List<TkField>>>, val parts: List<TkPartRow>?, val welds: Boolean)
class TkSpec(val help: String, val generic: Boolean, val tabs: List<TkTab>)
class TkThumb(val t: String?, val p: String?, val a: Map<String, Pair<Double, Double>>)
class TkSym(val shape: String, val color: String, val code: String, val num: String, val fam: String)

/**
 * The component catalog sheet and dialogs as v69 shows them (asset `tk_ui.json`, written from the live page by
 * `tools/golden/s7_catalog.js`): ST_CATALOG (18 groups, 252 items), CONN of every component, `tkSpec(k)`, the catalog
 * icons (SS_ICONS), the dialog pictures (SVG_PIC) and pictograms (TKPG), the v58 thumbnails.
 */
class TkUi(raw: JsonObject) {
    val catalog: List<Pair<String, List<String>>> = raw.getValue("catalog").jsonArray.map { it.jsonObject.let { g -> g.getValue("g").jsonPrimitive.content to g.getValue("items").jsonArray.map { i -> i.jsonPrimitive.content } } }
    val conn: Map<String, CompDef> = raw.getValue("conn").jsonObject.mapValues { (k, e) -> val o = e.jsonObject
        CompDef(k, o.str("label"), o.str("tekla"), o.getValue("parts").jsonArray.map { it.jsonPrimitive.content }, o.strOrNull("fam"), o.strOrNull("grp"),
            o["gen"]?.jsonPrimitive?.content == "true", o["def"] as? JsonObject ?: JsonObject(emptyMap()), (o["ro"] as? JsonArray)?.map { it.jsonPrimitive.content } ?: emptyList()) }
    val specs: Map<String, TkSpec> = raw.getValue("specs").jsonObject.mapValues { (_, e) -> spec(e.jsonObject) }
    val icons: Map<String, String> = raw.getValue("icons").jsonObject.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.contentOrNull?.let { k to it } }.toMap()
    val pics: Map<String, String> = raw.getValue("pics").jsonObject.mapValues { it.value.jsonPrimitive.content }
    val pg: Map<String, String> = raw.getValue("pg").jsonObject.mapValues { it.value.jsonPrimitive.content }
    val thumbs: Map<String, TkThumb> = raw.getValue("thumbs").jsonObject.mapValues { (_, e) -> val o = e.jsonObject
        TkThumb(o.strOrNull("t"), o.strOrNull("p"), (o["a"] as? JsonObject)?.mapValues { (_, a) -> a.jsonArray.let { StGrid.jsNumber(it[0].jsonPrimitive.content) to StGrid.jsNumber(it[1].jsonPrimitive.content) } } ?: emptyMap()) }
    val sym: Map<String, TkSym> = raw.getValue("sym").jsonObject.mapValues { (_, e) -> val o = e.jsonObject; TkSym(o.str("shape"), o.str("color"), o.str("code"), o.str("num"), o.str("fam")) }
    /** The accessor names used by the fields, with v69's get source (checked against [Acc.KNOWN]). */
    val acc: Map<String, String> = raw.getValue("acc").jsonObject.mapValues { it.value.jsonObject.str("get") }

    /** v69 ST_APPS entries on the sheet (`@numbering`, `@checks`). */
    val apps = mapOf("@portal" to ("Portal frame" to "Portal frame generator"), "@numbering" to ("Numbering" to "Numbering"), "@checks" to ("Checks" to "Clash check · model checks"))
    fun label(k: String) = conn[k]?.label ?: apps[k]?.first ?: k
    fun tekla(k: String) = conn[k]?.tekla ?: apps[k]?.second ?: k

    companion object {
        fun parse(text: String) = TkUi(Json.parseToJsonElement(text).jsonObject)
        private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull ?: ""
        private fun JsonObject.strOrNull(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
        private fun at(e: JsonElement?) = (e as? JsonArray)?.let { StGrid.jsNumber(it[0].jsonPrimitive.content) to StGrid.jsNumber(it[1].jsonPrimitive.content) }
        fun field(o: JsonObject) = TkField(o.str("k"), o.str("lbl"), o.strOrNull("type") ?: "num",
            (o["opts"] as? JsonArray)?.map { it.jsonObject.let { p -> TkOpt(p.getValue("v"), p.str("t"), p.strOrNull("ic")) } } ?: emptyList(),
            o["dv"], o.strOrNull("ph"), o.strOrNull("note"), o.strOrNull("off"), o.strOrNull("tip"), at(o["at"]), o.strOrNull("acc"))
        fun spec(o: JsonObject) = TkSpec(o.str("help"), o["generic"]?.jsonPrimitive?.content == "true", o.getValue("tabs").jsonArray.map { e -> val t = e.jsonObject
            TkTab(t.str("name"), t.strOrNull("pic"), (t["dims"] as? JsonArray)?.map { field(it.jsonObject) } ?: emptyList(), (t["anchors"] as? JsonArray)?.map { field(it.jsonObject) } ?: emptyList(),
                (t["groups"] as? JsonArray)?.map { g -> g.jsonArray.let { it[0].jsonPrimitive.content to it[1].jsonArray.map { f -> field(f.jsonObject) } } } ?: emptyList(),
                (t["parts"] as? JsonArray)?.map { p -> p.jsonArray.let { TkPartRow(it[0].jsonPrimitive.content, it[1].jsonPrimitive.content, it[2].jsonObject.mapValues { v -> v.value.jsonPrimitive.content }) } },
                t["welds"]?.jsonPrimitive?.content == "true") })
    }
}

/** JavaScript value helpers for the dialog (values are JSON: numbers, strings, '' for empty). */
object Js {
    val EMPTY = JsonPrimitive("")
    /** JavaScript `String(v)`. */
    fun str(e: JsonElement?): String = when (e) { null -> "undefined"; is JsonNull -> "null"; is JsonPrimitive -> if (e.isString) e.content else e.content.toDoubleOrNull()?.let { JsNumber.format(it) } ?: e.content; else -> e.toString() }
    /** JavaScript `+v` (Number(v)). */
    fun num(e: JsonElement?): Double = when (e) { null -> Double.NaN; is JsonNull -> 0.0; is JsonPrimitive -> if (e.isString) StGrid.jsNumber(e.content.trim().ifEmpty { "0" }) else e.content.toDoubleOrNull() ?: if (e.content == "true") 1.0 else if (e.content == "false") 0.0 else Double.NaN; else -> Double.NaN }
    fun truthy(e: JsonElement?) = com.solidsketchapp.steel.conn.jsTruthy(e)
    fun n(d: Double): JsonElement = jsNum(if (d == 0.0) 0.0 else d)
}

/** The field accessors v69's TKD dialogs use (v58 `neg`, `gaugeList`, `pitchList`), matched by their source text. */
object Acc {
    interface A { fun get(q: Map<String, JsonElement>, k: String): JsonElement; fun set(q: MutableMap<String, JsonElement>, k: String, v: JsonElement) }
    private val neg = object : A {
        override fun get(q: Map<String, JsonElement>, k: String): JsonElement { val v = q[k]; return if (v == null || Js.str(v) == "" && (v as? JsonPrimitive)?.isString == true) Js.EMPTY else Js.n(-Js.num(v)) }
        override fun set(q: MutableMap<String, JsonElement>, k: String, v: JsonElement) { q[k] = if ((v as? JsonPrimitive)?.isString == true && v.content == "") Js.EMPTY else Js.n(-Js.num(v)) }
    }
    private val gaugeList = object : A {
        override fun get(q: Map<String, JsonElement>, k: String): JsonElement {
            val g = Js.str(q["gauge"]); val g2 = if (Js.truthy(q["gauge2"])) Js.str(q["gauge2"]) else g
            return JsonPrimitive(if (com.solidsketchapp.steel.conn.jsInt(Js.num(q["cols"])) == 4) "$g2 $g $g2" else g)
        }
        override fun set(q: MutableMap<String, JsonElement>, k: String, v: JsonElement) {
            val a = Js.str(v).trim().split(Regex("""\s+""")).map { StGrid.jsNumber(it) }.filter { it.isFinite() && it > 0 }
            if (a.size >= 3) { q["cols"] = Js.n(4.0); q["gauge"] = Js.n(a[1]); q["gauge2"] = Js.n(a[0]) } else if (a.isNotEmpty()) { q["cols"] = Js.n(2.0); q["gauge"] = Js.n(a[0]) }
        }
    }
    private val pitchList = object : A {
        override fun get(q: Map<String, JsonElement>, k: String): JsonElement { val s = (if (Js.truthy(q["pitches"])) Js.str(q["pitches"]) else "").trim(); return JsonPrimitive(s.ifEmpty { if (Js.truthy(q["pitch"])) Js.str(q["pitch"]) else "" }) }
        override fun set(q: MutableMap<String, JsonElement>, k: String, v: JsonElement) { val s = Js.str(v).trim(); if (' ' in s) q["pitches"] = JsonPrimitive(s) else { q["pitches"] = Js.EMPTY; val n = StGrid.jsNumber(s.ifEmpty { "0" }); q["pitch"] = if (n != 0.0 && !n.isNaN()) Js.n(n) else (q["pitch"] ?: JsonNull) } }
    }
    /** v69 get source → accessor. */
    val KNOWN: Map<String, A> = mapOf(
        "(q, k) => q[k] === '' || q[k] === undefined ? '' : -q[k]" to neg,
        "q => (q.cols | 0) === 4 ? `\${q.gauge2 || q.gauge} \${q.gauge} \${q.gauge2 || q.gauge}` : `\${q.gauge}`" to gaugeList,
        "q => String(q.pitches || '').trim() || (q.pitch ? String(q.pitch) : '')" to pitchList,
    )
}
