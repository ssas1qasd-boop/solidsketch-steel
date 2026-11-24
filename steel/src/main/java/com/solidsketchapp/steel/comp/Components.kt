package com.solidsketchapp.steel.comp

import com.solidsketchapp.core.api.BodyId
import com.solidsketchapp.core.api.DocumentApi
import com.solidsketchapp.core.api.UndoApi
import com.solidsketchapp.steel.conn.StConn
import com.solidsketchapp.steel.conn.StRegen
import com.solidsketchapp.steel.model.jsArr
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

/**
 * The component framework of v69 (v38 L14383 catalog sheet and pick order, v58 L16304 dialog logic): catalog groups with
 * "Recently used", search by name or number, the defaults for new components (`ss.connDef`), saved presets
 * (`ss.connSets`), adding a component on picked parts (`stConnAdd` + regen in one undo step), Modify / Get / Apply /
 * Delete, and `stConnOK`. Plain Kotlin; the Compose sheets only show it.
 */
class Components(val ui: TkUi, private val doc: DocumentApi, private val undo: UndoApi?, private val store: SteelStore, private val regen: StRegen?) {
    init { regen?.ui = ui }
    // ---------------- storage (v69 stLS) ----------------
    private fun ls(k: String): JsonElement? = store.get(k)?.let { runCatching { Json.parseToJsonElement(it) }.getOrNull() }
    private fun ls(k: String, v: JsonElement) = store.put(k, v.toString())

    // ---------------- catalog sheet ----------------
    /** v69 stCatalog: with no filter, "Recently used" (when there are any) then ST_CATALOG. */
    fun groups(): List<Pair<String, List<String>>> {
        val rec = recent()
        return if (rec.isNotEmpty()) listOf("Recently used" to rec) + ui.catalog else ui.catalog
    }
    fun recent(): List<String> = (ls("ss.connRecent") as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }?.filter { it in ui.conn } ?: emptyList()
    /** v69 search: every item whose "label tekla key" (lower case) contains the filter, in sheet order. */
    fun search(filter: String): List<String> {
        val f = filter.trim().lowercase(); if (f.isEmpty()) return emptyList()
        return ui.catalog.flatMap { it.second }.distinct().filter { k -> "${ui.label(k)} ${ui.tekla(k)} $k".lowercase().contains(f) }
    }
    /** v69 stCatPick's recently-used list: this key first, at most 5. */
    fun noteRecent(k: String) { val r = (ls("ss.connRecent") as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }?.filter { it != k } ?: emptyList(); ls("ss.connRecent", JsonArray((listOf(k) + r).take(5).map(::JsonPrimitive))) }
    /** v69 stCatPrompt. */
    fun prompt(k: String, picked: Int): String { val c = ui.conn.getValue(k); val p = c.parts; return if (p.size == 1) "${c.tekla}: pick the ${p[0]}" else "${c.tekla}: pick the ${p[minOf(picked, p.size - 1)]}" }
    fun countInModel(): Int = conns().size

    // ---------------- defaults and presets ----------------
    /** CONN[k].def: a catalog component's (tk_ui.json) or one of v69's own types the applications make (S10, [com.solidsketchapp.steel.conn.Classic]). */
    fun defOf(k: String): JsonObject = ui.conn[k]?.def ?: com.solidsketchapp.steel.conn.Classic.DEFS[k] ?: error("unknown connection type $k")
    /** CONN[k].label. */
    fun labelOf(k: String): String = ui.conn[k]?.label ?: com.solidsketchapp.steel.conn.Classic.LABEL[k] ?: k
    /** v69 stCQ: CONN def + the saved defaults for new components. */
    fun cq(k: String): JsonObject = JsonObject(LinkedHashMap(defOf(k)).apply { ((ls("ss.connDef") as? JsonObject)?.get(k) as? JsonObject)?.let { putAll(it) } })
    fun setDefaults(k: String, q: JsonObject) { val d = LinkedHashMap((ls("ss.connDef") as? JsonObject) ?: JsonObject(emptyMap())); d[k] = q; ls("ss.connDef", JsonObject(d)) }
    fun presets(k: String): List<String> = ((ls("ss.connSets") as? JsonObject)?.get(k) as? JsonObject)?.keys?.toList() ?: emptyList()
    fun preset(k: String, name: String): JsonObject? = ((ls("ss.connSets") as? JsonObject)?.get(k) as? JsonObject)?.get(name) as? JsonObject
    fun savePreset(k: String, name: String, q: JsonObject) {
        val all = LinkedHashMap((ls("ss.connSets") as? JsonObject) ?: JsonObject(emptyMap()))
        all[k] = JsonObject(LinkedHashMap((all[k] as? JsonObject) ?: JsonObject(emptyMap())).apply { put(name, q) }); ls("ss.connSets", JsonObject(all))
    }

