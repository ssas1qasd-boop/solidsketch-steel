package com.solidsketchapp.steel

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Toolbar button whose label always stays on one line (owner, S3 check: "Delete" wrapped). */
@Composable
fun ToolButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
        Text(label, maxLines = 1, softWrap = false, overflow = TextOverflow.Visible)
    }
}
