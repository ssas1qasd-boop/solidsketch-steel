package com.solidsketchapp.steel.mode

import com.solidsketchapp.core.api.BodyId
import com.solidsketchapp.core.api.DocumentApi
import com.solidsketchapp.core.api.SelectionApi
import com.solidsketchapp.core.api.UndoApi
import com.solidsketchapp.core.api.Vec3
import com.solidsketchapp.steel.data.SteelData
import com.solidsketchapp.steel.geom.D3
import com.solidsketchapp.steel.geom.StBuild
import com.solidsketchapp.steel.model.StGrid
import com.solidsketchapp.steel.model.StParts
import com.solidsketchapp.steel.model.jsArr
import com.solidsketchapp.steel.model.jsNum
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlin.math.hypot
import kotlin.math.roundToLong

/**
 * Steel mode (web v69 v32 L13692–13826): Grid, work levels, Column / Beam / Plate tools that pick part ends and grid
 * points, the property sets (`ST_SETS` + `stPropSheet`), handles that move a part's ends, Exit Steel.
 * Plain Kotlin over the :core-api interfaces; the Compose screen ([SteelPanel]) only shows this state.
 *
 * [project] maps a model point (mm) to a screen pixel (null if behind the camera); [ray] is the pick ray of a pixel.
 * [px] is how many screen pixels v69's CSS pixel is (the phone's density), for the 26 px snap and 24 px handle radii.
 */
