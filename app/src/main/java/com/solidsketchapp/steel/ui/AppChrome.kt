package com.solidsketchapp.steel.ui

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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The SolidSketch main screen chrome as web v69 draws it (L20–51 CSS, L250–281 DOM; values in docs/steel/UI-MAP.md):
 * `#top .bar` (title, body count, Items ◫, Section ◧, Undo, Redo, History, Delete 🗑, Walk 🚶, ⋮ menu), `#bottom`
 * (`#hint` pill, `#tools` chips), `#toast`. 1 CSS px = 1 dp (the owner's phone: DPR 2.625 = density 2.625).
 */
class ChromeState(
    val bodies: Int,
    val canUndo: Boolean, val canRedo: Boolean, val canDelete: Boolean,
    val tool: String,
    val hint: String,
    val menuOpen: Boolean,
)

/** v69 TOOLS (L11706) with Steel first (v32) and the two later chips (Grid snap, ▦ Folders), as v69 shows them. */
val V69_TOOLS = listOf("steel" to "Steel", "select" to "Select", "move" to "Move/Rotate", "line" to "Line", "polyline" to "Polyline", "rect" to "Rectangle", "circle" to "Circle",
    "edit" to "Edit sketch", "box" to "Box", "cylinder" to "Cylinder", "sphere" to "Sphere", "gridsnap" to "Grid snap", "folders" to "▦ Folders")

/** v69 `#menu` items (L262–275 and later chapters), in its order; the app adds "About SolidSketch Steel". */
val V69_MENU = listOf("open" to "Open model (.ss)…", "walk" to "Walkthrough", "faces" to "Pick faces only: off", "save" to "Save model as .ss…", "furn" to "Furniture…",
    "import3d" to "Import 3D model (glTF, OBJ, DAE, FBX)…", "import" to "Import DXF / DWG…", "paste" to "Paste DXF text…", "export" to "Export STL + OBJ (zip)",
    "exportdxf" to "Export DXF drawing (zip)", "exact" to "Exact engine (beta): on", "reset" to "Reset view", "help" to "Gestures & help",
    "keepsketch" to "Keep sketch when leaving: on", "autoturn" to "Auto view turn: on", "clear" to "Clear all", "units" to "Units: mm", "about" to "About SolidSketch Steel")

/** Insets every top-anchored piece of chrome keeps clear of (status bar, cutout). */
val TopInsets @Composable get() = com.solidsketchapp.steel.ui.topInsets()
/** Insets every bottom-anchored piece keeps clear of (navigation bar, 3-button or gesture, and the keyboard). */
val BottomInsets @Composable get() = com.solidsketchapp.steel.ui.panelInsets()

/** v69 `.btn` (14 px 600, accent; disabled: muted at 50 %); `.btn.icon` 18 px. */
@Composable
private fun TopBtn(label: String, enabled: Boolean = true, icon: Boolean = false, color: androidx.compose.ui.graphics.Color? = null, onClick: () -> Unit) {
    val c = LocalSs.current
    Box(Modifier.heightIn(min = 40.dp).clickable(enabled = enabled) { onClick() }.padding(horizontal = if (icon) 3.dp else 5.dp, vertical = 8.dp).alpha(if (enabled) 1f else 0.5f), contentAlignment = Alignment.Center) {
        Text(label, fontSize = if (icon) 18.sp else 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false, color = if (!enabled) c.muted else color ?: c.accent)
    }
}

/** v69 History button: the clock-with-arrow SVG (20 px, stroke 2, accent). */
@Composable
private fun HistoryIcon(enabled: Boolean, onClick: () -> Unit) {
    val c = LocalSs.current
    Box(Modifier.size(width = 27.dp, height = 40.dp).clickable(enabled = enabled) { onClick() }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(20.dp)) { val k = size.width / 24f; val st = Stroke(2f * k, cap = StrokeCap.Round, join = StrokeJoin.Round)
            // <path d="M3 12a9 9 0 1 0 3-6.7"/><path d="M3 4v4h4"/><path d="M12 7v5l3 2"/>
            drawArc(c.accent, startAngle = 180f + 22f, sweepAngle = 338f - 22f, useCenter = false, topLeft = Offset(3 * k, 3 * k), size = androidx.compose.ui.geometry.Size(18 * k, 18 * k), style = st)
            drawPath(Path().apply { moveTo(3 * k, 4 * k); lineTo(3 * k, 8 * k); lineTo(7 * k, 8 * k) }, c.accent, style = st)
            drawPath(Path().apply { moveTo(12 * k, 7 * k); lineTo(12 * k, 12 * k); lineTo(15 * k, 14 * k) }, c.accent, style = st) }
    }
}

