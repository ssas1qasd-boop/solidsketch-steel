package com.solidsketchapp.steel.mode

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.solidsketchapp.core.api.Fmt
import com.solidsketchapp.core.api.Vec3
import com.solidsketchapp.steel.model.StGrid
import com.solidsketchapp.steel.model.jsNum
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** v69 Steel buttons (`#st-panel button`): pill, one line; "on" = #c0392b with white text. */
@Composable
fun StBtn(label: String, on: Boolean = false, enabled: Boolean = true, padH: Int = 12, padV: Int = 7, onClick: () -> Unit) {
    // v69 L13730: border 1px rgba(127,127,127,.35), transparent, colour inherit, radius 999, padding 7 × 12, 14 px; .on #c0392b / white.
    // CSS sizes: the 1 px border adds to the padding, the 14 px Roboto line is 16 px (UI golden: 32 × text + 26 px boxes).
    // padH / padV: other v69 paddings (catalog rows: 5 × 9).
    val c = com.solidsketchapp.steel.ui.LocalSs.current
    Box(Modifier.height(androidx.compose.ui.unit.Dp((16 + 2 * padV + 2).toFloat())).background(if (on) c.on else Color.Transparent, RoundedCornerShape(999.dp)).border(1.dp, if (on) c.on else c.pillBorder, RoundedCornerShape(999.dp))
        .clickable(enabled = enabled) { onClick() }, contentAlignment = Alignment.Center) {
        com.solidsketchapp.steel.ui.CssText(label, com.solidsketchapp.steel.ui.cssLine(16).copy(fontSize = 14.sp, color = (if (on) Color.White else c.fg).copy(alpha = if (enabled) 1f else 0.45f)), pad = (padH + 1).dp)
    }
}

