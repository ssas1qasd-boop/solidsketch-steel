package com.solidsketchapp.steel.license

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

// Values from #ss-lock CSS in reference/solidsketch_v69.html (fallback colours of the CSS vars).
private val Bg = Color(0xFFEEF1F5)
private val Fg = Color(0xFF1A1D24)
private val Panel = Color.White
private val Muted = Color(0xFF5B6270)
private val Chip = Color(0xFFE2E6EC)
private val Accent = Color(0xFF2F6FED)
private val Err = Color(0xFFC0392B)

/** Lock screen. [checking] hides the form and shows "Checking your license…". */
@Composable
fun LockScreen(
    message: String,
    isError: Boolean,
    checking: Boolean,
    busy: Boolean,
    onUnlock: (String) -> Unit,
) {
    var key by remember { mutableStateOf("") }
    val uri = LocalUriHandler.current
    Box(
        Modifier.fillMaxSize().background(Bg).safeDrawingPadding().padding(16.dp).verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 400.dp).fillMaxWidth()
                .shadow(30.dp, RoundedCornerShape(18.dp), ambientColor = Color(0x1F000000), spotColor = Color(0x1F000000)),
            shape = RoundedCornerShape(18.dp),
            color = Panel,
            border = BorderStroke(1.dp, Color(0x14000000)),
        ) {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 28.dp)) {
                Text("SolidSketch Pro", color = Fg, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 6.dp))
                // Web text says "The browser version is for Pro members…"; adapted for the app.
                // TODO(owner): confirm the app wording.
                Text("SolidSketch Steel is for Pro members. Enter the license key from your purchase email to open it.",
                    color = Muted, lineHeight = 1.45.em, modifier = Modifier.padding(bottom = 18.dp))
                val shownMessage = if (checking) LicenseManager.MSG_CHECKING else if (busy) "Checking…" else message
                if (!checking) {
                    BasicTextField(
                        value = key,
                        onValueChange = { key = it },
                        singleLine = true,
                        enabled = !busy,
                        textStyle = TextStyle(color = Fg, fontSize = 16.sp),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Done,
                        ),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
                            .background(Chip).border(2.dp, Color(0x26000000), RoundedCornerShape(12.dp)),
                        decorationBox = { inner ->
                            Box(Modifier.padding(horizontal = 14.dp, vertical = 14.dp)) {
                                if (key.isEmpty()) Text("License key", color = Muted, fontSize = 16.sp)
                                inner()
                            }
                        },
                    )
                    Box(
                        Modifier.padding(top = 12.dp).fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
                            .background(if (busy) Accent.copy(alpha = .55f) else Accent)
                            .then(if (busy) Modifier else Modifier.clickableNoRipple { onUnlock(key) }),
                        contentAlignment = Alignment.Center,
                    ) { Text("Unlock", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                }
                Text(shownMessage, color = if (isError && !busy && !checking) Err else Muted, fontSize = 14.72.sp,
                    modifier = Modifier.padding(top = 12.dp).heightIn(min = 20.dp))
                if (!checking) {
                    HorizontalDivider(Modifier.padding(top = 20.dp), color = Color(0x1A000000))
                    Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("No key yet?", color = Muted, fontSize = 14.72.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Link("Monthly, \$4.99", FontWeight.SemiBold) { uri.openUri(LicenseConfig.BUY_MONTHLY) }
                            Link("Yearly, \$34.99", FontWeight.SemiBold) { uri.openUri(LicenseConfig.BUY_YEARLY) }
                        }
                        Link("solidsketchapp.com", FontWeight.Medium) { uri.openUri(LicenseConfig.SITE) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Link(text: String, weight: FontWeight, onClick: () -> Unit) =
    Text(text, color = Accent, fontWeight = weight, fontSize = 14.72.sp, modifier = Modifier.clickableNoRipple(onClick))

private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.then(androidx.compose.foundation.clickable(onClick = onClick).let { Modifier.clickable(onClick = onClick) })