    // ---------------- connections in the document ----------------
    fun conns(): List<StConn> = doc.field("stConns")?.let { runCatching { Json.parseToJsonElement(it).jsonArray.map { e -> StConn(e.jsonObject) } }.getOrNull() } ?: emptyList()
    fun conn(id: String): StConn? = conns().firstOrNull { it.id == id }
    private fun setConns(l: List<JsonObject>) = doc.setField("stConns", JsonArray(l).toString())
    private fun stOf(id: BodyId): JsonObject? = doc.bodyJson(id)?.let { runCatching { Json.parseToJsonElement(it).jsonObject["st"] as? JsonObject }.getOrNull() }
    /** The connection a selected component plate or bolt group belongs to. */
    fun connOfBody(id: BodyId): StConn? = stOf(id)?.takeIf { (it["kind"] as? JsonPrimitive)?.contentOrNull.let { k -> k == "conn" || k == "bolts" } }?.let { st -> conns().firstOrNull { it.id == Js.str(st["conn"]) } }
    fun kids(c: StConn): List<BodyId> = doc.bodyIds().filter { id -> stOf(id)?.let { Js.str(it["conn"]) == c.id && (it["kind"] as? JsonPrimitive)?.contentOrNull.let { k -> k == "conn" || k == "bolts" } } == true }

    /** v69 stConnOK. */
    fun ok(c: StConn): Boolean {
        val q = c.q; val d = ui.conn[c.type]
        if (c.type == "TKCUT" || (c.type == "TKBOLT" && Js.truthy(q["holesOnly"])) || c.type == "fitting" || c.type == "weldonly" || (d != null && (d.fam == "WF" || d.fam == "OP"))) return true
        if (((c.raw["welds"] as? JsonArray)?.size ?: 0) > 0) return true
        return kids(c).isNotEmpty()
    }

    /** v69 stConnAdd + stRegen in one undo step (label = the component's name); `pt` = the first picked point. */
    fun add(k: String, parts: List<BodyId>, q: JsonObject, pt: DoubleArray? = null): StConn {
        var out: StConn? = null
        val run = {
            val next = doc.field("stNextConn")?.toDoubleOrNull()?.toLong()?.takeIf { it > 0 } ?: 1L
            val m = linkedMapOf<String, JsonElement>("id" to jsNum(next.toDouble()), "type" to JsonPrimitive(k), "parts" to JsonArray(parts.map { p -> p.value.toLongOrNull()?.let { jsNum(it.toDouble()) } ?: JsonPrimitive(p.value) }), "q" to JsonObject(LinkedHashMap(q)))
            if (pt != null) m["pt"] = jsArr(*pt)
            doc.setField("stNextConn", (next + 1).toString())
            setConns(conns().map { it.raw } + JsonObject(m))
            regen?.regen(doc, parts)
            out = conn(next.toString())
        }
        undo?.group(labelOf(k), run) ?: run()
        return out!!
    }

    /** v69 stConnAdd for each, then one stRegen over all their parts (stPortalConns, stConnectMany): (key, parts, q = def + these). */
    fun addMany(list: List<Triple<String, List<BodyId>, JsonObject>>): List<StConn> {
        val made = ArrayList<StConn>(); var next = doc.field("stNextConn")?.toDoubleOrNull()?.toLong()?.takeIf { it > 0 } ?: 1L
        val raws = ArrayList(conns().map { it.raw })
        for ((k, parts, q) in list) {
            val m = linkedMapOf<String, JsonElement>("id" to jsNum(next.toDouble()), "type" to JsonPrimitive(k), "parts" to JsonArray(parts.map { p -> p.value.toLongOrNull()?.let { jsNum(it.toDouble()) } ?: JsonPrimitive(p.value) }),
                "q" to JsonObject(LinkedHashMap(cq(k)).apply { putAll(q) }))
            raws += JsonObject(m); made += StConn(JsonObject(m)); next++
        }
        doc.setField("stNextConn", next.toString()); setConns(raws)
        regen?.regen(doc, list.flatMap { it.second }.distinct())
        return made.map { conn(it.id) ?: it }
    }