/** Grid labels (v69 sprites: circled 1, 2, 3… / A, B, C… at the ends of the lines; level labels) and the picked points / handles. */
@Composable
fun SteelOverlay(mode: SteelMode, tick: Int, project: (Vec3) -> Pair<Float, Float>?, modifier: Modifier = Modifier,
                 /** The camera's revision. Read while drawing, so every camera change (orbit, pan, zoom, resize) redraws the labels,
                  *  handles and cones even when this composable itself is skipped (S9b regression: they stayed where they were first drawn). */
                 viewRev: () -> Int = { 0 },
                 /** Test hook: the grid labels as drawn (text, x, y in px). */ onDrawn: ((List<Triple<String, Float, Float>>) -> Unit)? = null,
                 /** Screen px per mm at a model point (the camera's perspective). v69's grid labels are sprites 900 mm across
                  *  (levels 1440 mm), so they grow and shrink with the zoom; without it they are drawn 26 dp across. */
                 scaleAt: ((Vec3) -> Float?)? = null,
                 /** Test hook: each drawn label's size in px (v69's sprite size on screen). */ onLabelSizes: ((List<Float>) -> Unit)? = null,
                 /** Test hook: the handles / picked points (x, y) and component cones (x, y) as drawn. */ onDrawnMarks: ((List<Pair<Float, Float>>, List<Pair<Float, Float>>) -> Unit)? = null) {
    @Suppress("UNUSED_EXPRESSION") tick
    val tm = rememberTextMeasurer()
    if (!mode.on) return
    Canvas(modifier.fillMaxSize()) {
        viewRev()
        val drawn = ArrayList<Triple<String, Float, Float>>(); val sizes = ArrayList<Float>()
        // v69 stLabelSprite: a 128 px canvas (ring r 58, stroke 6, #fff / #3a4250; text 600, 54 px or 34 px for longer labels,
        // #1d2330, centred 2 px low) on a sprite 900 mm across (levels: 1440 mm, no ring)
        fun label(p: Vec3, text: String, ring: Boolean) {
            val s = project(p) ?: return; val c = Offset(s.first, s.second); drawn += Triple(text, c.x, c.y)
            val S = scaleAt?.invoke(p)?.let { it * (if (ring) 900f else 1440f) } ?: 26.dp.toPx(); sizes += S
            val k = S / 128f
            if (ring) { drawCircle(Color.White, 58f * k, c); drawCircle(Color(0xFF3A4250), 58f * k, c, style = Stroke(6f * k)) }
            val fpx = (if (text.length > 3) 34f else 54f) * k; if (fpx < 2f) return
            val lay = tm.measure(text, TextStyle(fontSize = (fpx / density / fontScale).sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1D2330), textMotion = androidx.compose.ui.text.style.TextMotion.Animated))
            drawText(lay, topLeft = Offset(c.x - lay.size.width / 2f, c.y + 2f * k - lay.size.height / 2f))
        }
        for (l in mode.gridLabels()) label(l.p, l.text, l.ring)
        onDrawn?.invoke(drawn); onLabelSizes?.invoke(sizes)
        val marks = ArrayList<Pair<Float, Float>>()
        for ((p, argb) in mode.markers()) project(Vec3(p.x, p.y, p.z))?.let { marks += it; drawCircle(Color(argb), 6.dp.toPx(), Offset(it.first, it.second)) }
        // v58 tkFanDraw: dashed yellow lines from the selected component's symbol to its parts
        for ((a, b) in mode.fanLines()) { val sa = project(Vec3(a.x, a.y, a.z)) ?: continue; val sb = project(Vec3(b.x, b.y, b.z)) ?: continue
            drawLine(Color(0xFFFFD400), Offset(sa.first, sa.second), Offset(sb.first, sb.second), 2.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 5.dp.toPx()))) }
        // v58 component symbols: constant size (three.js sprite 0.026 without size attenuation, fov 45° = 3.14 % of the view height)
        val sz = size.height * 0.026f * 2.4142f / 2f
        val syms = mode.symbolsOnScreen()
        for (sy in syms) symbolCone(this, Offset(sy.x, sy.y), sz, sy.s, sy.id == mode.selConn)
        onDrawnMarks?.invoke(marks, syms.map { it.x to it.y })
    }
    // v69 tkCheckStep: checks run after a change, a few components at a time, and recolour the symbols
    androidx.compose.runtime.LaunchedEffect(tick) { kotlinx.coroutines.delay(1500); while (mode.checkStep()) kotlinx.coroutines.delay(60) }
}

/** v69 tkSymTex (64 × 64 canvas): a cone with a gradient, green / yellow / red; the selected one with a yellow halo and outline. */
private fun symbolCone(d: androidx.compose.ui.graphics.drawscope.DrawScope, c: Offset, size: Float, s: Int, sel: Boolean) = with(d) {
    val k = size / 64f; fun P(x: Float, y: Float) = Offset(c.x + (x - 32f) * k, c.y + (y - 32f) * k)
    val col = when (s) { 3 -> listOf(0xFFFF6B6B, 0xFFC92A2A, 0xFF7A1010); 2 -> listOf(0xFFFFE066, 0xFFF2B705, 0xFF8A6500); else -> listOf(0xFF69DB7C, 0xFF2F9E44, 0xFF14532D) }.map { Color(it.toInt()) }
    if (sel) drawCircle(Color(0x59FFE600), 30f * k, c)
    val body = androidx.compose.ui.graphics.Path().apply { moveTo(P(32f, 8f).x, P(32f, 8f).y); lineTo(P(52f, 48f).x, P(52f, 48f).y)
        for (i in 0..16) { val t = Math.PI * i / 16; lineTo(P(32f + 20f * Math.cos(t).toFloat(), 48f + 7f * Math.sin(t).toFloat()).x, P(32f + 20f * Math.cos(t).toFloat(), 48f + 7f * Math.sin(t).toFloat()).y) }
        lineTo(P(32f, 8f).x, P(32f, 8f).y); close() }
    val grad = androidx.compose.ui.graphics.Brush.horizontalGradient(0f to col[0], 0.55f to col[1], 1f to col[2], startX = P(14f, 0f).x, endX = P(50f, 0f).x)
    val stroke = if (sel) Color(0xFFFFE600) else Color(0x8C000000); val sw = (if (sel) 4f else 2f) * k
    drawPath(body, grad); drawPath(body, stroke, style = Stroke(sw))
    val top = androidx.compose.ui.graphics.Path().apply { for (i in 0..16) { val t = Math.PI + Math.PI * i / 16; val q = P(32f + 20f * Math.cos(t).toFloat(), 48f + 7f * Math.sin(t).toFloat()); if (i == 0) moveTo(q.x, q.y) else lineTo(q.x, q.y) } }
    drawPath(top, col[2]); drawPath(top, stroke, style = Stroke(sw))
}

