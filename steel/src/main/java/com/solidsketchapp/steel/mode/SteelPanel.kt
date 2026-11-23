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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** v69 Steel buttons (`#st-panel button`): pill, one line; "on" = #c0392b with white text. */
@Composable
fun StBtn(label: String, on: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    // v69 L13730: border 1px rgba(127,127,127,.35), transparent, colour inherit, radius 999, padding 7 × 12, 14 px; .on #c0392b / white
    val c = com.solidsketchapp.steel.ui.LocalSs.current
    Box(Modifier.heightIn(min = 33.dp).background(if (on) c.on else Color.Transparent, RoundedCornerShape(999.dp)).border(1.dp, if (on) c.on else c.pillBorder, RoundedCornerShape(999.dp))
        .clickable(enabled = enabled) { onClick() }.padding(horizontal = 12.dp, vertical = 7.dp), contentAlignment = Alignment.Center) {
        Text(label, maxLines = 1, softWrap = false, overflow = TextOverflow.Visible, fontSize = 14.sp, color = (if (on) Color.White else c.fg).copy(alpha = if (enabled) 1f else 0.45f))
    }
}

/** Grid labels (v69 sprites: circled 1, 2, 3… / A, B, C… at the ends of the lines; level labels) and the picked points / handles. */
@Composable
fun SteelOverlay(mode: SteelMode, tick: Int, project: (Vec3) -> Pair<Float, Float>?, modifier: Modifier = Modifier,
                 /** The camera's revision. Read while drawing, so every camera change (orbit, pan, zoom, resize) redraws the labels,
                  *  handles and cones even when this composable itself is skipped (S9b regression: they stayed where they were first drawn). */
                 viewRev: () -> Int = { 0 },
                 /** Test hook: the grid labels as drawn (text, x, y in px). */ onDrawn: ((List<Triple<String, Float, Float>>) -> Unit)? = null) {
    @Suppress("UNUSED_EXPRESSION") tick
    val tm = rememberTextMeasurer()
    if (!mode.on) return
    Canvas(modifier.fillMaxSize()) {
        viewRev()
        val r = 13.dp.toPx()
        val drawn = ArrayList<Triple<String, Float, Float>>()
        fun label(p: Vec3, text: String, ring: Boolean) {
            val s = project(p) ?: return; val c = Offset(s.first, s.second); drawn += Triple(text, c.x, c.y)
            if (ring) { drawCircle(Color.White, r, c); drawCircle(Color(0xFF3A4250), r, c, style = Stroke(1.5.dp.toPx())) }
            val lay = tm.measure(text, TextStyle(fontSize = if (text.length > 3) 10.sp else 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1D2330)))
            drawText(lay, topLeft = Offset(c.x - lay.size.width / 2f, c.y - lay.size.height / 2f))
        }
        for (l in mode.gridLabels()) label(l.p, l.text, l.ring)
        onDrawn?.invoke(drawn)
        for ((p, argb) in mode.markers()) project(Vec3(p.x, p.y, p.z))?.let { drawCircle(Color(argb), 6.dp.toPx(), Offset(it.first, it.second)) }
        // v58 tkFanDraw: dashed yellow lines from the selected component's symbol to its parts
        for ((a, b) in mode.fanLines()) { val sa = project(Vec3(a.x, a.y, a.z)) ?: continue; val sb = project(Vec3(b.x, b.y, b.z)) ?: continue
            drawLine(Color(0xFFFFD400), Offset(sa.first, sa.second), Offset(sb.first, sb.second), 2.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 5.dp.toPx()))) }
        // v58 component symbols: constant size (three.js sprite 0.026 without size attenuation, fov 45° = 3.14 % of the view height)
        val sz = size.height * 0.026f * 2.4142f / 2f
        for (sy in mode.symbolsOnScreen()) symbolCone(this, Offset(sy.x, sy.y), sz, sy.s, sy.id == mode.selConn)
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
                StBtn("Portal frame") { soon("Portal frame", "the applications (S10)") }
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
                Text("Work level", fontSize = 14.sp, color = c.fg)
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
            "cat" -> if (comps != null && pics != null) com.solidsketchapp.steel.comp.CatalogSheet(mode, comps, pics, mode.symbols, { mode.symbols = !mode.symbols; mode.refresh() },
                { k -> dlg = k to null; sheet = "dlg" }, { k -> toastText = "${comps.ui.label(k)} comes with a later milestone (${if (k == "@portal") "S10" else "S11"})" }, { sheet = null })
            "dlg" -> { val d = dlg; if (comps != null && pics != null && d != null) androidx.compose.runtime.key(d) {
                com.solidsketchapp.steel.comp.CompDialogSheet(mode, comps, pics, d.first, d.second?.let { comps.conn(it) }, { c -> mode.status(c) }, { toastText = it }, { sheet = null; mode.refresh() }, initialTab) } }
        }
        toastText?.let { t -> com.solidsketchapp.steel.ui.SsToast(t, Modifier.align(Alignment.TopCenter)) }
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, placeholder: String = "", modifier: Modifier = Modifier) {
    // v69 #st-sheet input: padding 6 × 8, radius 8, border var(--border), transparent, colour inherit
    val c = com.solidsketchapp.steel.ui.LocalSs.current
    Box(modifier.border(1.dp, c.border, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 6.dp)) {
        if (value.isEmpty()) Text(placeholder, color = c.fg.copy(alpha = 0.5f), fontSize = 14.sp)
        BasicTextField(value, onChange, singleLine = true, textStyle = TextStyle(fontSize = 14.sp, color = c.fg), cursorBrush = androidx.compose.ui.graphics.SolidColor(c.fg), modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun Sheet(title: String, content: @Composable () -> Unit) = com.solidsketchapp.steel.ui.StSheet(title, content = { content() })

@Composable
private fun Row2(label: String, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, modifier = Modifier.width(120.dp)); Box(Modifier.weight(1f)) { content() }
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
        Text("Tekla syntax: the first value is where the grid starts, then the spacings; 4*6000 = four bays of 6000. X lines are numbered 1, 2, 3…, Y lines lettered A, B, C…", fontSize = 14.sp, modifier = Modifier.padding(vertical = 10.dp))
        if (err.isNotEmpty()) Text(err, color = Color(0xFFC0392B), fontSize = 13.sp)
        com.solidsketchapp.steel.ui.StBar {
            StBtn("Cancel") { close() }
            StBtn("Apply", on = true) { val e = mode.setGrid(StGrid(x, y, z, g0.lx, g0.ly)); if (e == null) close() else err = e }
        }
    }
}

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