    /** v69 dialog Modify: the targets (one, or all of this type; locked ones skipped) get [newQ] and are rebuilt. Returns the count. */
    fun modify(k: String, targets: List<StConn>, newQ: (StConn) -> JsonObject): Int {
        val ts = targets.filter { Js.num(it.q["locked"]).let { v -> v == 0.0 || v.isNaN() } }
        if (ts.isEmpty()) return 0
        val run = {
            val ids = ts.map { it.id }.toSet()
            setConns(conns().map { x -> if (x.id in ids) JsonObject(LinkedHashMap(x.raw).apply { put("q", newQ(x)) }) else x.raw })
            regen?.regen(doc, ts.flatMap { it.parts }.distinct().map(::BodyId))
        }
        undo?.group("Modify ${ts.size} ${ui.conn[k]?.label?.lowercase() ?: k}", run) ?: run()
        return ts.size
    }

    /** v69 dialog Delete: the connection, its plates and bolts go; its parts are rebuilt without its cuts. */
    fun delete(c: StConn) {
        val run = {
            for (b in kids(c)) doc.removeBody(b)
            val rest = conns().filter { it.id != c.id }
            setConns(rest.map { it.raw })
            val r = regen
            if (r != null) { r.regen(doc, c.parts.map(::BodyId)); for (id in c.parts.map(::BodyId)) { val json = doc.bodyJson(id) ?: continue; val st = stOf(id) ?: continue; runCatching { r.cutPart(id.value, st, rest) }.getOrNull()?.let { doc.replaceBody(id, json, it) } } }
        }
        undo?.group("Delete ${ui.conn[c.type]?.label ?: c.type}", run) ?: run()
    }

    /** The connection's first bolt group st with points (v69 tkBoltPic). */
    fun boltSt(c: StConn): JsonObject? = kids(c).asSequence().mapNotNull { stOf(it) }.firstOrNull { (it["kind"] as? JsonPrimitive)?.contentOrNull == "bolts" && ((it["pts"] as? JsonArray)?.size ?: 0) > 0 }
    fun partLabel(id: String): String = stOf(BodyId(id))?.let { "${Js.str(it["name"])} ${Js.str(it["prof"])}" } ?: id
}

/**
 * The live v58 component dialog's values (`stCompDialog`, L16304): every field of the spec with its input value and its
 * Tekla on/off box, read / fill / Get / on-off, and what Modify and Apply write. [conn] null: defaults for new components.
 */
class CompDialog(val comps: Components, val key: String, var conn: StConn?) {
    val def: CompDef = comps.ui.conn.getValue(key)
    val spec: TkSpec = comps.ui.specs.getValue(key)
    /** The values the dialog was opened with (v69 `q`). */
    var q: MutableMap<String, JsonElement> = LinkedHashMap<String, JsonElement>().apply { if (conn != null) { putAll(def.def); putAll(conn!!.q) } else putAll(comps.cq(key)) }; private set

    /** One input: the field, its current value (as the input shows it), its on/off box (null for table cells). */
    class Input(val f: TkField, var value: JsonElement, var on: Boolean?)
    val inputs = ArrayList<Input>()

