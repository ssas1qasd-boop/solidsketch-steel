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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Insets a panel anchored at the bottom keeps clear of: the navigation bar (3-button or gesture), side cutouts, the keyboard. */
@Composable fun panelInsets(): WindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal).union(WindowInsets.ime.only(WindowInsetsSides.Bottom))
/** Insets a sheet keeps clear of: everything (status bar, navigation bar, cutouts, keyboard). */
@Composable fun sheetInsets(): WindowInsets = WindowInsets.safeDrawing.union(WindowInsets.ime)

/**
 * v69 `#st-sheet` (L13734): fixed 8 px from the sides and bottom, 64 px from the top (here inside the system bars), max 640,
 * panel background, 1 px border, radius 16, padding 12, scrolls; title `h3` 16 px, margin 4 0 10.
 */
@Composable
fun StSheet(title: String?, scroll: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalSs.current
    Box(Modifier.fillMaxSize().windowInsetsPadding(sheetInsets()).padding(start = 8.dp, end = 8.dp, top = 64.dp, bottom = 8.dp), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().fillMaxHeight().shadow(28.dp, RoundedCornerShape(16.dp)).background(c.panel, RoundedCornerShape(16.dp))
            .border(1.dp, c.border, RoundedCornerShape(16.dp)).let { if (scroll) it.verticalScroll(rememberScrollState()) else it }.padding(12.dp)) {
            if (title != null) Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = c.fg, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))
            content()
        }
    }
}

/** v69 `#toast` (L50): centred, 72 px below the top safe area, `--toast` colours, radius 12, padding 10 × 16, 13.5 px, max 90 % wide. */
@Composable
fun SsToast(text: String, modifier: Modifier = Modifier) {
    val c = LocalSs.current
    Box(modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)).padding(top = 72.dp), contentAlignment = Alignment.TopCenter) {
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
        .background(c.panel, RoundedCornerShape(16.dp)).border(1.dp, c.border, RoundedCornerShape(16.dp)).padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp, Alignment.End), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) { content() }
}
