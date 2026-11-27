package com.solidsketchapp.steel

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.services.storage.TestStorage
import com.solidsketchapp.core.api.Vec3
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.sqrt

/**
 * R13 (reference since 2026-11-28) rule-B failure groups (owner, 2026-11-25, step 1): each group's piece of R13 (tests/steel/golden/r13/views/R13_<group>.ss, cut
 * out of R13 unchanged by tools/golden/r12_views.py prepare; the two parts red / blue) opened in the app, ORTHOGRAPHIC camera at the
 * measured spot looking along the contact face; one screenshot per group (r13/NN_<group>.png) and the spot's pixel on screen
 * (r13/NN_<group>.txt: x y of the view's top-left on screen + the spot's pixel in the view) for the labels drawn by
 * r12_views.py compose.
 */
@RunWith(AndroidJUnit4::class)
class R13ViewsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val act get() = compose.activity
    private val storage = TestStorage()
    private val ua get() = InstrumentationRegistry.getInstrumentation().uiAutomation

    private fun waitFor(what: String, ms: Long = 20_000, cond: () -> Boolean) {
        try { compose.waitUntil(ms) { runCatching(cond).getOrDefault(false) } } catch (e: Throwable) { throw AssertionError("timed out waiting for $what") }
    }
    private fun pause(ms: Long) { compose.waitForIdle(); Thread.sleep(ms) }

    @Test fun r13FailureGroups() {
        waitFor("the steel data", 60_000) { act.testSteel != null }
        val ctx = InstrumentationRegistry.getInstrumentation().context
        val views = Json.parseToJsonElement(ctx.assets.open("views.json").bufferedReader().readText()).jsonArray
        var n = 0
        for (v in views) {
            val o = v.jsonObject; val key = o.getValue("key").jsonPrimitive.content; val file = o.getValue("file").jsonPrimitive.content
            val f = java.io.File(act.cacheDir, file); ctx.assets.open(file).use { i -> f.outputStream().use { i.copyTo(it) } }
            act.runOnUiThread { act.testOpen(f) }
            waitFor("$file opened", 120_000) { act.testToast?.startsWith("Opened") == true && !act.testBusy }
            pause(1500)
            val t = o.getValue("target").jsonArray.map { it.jsonPrimitive.double }; val d = o.getValue("dir").jsonArray.map { it.jsonPrimitive.double }
            val dist = o.getValue("dist").jsonPrimitive.double; val l = sqrt(d.sumOf { it * it })
            val tv = Vec3(t[0], t[1], t[2]); val eye = Vec3(t[0] + d[0] / l * dist, t[1] + d[1] / l * dist, t[2] + d[2] / l * dist)
            act.runOnUiThread { act.testViewport.camera.orthographic = true; act.testViewport.camera.lookAt(tv, eye); act.testViewport.aim(tv, dist) }
            pause(2500)
            n++
            compose.waitForIdle(); Thread.sleep(500)
            ua.takeScreenshot()?.let { b -> storage.openOutputFile("r13/%02d_%s.png".format(n, key)).use { b.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            val px = act.testViewport.worldToScreen(tv); val loc = IntArray(2); act.testViewportView?.getLocationOnScreen(loc)
            storage.openOutputFile("r13/%02d_%s.txt".format(n, key)).use { it.write("${loc[0]} ${loc[1]} ${px?.first} ${px?.second}\n".toByteArray()) }
            act.runOnUiThread { act.testViewport.camera.orthographic = false }
        }
    }
}