    init {
        for (t in spec.tabs) {
            for (f in picFields(t)) if (f.off == null) inputs += Input(f, initial(f), true)
            t.parts?.forEach { row -> for (x in listOf("t", "b", "h")) row.dm[x]?.let { k -> val f = TkField(k, "${row.lbl} $x", "num", emptyList(), null, null, null, null, null, null, null); inputs += Input(f, initial(f), null) }
                val r = if (row.role == "*") "ALL" else row.role
                for ((x, ph) in listOf("pp" to "p", "ps" to "1001", "mat" to "S275", "name" to (if (row.role == "*") "" else row.role), "fin" to "")) { val f = TkField("P.$r.$x", x, if (x == "ps") "num" else "text", emptyList(), null, ph, null, null, null, null, null); inputs += Input(f, initial(f), null) } }
            for ((_, fs) in t.groups) for (f in fs) if (f.off == null) inputs += Input(f, initial(f), true)
            if (t.welds) for (n in weldNames()) { val w0 = weldOf(n)
                // v69: placeholder String(w0 ? w0.a : q.a || '')
                val fa = TkField("W.$n.a", "a", "num", emptyList(), null, if (w0 != null) Js.str(w0["a"]) else if (Js.truthy(q["a"])) Js.str(q["a"]) else "", null, null, null, null, null)
                inputs += Input(fa, initial(fa), null)
                for (f in listOf(TkField("W.$n.type", "type", "sel", listOf(TkOpt(Js.EMPTY, "Fillet", null), TkOpt(JsonPrimitive("Bevel"), "Bevel groove", null), TkOpt(JsonPrimitive("Square"), "Square groove", null), TkOpt(JsonPrimitive("V"), "Single V butt", null)), null, null, null, null, null, null, null),
                        TkField("W.$n.ws", "ws", "sel", listOf(TkOpt(Js.EMPTY, "Workshop", null), TkOpt(JsonPrimitive("Site"), "Site", null)), null, null, null, null, null, null, null))) inputs += Input(f, initial(f), null)
                val fr = TkField("W.$n.around", "around", "chk", emptyList(), null, null, null, null, null, null, null); inputs += Input(fr, chk(get(q, fr).let { if (Js.str(it) == "" ) jsNum(1.0) else it }), null) }
        }
    }

    /** The fields drawn on a tab's picture (v69: dims; for the 3D thumbnail with anchors, the anchor fields). */
    fun picFields(t: TkTab): List<TkField> {
        if (t.pic == null) return emptyList()
        val th = comps.ui.thumbs[key]
        if (t.pic == "icon" && th?.p != null && th.a.isNotEmpty() && t.anchors.isNotEmpty()) return t.anchors.filter { th.a.containsKey(it.k) }.map { f -> TkField(f.k, f.k, f.type, f.opts, f.dv, f.ph, f.note, f.off, f.lbl, th.a[f.k], f.acc) }
        return t.dims.filter { it.off == null }
    }
    fun weldNames(): List<String> { val w = (conn?.raw?.get("welds") as? JsonArray)?.mapNotNull { (it as? JsonObject)?.get("name")?.let(Js::str) }?.distinct() ?: emptyList(); return w.ifEmpty { listOf("*") } }
    fun weldOf(n: String): JsonObject? = (conn?.raw?.get("welds") as? JsonArray)?.map { it as JsonObject }?.firstOrNull { Js.str(it["name"]) == n }

    // ---------------- tkGet / tkSet / tkInput ----------------
    fun get(q: Map<String, JsonElement>, f: TkField): JsonElement {
        f.acc?.let { a -> return accessor(a).get(q, f.k) }
        if (f.k.startsWith("P.") || f.k.startsWith("W.")) { val (o, r, x) = f.k.split(".", limit = 3); return ((q[o] as? JsonObject)?.get(r) as? JsonObject)?.get(x)?.takeIf { it !is JsonNull } ?: Js.EMPTY }
        return q[f.k] ?: f.dv ?: Js.EMPTY
    }
    fun set(q: MutableMap<String, JsonElement>, f: TkField, v: JsonElement) {
        f.acc?.let { a -> accessor(a).set(q, f.k, v); return }
        if (f.k.startsWith("P.") || f.k.startsWith("W.")) { val (o, r, x) = f.k.split(".", limit = 3)
            val O = LinkedHashMap((q[o] as? JsonObject) ?: JsonObject(emptyMap())); val R = LinkedHashMap((O[r] as? JsonObject) ?: JsonObject(emptyMap()))
            if ((v as? JsonPrimitive)?.let { it.isString && it.content == "" } == true) R.remove(x) else R[x] = v
            O[r] = JsonObject(R); q[o] = JsonObject(O); return }
        q[f.k] = v
    }
    private fun accessor(a: String): Acc.A = Acc.KNOWN[comps.ui.acc.getValue(a)] ?: error("v69 accessor $a not ported")

