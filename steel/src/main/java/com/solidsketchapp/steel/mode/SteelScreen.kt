package com.solidsketchapp.steel.mode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * S0 placeholder: proves the module, JNI chain and license wiring.
 * Real Steel UI (copied from web v69) starts in S5.
 */
@Composable
fun SteelScreen(host: SteelHost, coreInfo: List<String>, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("SolidSketch Steel")
        Text("Plan: ${host.license.plan ?: "-"}")
        Text(NativeSteel.version())
        Text("links ${NativeSteel.coreVersion()}")
        coreInfo.forEach { Text(it) }
    }
}