/** v69 `#top`: fixed at the top (safe area + 8 px, 10 px sides), `.bar` panel, radius 16, padding 6 6 6 14, gap 4. */
@Composable
fun TopBar(s: ChromeState, onUndo: () -> Unit, onRedo: () -> Unit, onDelete: () -> Unit, onSoon: (String) -> Unit, onMore: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalSs.current
    Box(modifier.fillMaxWidth().windowInsetsPadding(TopInsets).padding(start = 10.dp, end = 10.dp, top = 8.dp), contentAlignment = Alignment.TopCenter) {
        Row(Modifier.widthIn(max = 720.dp).fillMaxWidth().shadow(18.dp, RoundedCornerShape(16.dp), ambientColor = androidx.compose.ui.graphics.Color(0x1F000000), spotColor = androidx.compose.ui.graphics.Color(0x1F000000))
            .background(c.panel, RoundedCornerShape(16.dp)).border(1.dp, c.border, RoundedCornerShape(16.dp)).padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),   // v69 phone width: 6 4 6 10
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("SolidSketch", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.fg, maxLines = 1, softWrap = false)
            Text("${s.bodies} bod${if (s.bodies == 1) "y" else "ies"}", fontSize = 12.sp, color = c.muted, lineHeight = 14.sp, modifier = Modifier.padding(start = 6.dp).widthIn(max = 40.dp))   // v69 #count wraps ("0 / bodies") at phone width
            Spacer(Modifier.weight(1f))
            TopBtn("◫", icon = true, color = c.fg) { onSoon("Items") }
            TopBtn("◧", icon = true, color = c.fg) { onSoon("Section View") }
            TopBtn("Undo", s.canUndo, onClick = onUndo)
            TopBtn("Redo", s.canRedo, onClick = onRedo)
            HistoryIcon(true) { onSoon("History") }
            TopBtn("🗑", s.canDelete, icon = true, color = c.fg, onClick = onDelete)
            TopBtn("🚶", icon = true, color = c.fg) { onSoon("Walkthrough") }
            TopBtn("⋮", icon = true, color = c.fg, onClick = onMore)
        }
    }
}

/** v69 `#menu`: under the ⋮ button (right, 44 px below the bar row), panel, radius 12, min 190 px, padding 6; items 14 px, 10 × 12. */
@Composable
fun MainMenu(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val c = LocalSs.current
    Box(Modifier.fillMaxSize().clickable(interactionSource = null, indication = null) { onDismiss() }) {
        Column(Modifier.align(Alignment.TopEnd).windowInsetsPadding(TopInsets).padding(top = 8.dp + 6.dp + 44.dp, end = 10.dp + 6.dp).windowInsetsPadding(BottomInsets)
            .width(190.dp).shadow(30.dp, RoundedCornerShape(12.dp)).background(c.panel, RoundedCornerShape(12.dp)).border(1.dp, c.border, RoundedCornerShape(12.dp))
            .verticalScroll(rememberScrollState()).padding(6.dp)) {
            for ((id, label) in V69_MENU) Text(label, fontSize = 14.sp, color = c.fg, modifier = Modifier.fillMaxWidth().clickable { onPick(id) }.padding(horizontal = 12.dp, vertical = 10.dp))
        }
    }
}

/** v69 `.chip` / `.chip.sel`: 13.5 px 500, padding 9 × 14, radius 999, border 1 px. */
@Composable
fun Chip(label: String, sel: Boolean, onClick: () -> Unit) {
    val c = LocalSs.current
    Text(label, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false, color = if (sel) c.chipSelFg else c.chipFg,
        modifier = Modifier.background(if (sel) c.chipSel else c.chip, RoundedCornerShape(999.dp)).border(1.dp, if (sel) androidx.compose.ui.graphics.Color.Transparent else c.border, RoundedCornerShape(999.dp))
            .clickable { onClick() }.padding(horizontal = 14.dp, vertical = 9.dp))
}

