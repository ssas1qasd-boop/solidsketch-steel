package com.solidsketchapp.steel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.remember
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The system bar insets the UI keeps clear of; tests set fixed ones (a phone's status bar and navigation bar). */
val LocalSysInsets = androidx.compose.runtime.staticCompositionLocalOf<WindowInsets?> { null }
/** Status bar, navigation bar (3-button or gesture), cutouts and the keyboard. */
@Composable fun sysInsets(): WindowInsets = LocalSysInsets.current ?: WindowInsets.safeDrawing.union(WindowInsets.ime)
/** Insets a panel anchored at the bottom keeps clear of: the navigation bar (3-button or gesture), side cutouts, the keyboard. */
@Composable fun panelInsets(): WindowInsets = sysInsets().only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
/** Insets a sheet keeps clear of: everything (status bar, navigation bar, cutouts, keyboard). */
@Composable fun sheetInsets(): WindowInsets = sysInsets()
/** Insets a piece anchored at the top keeps clear of: the status bar and side cutouts. */
@Composable fun topInsets(): WindowInsets = sysInsets().only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)

/**
 * v69 `#st-sheet` (L13734): fixed 8 px from the sides and bottom, 64 px from the top (here inside the system bars), max 640,
 * panel background, 1 px border, radius 16, padding 12, scrolls; title `h3` 16 px, margin 4 0 10.
 */
@Composable
fun StSheet(title: String?, scroll: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalSs.current
    Box(Modifier.fillMaxSize().windowInsetsPadding(sheetInsets()).padding(start = 8.dp, end = 8.dp, top = 64.dp, bottom = 8.dp), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.testTag("st-sheet").widthIn(max = 640.dp).fillMaxWidth().fillMaxHeight().shadow(28.dp, RoundedCornerShape(16.dp)).background(c.panel, RoundedCornerShape(16.dp))
            .border(1.dp, c.border, RoundedCornerShape(16.dp)).let { if (scroll) it.verticalScroll(rememberScrollState()) else it }.padding(13.dp)) {   // padding 12 + the 1 px border
            if (title != null) SheetTitle(title)
            content()
        }
    }
}

/** v69 `#toast` (L50): centred, 72 px below the top safe area, `--toast` colours, radius 12, padding 10 × 16, 13.5 px, max 90 % wide. */
@Composable
fun SsToast(text: String, modifier: Modifier = Modifier) {
    val c = LocalSs.current
    Box(modifier.fillMaxWidth().windowInsetsPadding(topInsets()).padding(top = 72.dp), contentAlignment = Alignment.TopCenter) {
        Text(text, fontSize = 13.5.sp, color = c.toastFg, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(0.9f).widthIn(max = 420.dp).background(c.toast, RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 10.dp))
    }
}

/**
 * v69 `#st-sheet .bar`: the sheet's button row reuses the top bar's `.bar` class (L23: panel, 1 px border, radius 16,
 * padding 6 6 6 14, shadow) plus `#st-sheet .bar` (L13744: buttons at the end, wrap, gap 6, margin-top 12).
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun StBar(content: @Composable () -> Unit) {
    val c = LocalSs.current
    androidx.compose.foundation.layout.FlowRow(Modifier.padding(top = 12.dp).fillMaxWidth().shadow(18.dp, RoundedCornerShape(16.dp), ambientColor = androidx.compose.ui.graphics.Color(0x1F000000), spotColor = androidx.compose.ui.graphics.Color(0x1F000000))
        .background(c.panel, RoundedCornerShape(16.dp)).border(1.dp, c.border, RoundedCornerShape(16.dp)).padding(start = 14.dp, end = 5.dp, top = 7.dp, bottom = 7.dp),   // v69: 46 px high with 32 px buttons, 5 px from the right
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp, Alignment.End), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) { content() }
}

/** v69 `#st-sheet h3`: 16 px bold, margin 4 0 10 (a 19 px line box in Chrome). */
@Composable fun SheetTitle(title: String) = Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = LocalSs.current.fg, style = cssLine(19), modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))