/** The Steel panel (v69 `#st-panel`) and its sheets (Grid, Part properties). */
@Composable
fun SteelPanel(mode: SteelMode, tick: Int, modifier: Modifier = Modifier, pics: com.solidsketchapp.steel.comp.CompPics? = null,
               /** Opens with this sheet ("grid", "props", "cat", "dlg:<key>") — for the screenshot test. */ initialSheet: String? = null, initialTab: String? = null) {
    @Suppress("UNUSED_EXPRESSION") tick
    var sheet by remember { mutableStateOf(initialSheet?.substringBefore(':')) }
    var dlg by remember { mutableStateOf<Pair<String, String?>?>(initialSheet?.takeIf { it.startsWith("dlg:") }?.let { it.substringAfter(':') to null }) }   // component key, connection id
    var toastText by remember { mutableStateOf<String?>(null) }
    mode.lastToast?.let { toastText = it; mode.lastToast = null }
    mode.dialogRequest?.let { dlg = it; sheet = "dlg"; mode.dialogRequest = null }
    mode.sheetRequest?.let { sheet = it; mode.sheetRequest = null }
    androidx.compose.runtime.LaunchedEffect(toastText) { if (toastText != null) { kotlinx.coroutines.delay(2600); toastText = null } }
    if (!mode.on) return
    val comps = mode.comps
    /** v69 stConnSheet: a selected component plate or bolt group opens its dialog, else the catalog. */
    fun connSheet() { val c = mode.selectionFirst()?.let { comps?.connOfBody(it) }; if (c != null && comps?.ui?.conn?.containsKey(c.type) == true) { dlg = c.type to c.id; sheet = "dlg" } else sheet = "cat" }
    val c = com.solidsketchapp.steel.ui.LocalSs.current
    fun soon(what: String, ms: String) { toastText = "$what comes with $ms" }
    Box(modifier.fillMaxSize()) {
        // v69 #st-panel L13727: fixed 8 px from the sides and the bottom (here above the navigation bar), max 640, panel, radius 16, padding 8 × 10, 14 px
        Column(Modifier.align(Alignment.BottomCenter).windowInsetsPadding(com.solidsketchapp.steel.ui.panelInsets()).padding(8.dp).widthIn(max = 640.dp).fillMaxWidth()) {
        // v58 #tk-status: the selected component's checks (tap to clear the selection)
        mode.statusLine()?.let { (st, text) ->
            val (bg, fg, bd) = when (st) { 1 -> Triple(0xFFE9F8EE, 0xFF14532D, 0xFF2F9E44); 2 -> Triple(0xFFFFF8DB, 0xFF5C4400, 0xFFF2B705); else -> Triple(0xFFFFE3E3, 0xFF7A1010, 0xFFC92A2A) }
            Text(text, fontSize = 12.5.sp, color = Color(fg.toInt()), modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp).background(Color(bg.toInt()), RoundedCornerShape(10.dp))
                .border(1.dp, Color(bd.toInt()), RoundedCornerShape(10.dp)).clickable { mode.selectConn(null) }.padding(horizontal = 12.dp, vertical = 7.dp))
        }
        Column(Modifier.fillMaxWidth().shadow(28.dp, RoundedCornerShape(16.dp)).background(c.panel, RoundedCornerShape(16.dp))
            .border(1.dp, c.border, RoundedCornerShape(16.dp)).padding(horizontal = 10.dp, vertical = 8.dp)) {
            // v69 stPanel L13759: Grid · Portal frame · Column · Beam · Plate · Components · Numbering · Checks · Drawings · Marks · Properties · Steel list · Exit Steel
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                StBtn("Grid") { sheet = "grid" }
                StBtn("Portal frame") { sheet = "portal" }
                StBtn("Column", mode.tool == SteelMode.Tool.COLUMN) { mode.pickTool(SteelMode.Tool.COLUMN) }
                StBtn("Beam", mode.tool == SteelMode.Tool.BEAM) { mode.pickTool(SteelMode.Tool.BEAM) }
                StBtn("Plate", mode.tool == SteelMode.Tool.PLATE) { mode.pickTool(SteelMode.Tool.PLATE) }
                StBtn("Components", enabled = comps != null) { connSheet() }
                StBtn("Numbering") { soon("Numbering", "S11") }
                StBtn("Checks") { soon("Checks", "S11") }
                StBtn("Drawings") { soon("Drawings", "S12") }
                StBtn("Marks") { soon("Marks", "numbering (S11)") }
                StBtn("Properties") { sheet = "props" }
                StBtn("Steel list") { soon("Steel list", "S11") }
                StBtn("Exit Steel") { mode.toggle() }
            }
            val ck = mode.catK
            if (ck != null && comps != null) Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(comps.ui.label(ck), fontSize = 14.sp, maxLines = 1, color = c.fg)
                StBtn("Properties") { dlg = ck to null; sheet = "dlg" }
                StBtn("Catalog") { sheet = "cat" }
                StBtn("Done", on = true) { mode.catDone() }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                com.solidsketchapp.steel.ui.CssText("Work level", com.solidsketchapp.steel.ui.cssLine(16).copy(fontSize = 14.sp, color = c.fg))
                mode.levelLabels().forEachIndexed { i, l -> StBtn(l, mode.level == i) { mode.setLevel(i) } }
                if (mode.tool == SteelMode.Tool.BEAM || mode.tool == SteelMode.Tool.COLUMN) {
                    Text(" · Set", fontSize = 14.sp, color = c.fg)
                    for (k in if (mode.tool == SteelMode.Tool.COLUMN) listOf("COLUMN") else listOf("BEAM", "RAFTER", "PURLIN", "GIRT", "BRACING"))
                        StBtn(k.take(1) + k.drop(1).lowercase(), mode.set == k) { mode.useSet(k) }
                }
            }
            if ((mode.tool == SteelMode.Tool.BEAM || mode.tool == SteelMode.Tool.PLATE) && mode.pts.isNotEmpty()) {
                var typed by remember { mutableStateOf("") }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Field(typed, { typed = it }, "dx dy dz (mm)", Modifier.width(150.dp))
                    StBtn("Place") { mode.typedPoint(typed)?.let { } }
                    StBtn("Cancel") { mode.cancelPoints() }
                    if (mode.tool == SteelMode.Tool.PLATE && mode.pts.size >= 3) StBtn("Finish", on = true) { mode.finishPlate() }
                }
            }
            Text(mode.hint(), fontSize = 12.5.sp, color = c.fg.copy(alpha = 0.8f), modifier = Modifier.heightIn(min = 16.dp).padding(start = 2.dp, end = 2.dp, top = 2.dp))
        }
        }
        when (sheet) {
            "grid" -> GridSheet(mode) { sheet = null }
            "props" -> PropSheet(mode) { sheet = null }
            "portal" -> PortalSheet(mode, { toastText = it }) { sheet = null }
            "cat" -> if (comps != null && pics != null) com.solidsketchapp.steel.comp.CatalogSheet(mode, comps, pics, mode.symbols, { mode.symbols = !mode.symbols; mode.refresh() },
                { k -> dlg = k to null; sheet = "dlg" }, { k -> if (k == "@portal") sheet = "portal" else toastText = "${comps.ui.label(k)} comes with a later milestone (S11)" }, { sheet = null })
            "dlg" -> { val d = dlg; if (comps != null && pics != null && d != null) androidx.compose.runtime.key(d) {
                com.solidsketchapp.steel.comp.CompDialogSheet(mode, comps, pics, d.first, d.second?.let { comps.conn(it) }, { c -> mode.status(c) }, { toastText = it }, { sheet = null; mode.refresh() }, initialTab) } }
        }
        toastText?.let { t -> com.solidsketchapp.steel.ui.SsToast(t, Modifier.align(Alignment.TopCenter)) }
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, placeholder: String = "", modifier: Modifier = Modifier) {
    // v69 #st-sheet input: padding 6 × 8, radius 8, border var(--border), transparent, colour inherit (30 px high with the border)
    com.solidsketchapp.steel.ui.SsInput(value, onChange, placeholder, modifier.fillMaxWidth())
}