class SteelMode(
    val data: SteelData,
    private val doc: DocumentApi,
    private val undo: UndoApi?,
    private val selection: SelectionApi?,
    build: StBuild,
    private val project: (Vec3) -> Pair<Float, Float>?,
    private val ray: (Float, Float) -> Pair<Vec3, Vec3>?,
    private val px: Float = 1f,
    private val changed: () -> Unit = {},
    private val regen: com.solidsketchapp.steel.conn.StRegen? = null,
    /** S7: the component catalog, dialogs and presets (null until the UI data is loaded). */
    var comps: com.solidsketchapp.steel.comp.Components? = null,
) {
    val parts = StParts(data, doc, undo, build, regen)
    enum class Tool { COLUMN, BEAM, PLATE }

    var on = false; private set
    var tool: Tool? = null; private set
    var set = "COLUMN"; private set
    /** The properties used for new parts (v69 `ST.props`): a copy of a property set, changed by the sheet. */
    var props: MutableMap<String, JsonElement> = LinkedHashMap(data.propertySet("COLUMN")!!.raw); private set
    val pts = mutableListOf<D3>()
    var level = 0; private set
    var arm: Pair<BodyId, String>? = null; private set
    /** Column top in mm as typed in the property sheet ("" = the next level up). */
    var colTop = ""
    var say = ""; private set
    /** Saved settings (v69 keeps them in localStorage; here for the session). */
    private val sets = LinkedHashMap<String, MutableMap<String, JsonElement>>().apply { for (p in data.propertySets) put(p.name, LinkedHashMap(p.raw)) }

    val grid: StGrid? get() = StGrid.fromJson(doc.field("stGrid")?.let { runCatching { Json.parseToJsonElement(it) as? JsonObject }.getOrNull() })
    val propsName: String get() = (props["name"] as? JsonPrimitive)?.contentOrNull ?: ""
    val propsProf: String get() = (props["prof"] as? JsonPrimitive)?.contentOrNull ?: ""

    // ---------------- mode, tools, sets ----------------

    /** v69 stToggle: on → a grid if the document has none ("0 6*6000", "0 20000", "0 7000"); off → tools cleared. */
    fun toggle() {
        on = !on; tool = null; pts.clear(); arm = null; catK = null; pick.clear(); pickPts.clear()
        if (on && grid == null) doc.setField("stGrid", StGrid.DEFAULT.toJson().toString())
        // v69 stToggle: the message is a toast (the app shows it); the hint line keeps stHint ("Pick a tool, …")
        say = ""; if (on) lastToast = "Steel: grid, columns, beams and plates the Tekla way"
        changed()
    }

    /** v69 stToggle: with no steel parts yet, the view looks at the grid's centre from 1.6 × its span (the view direction
     *  stays); null when the model has steel parts (then v69 leaves the camera as it is). */
    fun gridView(): Pair<Vec3, Double>? {
        val g = grid ?: return null; if (g.xs.isEmpty() || g.ys.isEmpty()) return null
        if (doc.bodyIds().any { stOf(it) != null }) return null
        val span = maxOf(g.xs.max() - g.xs.min(), g.ys.max() - g.ys.min())
        return Vec3((g.xs.min() + g.xs.max()) / 2, (g.ys.min() + g.ys.max()) / 2, 0.0) to span * 1.6
    }

    /** v69 S.stGen: the portal frame's last values (null before one is made). */
    fun stGen(): JsonObject? = doc.field("stGen")?.let { runCatching { Json.parseToJsonElement(it) as? JsonObject }.getOrNull() }

    /** v69 stPortal: the portal frame from the sheet's values; returns v69's toast. */
    fun portal(q: JsonObject): String {
        val cs = comps ?: return "Components are still loading"
        val r = com.solidsketchapp.steel.apps.PortalFrame.run(q, data, doc, parts, cs, undo)
        if (r.parts != null) { selection?.setSelected(emptyList()) }
        changed(); return r.message
    }

    fun setGrid(g: StGrid): String? {
        if (g.xs.isEmpty() || g.ys.isEmpty()) return "X and Y need at least one line each"
        undo?.group("Grid") { doc.setField("stGrid", g.toJson().toString()) } ?: doc.setField("stGrid", g.toJson().toString())
        level = minOf(level, maxOf(0, g.zs.size - 1))
        val nz = g.zs.size
        say = "Grid ${g.xs.size} × ${g.ys.size} lines, $nz level${if (nz == 1) "" else "s"}"; changed(); return null
    }

    fun setLevel(i: Int) { level = i; changed() }

    /** v69 tool buttons: tapping the active tool turns it off; Column uses COLUMN, Plate PLATE, Beam keeps a beam-like set. */
    fun pickTool(t: Tool) {
        tool = if (tool == t) null else t; pts.clear(); arm = null; catK = null; pick.clear()
        if (tool != null) useSet(when (t) { Tool.COLUMN -> "COLUMN"; Tool.PLATE -> "PLATE"; Tool.BEAM -> if (set == "COLUMN" || set == "PLATE") "BEAM" else set })
        changed()
    }

    fun useSet(k: String) { set = k; props = LinkedHashMap(sets.getValue(k)); changed() }
    fun applyProps(p: Map<String, JsonElement>) { props = LinkedHashMap(props).apply { putAll(p) }; changed() }
    fun saveAsSet(p: Map<String, JsonElement>) { sets.getValue(set).putAll(p); say = "Saved as the ${set.lowercase()} settings"; changed() }
    fun setValues(k: String): Map<String, JsonElement> = sets.getValue(k)

    fun cancelPoints() { pts.clear(); changed() }

    // ---------------- components (v69 v38 stCatPick / stTap, L14402) ----------------

    /** The component being placed (v69 ST.tool 'cat', ST.catK) and the parts picked so far. */
    var catK: String? = null; private set
    val pick = mutableListOf<BodyId>(); private val pickPts = mutableListOf<DoubleArray>()

    /** v69 stCatPick: start picking the parts of component [k]; it goes to the top of "Recently used". */
    fun catPick(k: String) {
        val c = comps ?: return
        tool = null; pts.clear(); arm = null; catK = k; pick.clear(); pickPts.clear(); c.noteRecent(k); say = c.prompt(k, 0); changed()
    }
    /** v69 Done (and Escape): stop placing components. */
    fun catDone() { catK = null; pick.clear(); pickPts.clear(); say = ""; changed() }

    private fun catTap(x: Float, y: Float): Boolean {
        if (comps == null) return true
        val hit = selection?.pick(x, y)
        return catHit(hit?.body, hit?.let { doubleArrayOf(it.point.x, it.point.y, it.point.z) })
    }
    /** One picked part of the component being placed (v69 stTap's component branch): [body] at [point]; also the way a test or an
     *  accessibility action picks a part it knows, the same steps as a tap on it. */
    fun catHit(body: BodyId?, point: DoubleArray?): Boolean {
        val c = comps ?: return true; val k = catK ?: return true; val d = c.ui.conn.getValue(k)
        val st = body?.let { stOf(it) }
        if (body == null || st == null || (st["kind"] as? JsonPrimitive)?.contentOrNull != "member") { say = "Pick a steel part · " + c.prompt(k, pick.size); changed(); return true }
        if (body in pick) { say = "Already picked · " + c.prompt(k, pick.size); changed(); return true }
        pick += body; pickPts += (point ?: doubleArrayOf(0.0, 0.0, 0.0))
        if (pick.size < d.parts.size) { say = c.prompt(k, pick.size); changed(); return true }
        val parts = pick.toList(); val pps = pickPts.toList(); val p0 = pps.firstOrNull(); pick.clear(); pickPts.clear()
        if (d.gen) { val g = com.solidsketchapp.steel.comp.GenRun(data, doc, undo, this.parts, c, regen)
            // v69 stGenRun kind 'portal' (L15174): the Portal frame sheet opens
            if (g.s10(k)) { sheetRequest = "portal"; say = "${d.label} done · ${c.prompt(k, 0)} · Done to stop"; changed(); return true }
            // v69 stGenRun (L15170), then L14409's prompt
            runCatching { g.run(k, parts, pps) }.fold({ t -> if (t != null) lastToast = t }, { lastToast = it.message ?: "Error" })
            say = "${d.label} done · ${c.prompt(k, 0)} · Done to stop"; changed(); return true }
        val cn = runCatching { c.add(k, parts, c.cq(k), p0) }.getOrElse { say = it.message ?: "Error"; changed(); return true }
        val ok = c.ok(cn)
        say = "${d.label} ${if (ok) "created" else "failed (red symbol): check the parts"} · ${c.prompt(k, 0)} · Done to stop"
        lastToast = if (ok) "${d.label} created" else "${d.label}: could not be built for these parts"
        changed(); return true
    }
    /** The last toast text (v69 toast), shown once by the screen. */
    var lastToast: String? = null
    /** v69 ST.sym: component symbols shown (on by default since v49). */
    var symbols = true
    fun refresh() = changed()
    /** S7: the rule checks per component and the symbol places (v69 tkStatus, stSymPos). */
    var checks: com.solidsketchapp.steel.comp.TkCheck? = null
    /** A component's status for the dialog and symbols: 1 OK, 2 warning, 3 error, and the messages (v69 tkStatus). */
    fun status(c: com.solidsketchapp.steel.conn.StConn): Pair<Int, List<String>> {
        val cs = comps ?: return 3 to emptyList()
        checks?.let { k -> val r = k.status(c); return r.s to r.msgs }
        val lbl = cs.ui.conn[c.type]?.tekla ?: c.type
        return if (cs.ok(c)) 1 to emptyList() else 3 to listOf("$lbl #${c.id}: the component could not be created on these parts")
    }

    // ---------------- component symbols (v58 stSymDraw / stSymHit / tkSelect / tkFanDraw / tkCheckStep) ----------------

    /** The selected component (v69 tkSel): its symbol is highlighted, lines go to its parts, the status line shows its checks. */
    var selConn: String? = null; private set
    /** A dialog the screen should open (v69: tapping a symbol opens the component's dialog). */
    var dialogRequest: Pair<String, String?>? = null
    /** A sheet the panel should open (v69 stGenRun kind 'portal' → stPortalSheet). */
    var sheetRequest: String? = null
    fun selectConn(id: String?) { selConn = id; changed() }
    class Sym(val id: String, val p: D3, val s: Int, val x: Float, val y: Float)
    private var symCache: Pair<Int, List<Pair<com.solidsketchapp.steel.conn.StConn, D3>>>? = null
    private var docRev = 0
    init { doc.addListener { docRev++ } }
    private fun symBase(): List<Pair<com.solidsketchapp.steel.conn.StConn, D3>> {
        symCache?.takeIf { it.first == docRev }?.let { return it.second }
        val cs = comps ?: return emptyList(); val k = checks ?: return emptyList()
        val l = cs.conns().filter { it.type != "TKCUT" && it.type != "TKBOLT" }.mapNotNull { c -> k.symPos(c)?.let { c to it } }
        symCache = docRev to l; return l
    }
    /** The symbols on screen: status colour (checked, else built / failed), one per 24 px cell, errors then warnings first, the selected one always. */
    fun symbolsOnScreen(): List<Sym> {
        if (!on || !symbols) return emptyList(); val cs = comps ?: return emptyList(); val k = checks
        val list = symBase().mapNotNull { (c, p) -> val s = k?.cached(c.id)?.s ?: if (cs.ok(c)) 1 else 3; project(Vec3(p.x, p.y, p.z))?.let { (x, y) -> Sym(c.id, p, s, x, y) } }
            .sortedWith(compareByDescending<Sym> { it.s }.thenByDescending { it.id == selConn })
        val used = HashSet<Long>(); val cell = 24f * px; val out = ArrayList<Sym>()
        for (sy in list) { val key = (Math.floor((sy.x / cell).toDouble()).toLong() shl 32) xor Math.floor((sy.y / cell).toDouble()).toLong().and(0xffffffffL)
            if (key in used && sy.id != selConn) continue; used += key; out += sy }
        return out
    }
    /** v69 tkCheckStep: checks components that have none yet, for about 30 ms; true when more remain. */
    fun checkStep(): Boolean {
        val cs = comps ?: return false; val k = checks ?: return false; val t0 = System.nanoTime(); var changedAny = false
        val todo = symBase().map { it.first }.filter { k.cached(it.id) == null }
        for (c in todo) { k.status(c); changedAny = true; if ((System.nanoTime() - t0) / 1e6 > 30) break }
        if (changedAny) changed()
        return symBase().any { k.cached(it.first.id) == null }
    }
    /** v69 stSymHit: the visible symbol within 28 px of the tap (touch). */
    private fun symAt(x: Float, y: Float): com.solidsketchapp.steel.conn.StConn? {
        var best: Sym? = null; var bd = 28f * px
        for (s in symbolsOnScreen()) { val d = hypot(s.x - x, s.y - y); if (d < bd) { bd = d; best = s } }
        return best?.let { comps?.conn(it.id) }
    }
    /** The status line for the selected component (v69 #tk-status): colour class and text. */
    fun statusLine(): Pair<Int, String>? { val id = selConn ?: return null; val cs = comps ?: return null; val c = cs.conn(id) ?: return null
        val r = status(c); return r.first to (if (r.second.isNotEmpty()) r.second.joinToString("  ·  ") else "${cs.ui.conn[c.type]?.tekla ?: c.type} #${c.id}: OK") }
    /** v69 tkFanDraw: the selected component's lines to its parts. */
    fun fanLines(): List<Pair<D3, D3>> { val id = selConn ?: return emptyList(); val c = comps?.conn(id) ?: return emptyList(); return checks?.fan(c) ?: emptyList() }

    // ---------------- picking ----------------

    data class Pick(val p: D3, val kind: String)

    private fun memberEnds(): List<Pick> = doc.bodyIds().mapNotNull { id -> stOf(id) }.filter { (it["kind"] as? JsonPrimitive)?.contentOrNull == "member" }
        .flatMap { st -> val a = vec(st["p1"]); val b = vec(st["p2"]); if (a == null || b == null) emptyList() else listOf(Pick(a, "end"), Pick(b, "end"), Pick(a.lerp(b, 0.5), "mid")) }

    /** v69 stPick: part ends (4 px bonus) and middles, grid points on every level, within 26 px; else the work plane, on a 10 mm raster. */
    fun pick(x: Float, y: Float): Pick? {
        val cands = memberEnds().toMutableList()
        grid?.let { g -> for (z in g.zs.ifEmpty { listOf(0.0) }) for (gx in g.xs) for (gy in g.ys) cands += Pick(D3(gx, gy, z), "grid") }
        var best: Pick? = null; var bd = 26f * px
        for (c in cands) { val s = project(Vec3(c.p.x, c.p.y, c.p.z)) ?: continue; val d = hypot(s.first - x, s.second - y) - (if (c.kind == "end") 4f * px else 0f); if (d < bd) { bd = d; best = c } }
        if (best != null) return best
        val r = ray(x, y) ?: return null
        val zs = grid?.zs ?: emptyList(); val wz = if (zs.isNotEmpty()) zs[minOf(level, zs.size - 1)] else 0.0
        val (o, d) = r; if (d.z == 0.0) return null
        val t = (wz - o.z) / d.z; if (t < 0) return null
        val hx = o.x + d.x * t; val hy = o.y + d.y * t
        return Pick(D3(Math.round(hx / 10) * 10.0, Math.round(hy / 10) * 10.0, wz), "plane")
    }

    // ---------------- taps ----------------

    /** v69 stTap: returns true when Steel used the tap (the viewport then does not pick a body). */
    fun tap(x: Float, y: Float): Boolean {
        if (!on) return false
        if (catK != null) return catTap(x, y)
        // v69 (v49 stTap + v58 stSymHit): with no tool, a tap on a component symbol selects it and opens its dialog
        if (tool == null && arm == null && comps != null) {
            val c = symAt(x, y)
            if (c != null) { selConn = c.id; dialogRequest = c.type to c.id; lastToast = "${comps?.ui?.conn?.get(c.type)?.tekla ?: c.type} · connection ${c.id}"; changed(); return true }
            if (selConn != null) { selConn = null; changed() }
        }
        val sel = selection?.selected()?.firstOrNull()
        val selSt = sel?.let { stOf(it) }
        if (tool == null && selSt != null && (selSt["kind"] as? JsonPrimitive)?.contentOrNull == "member") {
            for (k in listOf("p1", "p2")) { val p = vec(selSt[k]) ?: continue; val s = project(Vec3(p.x, p.y, p.z)) ?: continue
                if (hypot(s.first - x, s.second - y) < 24f * px) { arm = sel to k; say = "Tap the new ${if (k == "p1") "start (yellow)" else "end (magenta)"} point"; changed(); return true } }
        }
        arm?.let { a -> val pk = pick(x, y); arm = null; say = ""
            if (pk != null) runCatching { parts.modify(listOf(a.first), mapOf(a.second to arr(pk.p)), "Move part end") }.onFailure { say = it.message ?: "Error" }
            changed(); return true }
        val t = tool ?: return false
        val pk = pick(x, y) ?: return true
        when (t) {
            Tool.COLUMN -> {
                val zs = grid?.zs ?: emptyList()
                val top = colTop.trim().takeIf { it.isNotEmpty() }?.let { StGrid.jsNumber(it) }
                    ?: zs.filter { it > pk.p.z + 1e-6 }.minOrNull() ?: (pk.p.z + 3000)
                add(memberSt(pk.p, D3(pk.p.x, pk.p.y, top)), "Column $propsProf")
            }
            Tool.BEAM -> { pts += pk.p; if (pts.size == 2) { val (a, b) = pts.toList(); pts.clear(); add(memberSt(a, b), "${propsName.take(1)}${propsName.drop(1).lowercase()} $propsProf") } }
            Tool.PLATE -> {
                val first = pts.firstOrNull()?.let { project(Vec3(it.x, it.y, it.z)) }
                if (pts.size >= 3 && first != null && hypot(first.first - x, first.second - y) < 24f * px) { finishPlate(); return true }
                pts += pk.p
            }
        }
        changed(); return true
    }

    /** v69 stTypedPoint: "dx dy dz" in mm from the last picked point. */
    fun typedPoint(txt: String): String? {
        val v = txt.trim().split(Regex("""[\s,]+""")).map { StGrid.jsNumber(it) }
        if (pts.isEmpty() || v.isEmpty() || v.any { !it.isFinite() }) return "Type dx dy dz in mm, e.g. 10000 0 1763"
        val a = pts.last(); val p = D3(a.x + v.getOrElse(0) { 0.0 }, a.y + v.getOrElse(1) { 0.0 }, a.z + v.getOrElse(2) { 0.0 })
        if (tool == Tool.BEAM) { pts += p; if (pts.size == 2) { val (s, e) = pts.toList(); pts.clear(); add(memberSt(s, e), "$propsName $propsProf") } } else if (tool == Tool.PLATE) pts += p
        changed(); return null
    }

    /** v69 stPlateFinish: a contour plate on the first corner's level. */
    fun finishPlate(): String? {
        if (pts.size < 3) return "A plate needs at least 3 points".also { say = it; changed() }
        val pr = data.profiles.lookup(propsProf)
        if (pr == null || ((pr.t ?: 0.0) == 0.0 && (pr.h ?: 0.0) == 0.0)) return "Plate profile: PL and the thickness, e.g. PL20".also { say = it; changed() }
        val z = pts[0].z
        val st = stFor(JsonPrimitive("plate"), linkedMapOf("pts" to JsonArray(pts.map { jsArr(it.x, it.y) }), "z" to jsNum(z)))
        pts.clear(); add(st, "Contour plate ${pr.key}"); changed(); return null
    }

    private fun add(st: JsonObject, label: String) {
        runCatching { val id = parts.add(st, label); selection?.setSelected(listOf(id)); say = "" }.onFailure { say = it.message ?: "Error" }
    }

    /** v69 stPropsFor: the part fields in v69's order (kind first, numbers as numbers). */
    private fun stFor(kind: JsonPrimitive, tail: Map<String, JsonElement>): JsonObject {
        val P = props; val m = LinkedHashMap<String, JsonElement>()
        m["kind"] = kind
        fun s(k: String) = P[k] ?: JsonPrimitive("")
        fun n(k: String) = jsNum(StBuild.jsNum(P[k]))
        m["name"] = s("name"); m["prof"] = s("prof"); m["mat"] = s("mat"); m["cls"] = n("cls"); m["fin"] = s("fin")
        m["pp"] = s("pp"); m["ps"] = n("ps"); m["ap"] = s("ap"); m["as"] = n("as"); m["plane"] = s("plane"); m["pOff"] = n("pOff")
        m["rot"] = s("rot"); m["rAng"] = n("rAng"); m["depth"] = s("depth"); m["dOff"] = n("dOff"); m["dx1"] = n("dx1"); m["dx2"] = n("dx2")
        m.putAll(tail); return JsonObject(m)
    }
    private fun memberSt(a: D3, b: D3) = stFor(JsonPrimitive("member"), linkedMapOf("p1" to arr(a), "p2" to arr(b)))
    private fun arr(p: D3) = jsArr(p.x, p.y, p.z)

    fun stOf(id: BodyId): JsonObject? = doc.bodyJson(id)?.let { runCatching { Json.parseToJsonElement(it).jsonObject["st"] as? JsonObject }.getOrNull() }
    private fun vec(e: JsonElement?): D3? = (e as? JsonArray)?.takeIf { it.size >= 3 }?.let { D3(StBuild.jsNum(it[0]), StBuild.jsNum(it[1]), StBuild.jsNum(it[2])) }

    /** v69 stHint: the help line under the panel. */
    fun hint(): String {
        if (say.isNotEmpty()) return say
        if (arm != null) return "Tap the new point for the end you picked"
        catK?.let { k -> comps?.let { return it.prompt(k, pick.size) } }
        return when (tool) {
            Tool.COLUMN -> "Column $propsProf: tap a grid point · top at ${if (colTop.isNotBlank()) "$colTop mm" else "the next level up"}"
            Tool.BEAM -> if (pts.isNotEmpty()) "$propsName $propsProf: tap the end point, or type dx dy dz (mm)" else "$propsName $propsProf: tap the start point (part ends and grid points snap)"
            Tool.PLATE -> if (pts.size >= 3) "Plate $propsProf: ${pts.size} corners · tap the first corner or Finish" else "Plate $propsProf: tap the corners on the work level"
            null -> {
                val st = selection?.selected()?.firstOrNull()?.let { stOf(it) }
                if (st != null) "${str(st, "name")} · ${str(st, "prof")} · ${str(st, "mat")} · tap a yellow / magenta handle to move that end"
                else "Pick a tool, or tap a steel part to see its properties"
            }
        }
    }
    private fun str(o: JsonObject, k: String) = (o[k] as? JsonPrimitive)?.contentOrNull ?: ""

    /** v69 "Work level" buttons: "+0", "+7500", … */
    /** A grid label (v69 stGridDraw sprites) at its model point: circled line numbers / letters, level labels. */
    class GridLabel(val text: String, val p: Vec3, val ring: Boolean)
    /** v69 stGridDraw: X labels 0.6 × 900 mm beyond the start of each X line (the lines run 1500 mm past the outer Y lines), Y labels
     *  likewise at each Y line's start, level labels 900 mm past the far corner of each level. Projected when drawn. */
    fun gridLabels(): List<GridLabel> {
        val g = grid ?: return emptyList(); if (g.xs.isEmpty() || g.ys.isEmpty()) return emptyList()
        val zs = g.zs.ifEmpty { listOf(0.0) }; val sz = 900.0; val ext = 1500.0
        val x0 = g.xs.min() - ext; val x1 = g.xs.max() + ext; val y0 = g.ys.min() - ext; val y1 = g.ys.max() + ext
        return g.xs.mapIndexed { i, x -> GridLabel(g.xLabel(i), Vec3(x, y0 - sz * 0.6, zs[0]), true) } +
            g.ys.mapIndexed { i, y -> GridLabel(g.yLabel(i), Vec3(x0 - sz * 0.6, y, zs[0]), true) } +
            g.zs.mapIndexed { i, z -> GridLabel(g.levelLabel(i), Vec3(x1 + sz, y1, z), false) }
    }

    fun levelLabels(): List<String> = grid?.let { g -> g.zs.indices.map(g::levelLabel) } ?: listOf("+0")

    /** Picked points, the selected member's handles (yellow p1, magenta p2), for the screen overlay. */
    fun markers(): List<Pair<D3, Int>> {
        val out = pts.map { it to 0xFF2F6FB3.toInt() }.toMutableList()
        if (on && tool == null) selection?.selected()?.firstOrNull()?.let { stOf(it) }?.let { st ->
            if ((st["kind"] as? JsonPrimitive)?.contentOrNull == "member") { vec(st["p1"])?.let { out += it to 0xFFFFD400.toInt() }; vec(st["p2"])?.let { out += it to 0xFFE0287E.toInt() } } }
        return out
    }

    fun selectionFirst(): BodyId? = selection?.selected()?.firstOrNull()?.takeIf { stOf(it) != null }
    fun bodyName(id: BodyId): String = doc.bodyJson(id)?.let { runCatching { (Json.parseToJsonElement(it).jsonObject["name"] as? JsonPrimitive)?.contentOrNull }.getOrNull() } ?: ""
    /** v69 stPropSheet: a selected member that is vertical (|a·Z| > 0.995), or the COLUMN set with nothing selected. */
    fun isColumn(st: JsonObject?): Boolean {
        if (st == null) return set == "COLUMN"
        if ((st["kind"] as? JsonPrimitive)?.contentOrNull != "member") return false
        val a = vec(st["p1"]) ?: D3.ZERO; val b = vec(st["p2"]) ?: D3.Z
        return kotlin.math.abs((b - a).normalize().z) > 0.995
    }

    companion object {
        fun mm(d: Double): String = if (d == Math.rint(d)) d.roundToLong().toString() else d.toString()
    }
}