    /** v69 tkInput's starting text / choice for a value (TK_ZERO_AUTO fields show 0 as empty, "auto"). */
    private fun initial(f: TkField): JsonElement { val v = get(q, f); return when (f.type) { "chk" -> chk(v); "sel" -> v; else -> if (f.k in ZERO_AUTO && Js.str(v) == "0") Js.EMPTY else v } }
    private fun chk(v: JsonElement) = jsNum(if (Js.str(v) == "0" || Js.str(v) == "false") 0.0 else 1.0)
    fun placeholder(f: TkField): String? = f.ph ?: if (f.k in ZERO_AUTO) "auto" else null
    /** The text a text box shows for its value. */
    fun text(i: Input): String = Js.str(i.value).let { if (i.value is JsonNull) "" else it }
    /** v69 `i.val()`: number fields parse the text (comma as point; '' stays ''), text and list fields keep it trimmed. */
    fun setText(i: Input, s: String) { i.value = JsonPrimitive(s) }
    fun valOf(i: Input): JsonElement = when (i.f.type) {
        "chk" -> chk(i.value); "sel" -> i.value
        "text", "list" -> JsonPrimitive(Js.str(i.value).trim())
        else -> { val s = Js.str(i.value).trim(); if (s.isEmpty() || i.value is JsonNull) Js.EMPTY else com.solidsketchapp.steel.model.StGrid.jsNumber(s.replaceFirst(',', '.')).let { if (it.isFinite()) Js.n(it) else Js.EMPTY } }
    }

    /** v69 read(allFields): q with every input (unticked ones only when [allFields]); `_` keys dropped. */
    fun read(allFields: Boolean): JsonObject {
        val o = LinkedHashMap(q); val chg = inputs.filter { Js.str(valOf(it)) != Js.str(get(q, it.f)) }
        for (i in inputs + chg) { if (!allFields && i.on == false) continue; set(o, i.f, valOf(i)) }
        o.keys.filter { it.startsWith("_") }.forEach { o.remove(it) }
        return JsonObject(o)
    }
    /** v69 fill: inputs show [q]. */
    fun fill() { for (i in inputs) i.value = if (i.f.type == "chk") chk(get(q, i.f)) else initial(i.f) }
    /** v69 Load (a preset, or "standard" = the defaults for new components). */
    fun load(name: String): Boolean { val v = if (name == "standard") comps.cq(key) else comps.preset(key, name) ?: return false; q = LinkedHashMap<String, JsonElement>(def.def).apply { putAll(v) }; fill(); return true }
    /** v69 Save: "standard" saves the defaults for new components, else the preset. */
    fun save(name: String) { if (name == "standard") comps.setDefaults(key, read(true)) else comps.savePreset(key, name, read(true)) }
    fun apply() = comps.setDefaults(key, read(true))
    /** v69 Get: the values of [c] (a component of this type). */
    fun getFrom(c: StConn): Boolean { if (c.type != key) return false; q = LinkedHashMap<String, JsonElement>(def.def).apply { putAll(c.q) }; conn = c; fill(); return true }
    private var allOn = true
    fun toggleAll() { allOn = !allOn; for (i in inputs) if (i.on != null) i.on = allOn }

    /** v69 Modify: per target, its own q with every ticked input applied over (CONN def + its q), `_` keys dropped. */
    fun modify(allOfType: Boolean): Int {
        val targets = if (allOfType) comps.conns().filter { it.type == key } else listOfNotNull(conn)
        val q0 = LinkedHashMap(q)
        val n = comps.modify(key, targets) { x ->
            val base = LinkedHashMap<String, JsonElement>(def.def).apply { putAll(x.q) }; val out = LinkedHashMap(x.q)
            val chg = inputs.filter { Js.str(valOf(it)) != Js.str(get(q0, it.f)) }
            for (i in inputs + chg) { if (i.on == false) continue; val tmp = LinkedHashMap(base).apply { putAll(out) }; set(tmp, i.f, valOf(i)); out.putAll(tmp) }
            out.keys.filter { it.startsWith("_") }.forEach { out.remove(it) }
            JsonObject(out)
        }
        conn?.let { c -> comps.conn(c.id)?.let { conn = it } }
        return n
    }

    companion object { val ZERO_AUTO = setOf("gx", "gy", "rows", "Lp", "Wp", "hb", "ph", "pw", "len", "depth", "gauge2") }
}