@Composable
private fun Sheet(title: String, content: @Composable () -> Unit) = com.solidsketchapp.steel.ui.StSheet(title, content = { content() })

@Composable
private fun Row2(label: String, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        // v69 #st-sheet .g: grid-template-columns 120px 1fr, gap 6 × 10
        Text(label, fontSize = 14.sp, style = com.solidsketchapp.steel.ui.cssLine(16), modifier = Modifier.width(130.dp).padding(end = 10.dp)); Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun H4(t: String) = Text(t.uppercase(), fontSize = 13.sp, letterSpacing = 0.52.sp, color = com.solidsketchapp.steel.ui.LocalSs.current.fg.copy(alpha = 0.75f), modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))

/** v69 stGridSheet. */
@Composable
private fun GridSheet(mode: SteelMode, close: () -> Unit) {
    val g0 = mode.grid ?: StGrid.DEFAULT
    var x by remember { mutableStateOf(g0.x) }; var y by remember { mutableStateOf(g0.y) }; var z by remember { mutableStateOf(g0.z) }
    var err by remember { mutableStateOf("") }
    Sheet("Grid") {
        Row2("X (mm)") { Field(x, { x = it }) }; Row2("Y (mm)") { Field(y, { y = it }) }; Row2("Z levels (mm)") { Field(z, { z = it }) }
        // v69 <p>: margin 14 0 (after the grid's last row; its bottom margin collapses with the bar's 12)
        Text("Tekla syntax: the first value is where the grid starts, then the spacings; 4*6000 = four bays of 6000. X lines are numbered 1, 2, 3…, Y lines lettered A, B, C…", fontSize = 14.sp, style = com.solidsketchapp.steel.ui.cssLine(16), modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
        if (err.isNotEmpty()) Text(err, color = Color(0xFFC0392B), fontSize = 13.sp)
        com.solidsketchapp.steel.ui.StBar {
            StBtn("Cancel") { close() }
            StBtn("Apply", on = true) { val e = mode.setGrid(StGrid(x, y, z, g0.lx, g0.ly)); if (e == null) close() else err = e }
        }
    }
}

/**
 * v69 stPortalSheet (L13866): the values (PF_DEF + S.stGen), Close / Create. Create checks the profiles (columns and rafters
 * always, the others when switched on) and the numbers as v69 does, closes the sheet and builds the frame (PortalFrame.run).
 */
@Composable
private fun PortalSheet(mode: SteelMode, toast: (String) -> Unit, close: () -> Unit) {
    val q0 = remember { JsonObject(LinkedHashMap(com.solidsketchapp.steel.apps.PortalFrame.DEF).apply { mode.stGen()?.let { putAll(it) } }) }
    fun str(k: String) = (q0[k] as? JsonPrimitive)?.let { p -> if (p.isString) p.content else p.doubleOrNull?.let { com.solidsketchapp.core.api.JsNumber.format(it) } ?: p.content } ?: ""
    val f = remember { linkedMapOf<String, androidx.compose.runtime.MutableState<String>>() }
    val chk = remember { linkedMapOf<String, androidx.compose.runtime.MutableState<Boolean>>() }
    // v69 order of the rows (L13870–13871): number / text / checkbox
    val rows = listOf("n:span:Span (mm)", "n:bays:Bays", "n:bay:Bay (mm)", "n:eave:Eaves height (mm)", "n:pitch:Roof pitch (°)", "t:col:Columns", "t:raf:Rafters",
        "c:purOn:Purlins", "t:pur:Purlin profile", "n:purS:Purlin spacing (mm)", "c:tieOn:Eaves ties", "t:tie:Tie profile", "c:rbOn:Roof bracing (end bays)", "t:rb:Roof brace profile",
        "c:wbOn:Wall bracing (end bays)", "t:wb:Wall brace profile", "c:girtOn:Side girts", "t:girt:Girt profile", "n:girtS:Girt spacing (mm)", "c:craneOn:Crane runway", "n:crH:Rail top level (mm)",
        "t:crBeam:Crane beam", "t:crBr:Bracket", "c:gableOn:Gable columns + gable girts", "t:gcol:Gable column profile", "n:gcolS:Gable column spacing (mm)")
    for (r in rows) { val (t, k) = r.split(':'); if (t == "c") chk.getOrPut(k) { mutableStateOf((q0[k] as? JsonPrimitive)?.let { it.booleanOrNull ?: (it.doubleOrNull?.let { d -> d != 0.0 }) } ?: false) } else f.getOrPut(k) { mutableStateOf(str(k)) } }
    Sheet("Portal frame") {
        Text("Tekla layout: grid A–B–C across the span (B is the ridge), frames on grid lines 1, 2, 3… along the building. Changing a value and pressing Create rebuilds the frame parts.",
            fontSize = 14.sp, style = com.solidsketchapp.steel.ui.cssLine(16), modifier = Modifier.padding(top = 4.dp, bottom = 14.dp))   // <p> margin 14 (collapses with the title's 10)
        com.solidsketchapp.steel.ui.CssStack { for (r in rows) { val (t, k, label) = r.split(':', limit = 3)
            Row2(label) { if (t == "c") PfCheck(chk.getValue(k).value) { chk.getValue(k).value = it } else Field(f.getValue(k).value, { f.getValue(k).value = it }) } } }
        com.solidsketchapp.steel.ui.StBar {
            StBtn("Close") { close() }
            StBtn("Create", on = true) {
                // v69: numbers are +value, texts trimmed, checkboxes booleans
                val q = linkedMapOf<String, JsonElement>()
                for (r in rows) { val (t, k) = r.split(':'); q[k] = when (t) { "c" -> JsonPrimitive(chk.getValue(k).value)
                    "n" -> f.getValue(k).value.trim().let { v -> if (v.isEmpty()) jsNumEl(0.0) else v.toDoubleOrNull()?.let(::jsNumEl) ?: jsNumEl(Double.NaN) }
                    else -> JsonPrimitive(f.getValue(k).value.trim()) } }
                for (k in listOf("col", "raf", "pur", "tie", "rb", "wb")) { val onK = q[k + "On"] as? JsonPrimitive
                    if ((k == "col" || k == "raf" || onK?.booleanOrNull != false) && mode.data.profiles.lookup((q[k] as JsonPrimitive).content) == null) { toast("Unknown profile ${(q[k] as JsonPrimitive).content}"); return@StBtn } }
                fun num(k: String) = (q[k] as JsonPrimitive).doubleOrNull ?: Double.NaN
                if (!(num("span") > 0 && num("bays") >= 1 && num("bay") > 0 && num("eave") > 0 && num("pitch") >= 0 && num("pitch") < 45)) { toast("Check the span, bays, eaves height and pitch"); return@StBtn }
                close(); toast(mode.portal(JsonObject(q)))
            }
        }
    }
}

/** v69 sheet checkbox (`<input type=checkbox>`, width 22 px). */
@Composable
private fun PfCheck(on: Boolean, change: (Boolean) -> Unit) {
    val c = com.solidsketchapp.steel.ui.LocalSs.current
    // Chrome's checkbox: 13 px with margins 3 3 3 4 (the row is 19 px high), width 22 px (v69 style)
    Box(Modifier.width(22.dp).height(19.dp).clickable { change(!on) }, contentAlignment = Alignment.TopStart) {
        androidx.compose.foundation.Canvas(Modifier.padding(start = 4.dp, top = 3.dp).size(13.dp)) { val r = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
            if (on) { drawRoundRect(Color(0xFF2A6FD1), cornerRadius = r)
                val p = androidx.compose.ui.graphics.Path().apply { moveTo(size.width * 0.22f, size.height * 0.52f); lineTo(size.width * 0.42f, size.height * 0.72f); lineTo(size.width * 0.78f, size.height * 0.30f) }
                drawPath(p, Color.White, style = androidx.compose.ui.graphics.drawscope.Stroke(1.6.dp.toPx())) }
            else { drawRoundRect(Color.Transparent, cornerRadius = r); drawRoundRect(c.fg.copy(alpha = 0.6f), cornerRadius = r, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx())) } }
    }
}