/** A CSS line box: the text centred in a line of [px] CSS px (Chrome's "normal" Roboto line is 1.17 × the font size, snapped to
 *  whole px), with linear (unhinted) glyph advances as Chrome lays text out, so a word is as wide as in v69 (UI golden). */
fun cssLine(px: Number) = androidx.compose.ui.text.TextStyle(lineHeight = px.toFloat().sp, textMotion = androidx.compose.ui.text.style.TextMotion.Animated,
    lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center, androidx.compose.ui.text.style.LineHeightStyle.Trim.None),
    platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false))

/** v69 `#st-sheet input` (and the catalog's `.srch`): 14 px, padding 6 × 8, radius 8, border var(--border), 30 px high with the
 *  border. The whole box is the text field (a tap anywhere in it focuses it; its bounds are the input's bounds). */
@Composable
fun SsInput(value: String, onChange: (String) -> Unit, placeholder: String = "", modifier: Modifier = Modifier) {
    val c = LocalSs.current
    androidx.compose.foundation.text.BasicTextField(value, onChange, singleLine = true, textStyle = cssLine(16).copy(fontSize = 14.sp, color = c.fg),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(c.fg), modifier = modifier,
        decorationBox = { inner -> Box(Modifier.border(1.dp, c.border, RoundedCornerShape(8.dp)).padding(horizontal = 9.dp, vertical = 7.dp)) {
            if (value.isEmpty()) Text(placeholder, color = c.fg.copy(alpha = 0.5f), fontSize = 14.sp, style = cssLine(16), maxLines = 1, softWrap = false)
            inner() } })
}

/** One line of text as wide as Chrome makes it: Compose rounds a text's width up to whole px, and each dp of padding to whole
 *  px, while Chrome keeps the fractions, so a row of buttons drifts by up to 1.5 px a button. This lays the text out in a box
 *  [pad] wider on each side (the button's padding and border), rounded once to the nearest px (UI golden). */
@Composable
fun CssText(text: String, style: androidx.compose.ui.text.TextStyle, modifier: Modifier = Modifier, pad: androidx.compose.ui.unit.Dp = 0.dp) {
    val tm = androidx.compose.ui.text.rememberTextMeasurer(); val dens = androidx.compose.ui.platform.LocalDensity.current
    val st = androidx.compose.material3.LocalTextStyle.current.merge(style)
    // the line's exact width (a paragraph's intrinsic width is rounded up to whole px)
    val w = remember(text, st, dens) { tm.measure(text, st, softWrap = false, maxLines = 1).let { it.getLineRight(0) - it.getLineLeft(0) } }
    Text(text, style = st, maxLines = 1, softWrap = false, modifier = modifier.layout { m, _ ->
        val p = m.measure(androidx.compose.ui.unit.Constraints()); val wp = kotlin.math.round(w + 2 * pad.toPx()).toInt()
        layout(wp, p.height) { p.place((wp - p.width) / 2, 0) } })
}

/**
 * Rows stacked the way Chrome stacks them: each row is a whole number of CSS px high (its measured height snapped back to
 * CSS px), and rows start at the running total in fractional device px, rounded once. Compose rounds every row's height
 * to whole device px instead, so a long sheet drifts by half a px a row (UI golden: the Portal frame sheet's 26 rows).
 */
@Composable
fun CssStack(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    androidx.compose.ui.layout.Layout(content, modifier) { ms, c ->
        val ps = ms.map { it.measure(c.copy(minHeight = 0, maxHeight = androidx.compose.ui.unit.Constraints.Infinity)) }
        val ys = ArrayList<Int>(); var yf = 0f
        for (p in ps) { ys += kotlin.math.round(yf).toInt(); yf += kotlin.math.round(p.height / density) * density }
        layout(c.maxWidth, kotlin.math.round(yf).toInt()) { ps.forEachIndexed { i, p -> p.place(0, ys[i]) } }
    }
}
