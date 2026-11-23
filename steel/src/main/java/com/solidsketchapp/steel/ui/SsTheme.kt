package com.solidsketchapp.steel.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * v69's theme (`:root` CSS variables, L12–17; `--sheet-solid` L8357): light by default, dark when the phone is dark
 * (`prefers-color-scheme: dark`) unless `data-theme` says otherwise. Values copied exactly (see docs/steel/UI-MAP.md).
 */
class SsColors(
    val dark: Boolean,
    val bg: Color, val panel: Color, val panel2: Color, val fg: Color, val muted: Color,
    val chip: Color, val chipFg: Color, val chipSel: Color, val chipSelFg: Color, val accent: Color, val border: Color,
    val toast: Color, val toastFg: Color, val sheetSolid: Color,
    /** three.js clear colour and the ground grid (v69 buildGrid L7936–7940). */
    val scene: Int, val gridMinor: Int, val gridMajor: Int,
) {
    /** v69 `#st-panel button` border and the "on" pill (#c0392b). */
    val pillBorder = Color(0x597F7F7F)
    val on = Color(0xFFC0392B)

    companion object {
        val Light = SsColors(false,
            bg = Color(0xFFEEF1F5), panel = Color(0xEBFFFFFF), panel2 = Color(0xCCFFFFFF), fg = Color(0xFF1A1D24), muted = Color(0xFF5B6270),
            chip = Color(0xFFE2E6EC), chipFg = Color(0xFF1A1D24), chipSel = Color(0xFF2F6FED), chipSelFg = Color(0xFFFFFFFF), accent = Color(0xFF2F6FED), border = Color(0x14000000),
            toast = Color(0xFF1A1D24), toastFg = Color(0xFFFFFFFF), sheetSolid = Color(0xFFFFFFFF),
            scene = 0xEEF1F5, gridMinor = 0xCFD4DC, gridMajor = 0xA3ABB8)
        val Dark = SsColors(true,
            bg = Color(0xFF1C1E23), panel = Color(0xF022252C), panel2 = Color(0xD122252C), fg = Color(0xFFEEF0F4), muted = Color(0xFFA2A9B6),
            chip = Color(0xFF2F333C), chipFg = Color(0xFFEEF0F4), chipSel = Color(0xFF5C9EED), chipSelFg = Color(0xFF0E1420), accent = Color(0xFF5C9EED), border = Color(0x14FFFFFF),
            toast = Color(0xFFEEF0F4), toastFg = Color(0xFF1A1D24), sheetSolid = Color(0xFF22252C),
            scene = 0x1C1E23, gridMinor = 0x2E333C, gridMajor = 0x434A56)

        /** v69 `dark()` (L7801): data-theme "light" → light, "dark" → dark, else the phone's setting. */
        fun pick(theme: String?, systemDark: Boolean) = when (theme) { "light" -> Light; "dark" -> Dark; else -> if (systemDark) Dark else Light }
    }
}

val LocalSs = staticCompositionLocalOf { SsColors.Light }

@Composable
fun SsTheme(colors: SsColors, content: @Composable () -> Unit) = CompositionLocalProvider(LocalSs provides colors, androidx.compose.material3.LocalContentColor provides colors.fg,
    // text laid out as Chrome lays out v69's: linear glyph advances, no extra font padding (UI golden)
    androidx.compose.material3.LocalTextStyle provides androidx.compose.material3.LocalTextStyle.current.merge(androidx.compose.ui.text.TextStyle(textMotion = androidx.compose.ui.text.style.TextMotion.Animated,
        platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false))), content = content)