private fun jsNumEl(d: Double): JsonElement = com.solidsketchapp.steel.model.jsNum(d)

/** v69 stPropSheet: saved settings, attributes, numbering series, position; Apply / Save as / Get / Modify. */
@Composable
private fun PropSheet(mode: SteelMode, close: () -> Unit) {
    val selId = mode.selectionFirst()
    val selSt = selId?.let { mode.stOf(it) }
    val src: Map<String, JsonElement> = if (selSt != null) LinkedHashMap(mode.props).apply { putAll(selSt) } else mode.props
    fun s(k: String) = (src[k] as? JsonPrimitive)?.contentOrNull ?: ""
    val keys = listOf("name", "prof", "mat", "cls", "fin", "pp", "ps", "ap", "as", "plane", "pOff", "rot", "rAng", "depth", "dOff", "dx1", "dx2")
    val f = remember(selId, mode.set) { keys.associateWith { mutableStateOf(s(it)) } }
    var colTop by remember { mutableStateOf(mode.colTop) }
    var msg by remember { mutableStateOf("") }
    fun read(): Map<String, JsonElement> = keys.associateWith { k ->
        val v = f.getValue(k).value
        when (k) { "cls", "ps", "as", "pOff", "rAng", "dOff", "dx1", "dx2" -> jsNum(StGrid.jsNumber(v).let { if (it.isFinite()) it else 0.0 }); "name" -> JsonPrimitive(v.ifBlank { "PART" }.uppercase()); else -> JsonPrimitive(v) }
    }
    val title = if (selSt != null) "Part properties · ${mode.bodyName(selId)}" else "Part properties · ${mode.set.take(1)}${mode.set.drop(1).lowercase()}"
    Sheet(title) {
        H4("Saved settings")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (k in mode.data.propertySets.map { it.name }) StBtn(k, selSt == null && mode.set == k) { mode.useSet(k); for ((kk, st) in f) st.value = (mode.setValues(k)[kk] as? JsonPrimitive)?.contentOrNull ?: "" }
        }
        H4("Attributes")
        Row2("Name") { Field(f.getValue("name").value, { f.getValue("name").value = it }) }
        Row2("Profile") { Field(f.getValue("prof").value, { f.getValue("prof").value = it }, "IPE300, HEA200, SHS100x100x5, PL20") }
        val p = mode.data.profiles.lookup(f.getValue("prof").value)
        Text(when { p == null -> "Not in the catalog"; p.kind == "P" -> if (p.b != null) "${SteelMode.mm(p.h!!)} × ${SteelMode.mm(p.b)} mm · ${Fmt.f("%.1f", p.m)} kg/m" else "plate ${SteelMode.mm(p.t!!)} mm (contour plate)"
            p.kind == "O" -> "Ø${SteelMode.mm(p.d!!)} × ${SteelMode.mm(p.t!!)} mm · ${p.m} kg/m · ${p.P} m²/m"; else -> "h ${SteelMode.mm(p.h!!)} · b ${SteelMode.mm(p.b!!)} mm · ${p.m} kg/m · ${p.P} m²/m" },
            fontSize = 12.sp, color = com.solidsketchapp.steel.ui.LocalSs.current.fg.copy(alpha = 0.75f), modifier = Modifier.padding(start = 120.dp))
        Row2("Material") { Choice(mode.data.materials.grades, f.getValue("mat")) }
        Row2("Class (colour)") { Choice(mode.data.classes.keys.sorted().map { it.toString() }, f.getValue("cls")) }
        Row2("Finish") { Field(f.getValue("fin").value, { f.getValue("fin").value = it }) }
        H4("Numbering series")
        Row2("Part prefix / no") { Two({ Field(f.getValue("pp").value, { f.getValue("pp").value = it }) }, { Field(f.getValue("ps").value, { f.getValue("ps").value = it }) }) }
        Row2("Assembly prefix / no") { Two({ Field(f.getValue("ap").value, { f.getValue("ap").value = it }) }, { Field(f.getValue("as").value, { f.getValue("as").value = it }) }) }
        val isCol = mode.isColumn(selSt)
        H4("Position")
        Row2(if (isCol) "Horizontal" else "On plane") { Two({ Choice(listOf("Middle", "Left", "Right"), f.getValue("plane")) }, { Field(f.getValue("pOff").value, { f.getValue("pOff").value = it }) }) }
        Row2("Rotation / °") { Two({ Choice(listOf("Top", "Front", "Below", "Back"), f.getValue("rot")) }, { Field(f.getValue("rAng").value, { f.getValue("rAng").value = it }) }) }
        Row2(if (isCol) "Vertical" else "At depth") { Two({ Choice(listOf("Middle", "Front", "Behind"), f.getValue("depth")) }, { Field(f.getValue("dOff").value, { f.getValue("dOff").value = it }) }) }
        Row2("End offset start / end (mm)") { Two({ Field(f.getValue("dx1").value, { f.getValue("dx1").value = it }) }, { Field(f.getValue("dx2").value, { f.getValue("dx2").value = it }) }) }
        if (selSt == null && mode.set == "COLUMN") Row2("Column top (mm)") { Field(colTop, { colTop = it }, "next level up") }
        if (msg.isNotEmpty()) Text(msg, color = Color(0xFFC0392B), fontSize = 13.sp)
        com.solidsketchapp.steel.ui.StBar {
            StBtn("Close") { close() }
            StBtn("Save as ${mode.set.lowercase()}") { mode.saveAsSet(read()) }
            if (selId != null && selSt != null) {
                StBtn("Get") { mode.applyProps(read()) }
                StBtn("Modify", on = true) { val o = read(); val pr = (o["prof"] as JsonPrimitive).content
                    if (mode.data.profiles.lookup(pr) == null) msg = "Unknown profile $pr" else { mode.parts.modify(listOf(selId), o); close() } }
            } else StBtn("Apply", on = true) { val o = read(); val pr = (o["prof"] as JsonPrimitive).content
                if (mode.data.profiles.lookup(pr) == null) msg = "Unknown profile $pr" else { mode.applyProps(o); mode.colTop = colTop.trim(); close() } }
        }
    }
}

@Composable
private fun Two(a: @Composable () -> Unit, b: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Box(Modifier.weight(1f)) { a() }; Box(Modifier.weight(1f)) { b() } }
}

/** A select as a row of pills (v69 uses a <select>; same options). */
@Composable
private fun Choice(options: List<String>, state: androidx.compose.runtime.MutableState<String>) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (o in options) StBtn(o, state.value == o) { state.value = o }
        Spacer(Modifier.width(1.dp))
    }
}
