package com.solidsketchapp.steel

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.solidsketchapp.core.view.NativeCore
import com.solidsketchapp.steel.license.LicenseGate
import com.solidsketchapp.steel.license.OpenLicenseGate
import com.solidsketchapp.steel.mode.SteelHost
import com.solidsketchapp.steel.mode.SteelScreen

class MainActivity : ComponentActivity() {
    // License gate postponed (owner, 2026-11-21): always allows.
    private val gate: LicenseGate = OpenLicenseGate

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    SteelScreen(
                        host = SteelHost(license = gate),
                        coreInfo = listOf(
                            NativeCore.version(),
                            "box 10×20×30 volume = ${NativeCore.boxVolume(10f, 20f, 30f)}",
                        ),
                    )
                }
            }
        }
    }
}