/** v69 `#bottom`: `#hint` pill over the `#tools` chip row (scrolls), 10 px sides, safe area + 10 px below, gap 8. */
@Composable
fun BottomBar(s: ChromeState, onTool: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalSs.current
    Column(modifier.fillMaxWidth().windowInsetsPadding(BottomInsets).padding(start = 10.dp, end = 10.dp, bottom = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(s.hint, fontSize = 12.5.sp, color = c.fg, textAlign = TextAlign.Center,
            modifier = Modifier.background(c.panel2, RoundedCornerShape(999.dp)).border(1.dp, c.border, RoundedCornerShape(999.dp)).padding(horizontal = 14.dp, vertical = 6.dp))
        Row(Modifier.widthIn(max = 720.dp).fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 2.dp, end = 2.dp, top = 2.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for ((id, label) in V69_TOOLS) Chip(label, id == s.tool || id == "gridsnap") { onTool(id) }   // v69: Grid snap is on by default
        }
    }
}

/** v69 `#toast`: centred, safe area + 72 px from the top, radius 12, padding 10 × 16, 13.5 px, max 90 % wide. */
@Composable
fun Toast(text: String, modifier: Modifier = Modifier) {
    val c = LocalSs.current
    Box(modifier.fillMaxWidth().windowInsetsPadding(TopInsets).padding(top = 72.dp), contentAlignment = Alignment.TopCenter) {
        Text(text, fontSize = 13.5.sp, color = c.toastFg, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(0.9f).wrapWidth().background(c.toast, RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 10.dp))
    }
}
private fun Modifier.wrapWidth() = this.then(Modifier.widthIn(max = 420.dp))

/** The About sheet (the app's, styled like v69's `#st-sheet`): version, engines, data, licence, theme, debug overlay. */
@Composable
fun AboutSheet(lines: List<String>, theme: String, onTheme: (String) -> Unit, debug: Boolean, onDebug: (Boolean) -> Unit, onClose: () -> Unit) {
    val c = LocalSs.current
    Box(Modifier.fillMaxSize().windowInsetsPadding(TopInsets).windowInsetsPadding(BottomInsets).padding(start = 8.dp, end = 8.dp, top = 64.dp, bottom = 8.dp), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().shadow(28.dp, RoundedCornerShape(16.dp)).background(c.panel, RoundedCornerShape(16.dp)).border(1.dp, c.border, RoundedCornerShape(16.dp))
            .verticalScroll(rememberScrollState()).padding(12.dp)) {
            Text("About SolidSketch Steel", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.fg, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))
            for (l in lines) Text(l, fontSize = 13.sp, color = c.fg, modifier = Modifier.padding(vertical = 2.dp))
            Text("THEME", fontSize = 13.sp, color = c.fg.copy(alpha = 0.75f), modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { for ((k, l) in listOf("system" to "As the phone", "dark" to "Dark", "light" to "Light")) com.solidsketchapp.steel.mode.StBtn(l, theme == k) { onTheme(k) } }
            Text("DEBUG", fontSize = 13.sp, color = c.fg.copy(alpha = 0.75f), modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
            com.solidsketchapp.steel.mode.StBtn(if (debug) "Debug overlay ✓" else "Debug overlay", debug) { onDebug(!debug) }
            com.solidsketchapp.steel.ui.StBar { com.solidsketchapp.steel.mode.StBtn("Close") { onClose() } }
            Spacer(Modifier.height(2.dp))
        }
    }
}

/**
 * The whole screen as v69 lays it out: the 3D view fills the window, `#top` floats over it, at the bottom either `#bottom`
 * (hint + tools) or, in Steel mode, the Steel panel (v69 `body.st-on` hides #tools / #hint); then toast, menu, About, debug.
 * Used by MainActivity and by the screenshot test (tests/steel/ui/app).
 */
@Composable
fun AppLayout(
    state: ChromeState, view: @Composable () -> Unit, steel: (@Composable () -> Unit)?,
    onUndo: () -> Unit, onRedo: () -> Unit, onDelete: () -> Unit, onSoon: (String) -> Unit, onMore: () -> Unit, onTool: (String) -> Unit,
    toast: String?, menu: (@Composable () -> Unit)?, about: (@Composable () -> Unit)?, debug: String?,
) {
    val c = LocalSs.current
    Box(Modifier.fillMaxSize().background(c.bg)) {
        view()
        TopBar(state, onUndo, onRedo, onDelete, onSoon, onMore, Modifier.align(Alignment.TopCenter))
        if (steel != null) steel() else BottomBar(state, onTool, Modifier.align(Alignment.BottomCenter))
        if (debug != null)   // below the top bar, left: never over a control
            Text(debug, fontSize = 10.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, color = c.fg,
                modifier = Modifier.align(Alignment.TopStart).windowInsetsPadding(TopInsets).padding(start = 10.dp, top = 72.dp).background(c.panel2).padding(4.dp))
        if (toast != null) com.solidsketchapp.steel.ui.SsToast(toast, Modifier.align(Alignment.TopCenter))
        menu?.invoke(); about?.invoke()
    }
}
