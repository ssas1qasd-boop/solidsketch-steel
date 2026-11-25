package com.solidsketchapp.steel

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.services.storage.TestStorage
import com.solidsketchapp.core.api.BodyId
import com.solidsketchapp.core.api.Vec3
import com.solidsketchapp.steel.mode.SteelMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.sqrt

/**
 * C04 purlin cleat, the owner's video check (docs/steel/PARITY.md, standing rule): a screen recording of the app
 * (`/sdcard/c04_rec.mp4`, pulled by tools/ci/emulator_run.sh as ci-out/C04_recording.mp4) and still frames per view.
 * The R10 case "toes down" (rafter BI220 at 12.8°, two CC200-2-20-60 purlins butting over it, rAng 347.191): the members are
 * placed one by one, the cleat is added through the catalog → Purlin cleat L dialog → Pick parts (rafter, purlin, purlin), the view
 * orbits slowly round the cleat (3D, front = along the purlins, end = along the rafter, top, 3D), then the dialog again:
 * Cleat location → Back, Modify, and the same orbit. Runs in its own pass after EmulatorFlowTest (its own screenrecord).
 */
@RunWith(AndroidJUnit4::class)
class C04RecordingTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val act get() = compose.activity
    private val storage = TestStorage()
    private val ua get() = InstrumentationRegistry.getInstrumentation().uiAutomation
    private var shotNo = 0

    private fun mode(): SteelMode = act.testSteel ?: throw AssertionError("Steel mode not set up")
    private fun waitFor(what: String, ms: Long = 20_000, cond: () -> Boolean) {
        try { compose.waitUntil(ms) { runCatching(cond).getOrDefault(false) } } catch (e: Throwable) { throw AssertionError("timed out waiting for $what") }
    }
    private fun shot(name: String) {
        compose.waitForIdle(); Thread.sleep(500); compose.waitForIdle()
        ua.takeScreenshot()?.let { b -> storage.openOutputFile("c04/%02d_%s.png".format(++shotNo, name)).use { b.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }
    private fun sh(cmd: String) { ua.executeShellCommand(cmd).use { pfd -> ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes() } } }
    private fun press(text: String) { val n = compose.onAllNodes(hasText(text) and hasClickAction())[0]; n.performScrollTo(); n.performClick(); compose.waitForIdle() }
    private fun screen(p: Vec3): Offset = act.testViewport.worldToScreen(p)?.let { Offset(it.first, it.second) } ?: throw AssertionError("point $p behind the camera")
    private fun tapAt(o: Offset) { compose.onRoot().performTouchInput { click(o) }; compose.waitForIdle() }
    private fun pause(ms: Long) { compose.waitForIdle(); Thread.sleep(ms) }

    private fun member(set: String, o: String): BodyId {
        val st = JsonObject(LinkedHashMap<String, JsonElement>(mode().data.propertySet(set)!!.raw).apply { put("kind", JsonPrimitive("member")); putAll(Json.parseToJsonElement(o).jsonObject) })
        var id: BodyId? = null; act.runOnUiThread { id = mode().parts.add(st, "C04") }; compose.waitForIdle(); return id!!
    }

    /** Camera at [t] looking from direction [dir] (normalised here) at distance [d]. */
    private fun look(t: Vec3, dir: Vec3, d: Double) {
        val l = sqrt(dir.x * dir.x + dir.y * dir.y + dir.z * dir.z); val e = Vec3(t.x + dir.x / l * d, t.y + dir.y / l * d, t.z + dir.z / l * d)
        act.runOnUiThread { act.testViewport.camera.lookAt(t, e); act.testViewport.aim(t, d) }
    }
    /** Slow orbit through the named views; a still frame at each. */
    private fun orbit(t: Vec3, d: Double, tag: String) {
        val views = listOf("3d" to Vec3(1.0, -1.2, 0.9), "front" to Vec3(0.02, -1.0, 0.12), "end" to Vec3(1.0, 0.02, 0.12), "top" to Vec3(0.05, -0.08, 1.0), "3d_back" to Vec3(-1.0, 1.2, 0.9), "3d" to Vec3(1.0, -1.2, 0.9))
        look(t, views[0].second, d); pause(600); shot("${tag}_3d")
        for (i in 1 until views.size) { val a = views[i - 1].second; val b = views[i].second
            for (f in 1..75) { val s = f / 75.0; val k = s * s * (3 - 2 * s); look(t, Vec3(a.x + (b.x - a.x) * k, a.y + (b.y - a.y) * k, a.z + (b.z - a.z) * k), d); Thread.sleep(33) }
            pause(700); if (i < views.size - 1) shot("${tag}_${views[i].first}") }
    }
    private fun cleatCentre(): Vec3 {
        val id = act.testDoc.bodyIds().first { (mode().stOf(it)?.get("name") as? JsonPrimitive)?.contentOrNull == "CLEAT" }
        val m = act.testDoc.bodyMesh(id)!!; var lo = doubleArrayOf(1e18, 1e18, 1e18); var hi = doubleArrayOf(-1e18, -1e18, -1e18)
        for (i in 0 until m.vertexCount) for (k in 0..2) { val v = m.positions[i * 3 + k].toDouble(); lo[k] = minOf(lo[k], v); hi[k] = maxOf(hi[k], v) }
        return Vec3((lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2, (lo[2] + hi[2]) / 2)
    }
    private fun locOf(): String? = mode().comps!!.conns().lastOrNull()?.q?.get("loc")?.let { (it as? JsonPrimitive)?.contentOrNull }

    @Test fun c04Recording() {
        waitFor("the steel data", 60_000) { act.testSteel != null }
        compose.onAllNodes(hasText("Steel") and hasClickAction())[0].performClick()
        waitFor("the Steel panel") { compose.onAllNodes(hasText("Grid") and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
        waitFor("the component catalog data") { mode().comps != null }
        val pp = Vec3(6507.8, 22355.0, 8957.6); look(pp, Vec3(1.0, -1.2, 0.9), 8000.0); pause(800)

        val rec = ua.executeShellCommand("screenrecord --bit-rate 6000000 --time-limit 170 /sdcard/c04_rec.mp4")
        try {
            pause(1500)
            // the R10 case: rafter, then the two C purlins (toes downhill)
            member("RAFTER", """{"prof":"BI220-8-150-10","p1":[237.306,22355,7416.591],"p2":[6705.5,22355,8887.192]}"""); pause(900)
            val pu = """"prof":"CC200-2-20-60","rot":"Top","rAng":347.191,"plane":"Middle","depth":"Middle""""
            member("PURLIN", """{$pu,"p1":[6507.808,22365,9057.605],"p2":[6507.808,28100,9057.605]}"""); pause(900)
            member("PURLIN", """{$pu,"p1":[6507.808,16795,9057.605],"p2":[6507.808,22345,9057.605]}"""); pause(1200)
            shot("members")
            // catalog → Purlin cleat L → Properties (the dialog) → Pick parts
            press("Components")
            waitFor("the catalog") { compose.onAllNodes(hasText("Applications & components")).fetchSemanticsNodes().isNotEmpty() }
            compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("Purlin cleat"); pause(1200)
            val all = compose.onAllNodes(hasText("Properties") and hasClickAction()); val ns = all.fetchSemanticsNodes()
            all[ns.indices.filter { ns[it].boundsInRoot.width > 0f }.minBy { ns[it].boundsInRoot.top }].performClick()
            waitFor("the Purlin cleat L dialog") { compose.onAllNodes(hasText("Parts") and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
            pause(1200); compose.onAllNodes(hasText("Parts") and hasClickAction())[0].performClick(); pause(1500); shot("dialog_front")
            press("Pick parts"); pause(800)
            val n0 = mode().comps!!.conns().size
            tapAt(screen(Vec3(5600.0, 22355.0, 8636.0))); pause(700)                   // rafter (its axis, below the purlins)
            tapAt(screen(Vec3(6507.8, 23300.0, 9057.6))); pause(700)                   // purlin 1
            tapAt(screen(Vec3(6507.8, 21400.0, 9057.6))); pause(700)                   // purlin 2
            waitFor("the purlin cleat") { mode().comps!!.conns().size == n0 + 1 }
            act.runOnUiThread { mode().catDone() }; pause(800)
            assertTrue("new cleat loc ${locOf()}", locOf() == "front")
            val c = cleatCentre()
            orbit(c, 1500.0, "front")
            // the dialog of this cleat (as a tap on its symbol), Parts → Cleat location → Back, Modify
            val cl = act.testDoc.bodyIds().first { (mode().stOf(it)?.get("name") as? JsonPrimitive)?.contentOrNull == "CLEAT" }
            act.runOnUiThread { act.testViewport.selection.setSelected(listOf(cl)) }; pause(800)
            press("Components")   // with a component part selected: that component's dialog
            waitFor("the cleat's dialog") { compose.onAllNodes(hasText("Parts") and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
            pause(800); compose.onAllNodes(hasText("Parts") and hasClickAction())[0].performClick(); pause(1000)
            compose.onAllNodes(hasText("Front") and hasClickAction())[0].performClick(); pause(900)
            compose.onAllNodes(hasText("Back") and hasClickAction())[0].performClick(); pause(900); shot("dialog_back")
            press("Modify"); pause(800)
            waitFor("Cleat location Back") { locOf() == "back" }
            compose.onAllNodes(hasText("Cancel") and hasClickAction()).fetchSemanticsNodes().takeIf { it.isNotEmpty() }?.let { compose.onAllNodes(hasText("Cancel") and hasClickAction())[0].performClick() }
            act.runOnUiThread { act.testViewport.selection.setSelected(emptyList()) }; pause(800)
            orbit(cleatCentre(), 1500.0, "back")
            pause(1000)
        } finally {
            sh("pkill -INT screenrecord"); Thread.sleep(3000); rec.close()
        }
    }
}
