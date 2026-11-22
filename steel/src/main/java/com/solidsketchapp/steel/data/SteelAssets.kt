package com.solidsketchapp.steel.data

import android.content.res.AssetManager

/** Loads [SteelData] from the APK's `assets/steel/` folder. */
object SteelAssets {
    fun load(assets: AssetManager): SteelData =
        SteelData.load { name -> assets.open("steel/$name").bufferedReader(Charsets.UTF_8).use { it.readText() } }
}
