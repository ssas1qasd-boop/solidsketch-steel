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
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
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
 * C14 Shear plate (146), joist <- girder, the owner's video check (docs/steel/PARITY.md "C14"): R12's girder (BI320-8-120-10) and
 * joist (BI220-8-140-10) placed one by one, the 146 added through its dialog with R12's settings (Picture: gap 20, t 10; Plates: Near
 * side; Bolts: M16, 2 x 2 at 70 / 60, edge 30, horizontal edge 35, Vertical measurement Top 77.2), Pick parts (girder, joist); orbit,
 * orthographic stills (end view along the joist: the vertical dimensions; side view along the girder; top), the model saved
 * (c14/c14_case.ss: rule B on what the app built), then the dialog again: Vertical measurement -> Middle, Modify, orbit again.
 * Screen recording /sdcard/c14_rec.mp4 (ci-out/C14_recording.mp4), its own pass in tools/ci/emulator_run.sh.
 */
@RunWith(AndroidJUnit4::class)
class C14RecordingTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val act get() = compose.activity
    private val storage = TestStorage()
    private val ua get() = InstrumentationRegistry.getInstrumentation().uiAutomation
    private var shotNo = 0

    private fun mode(): SteelMode = act.testSteel ?: throw AssertionError("Steel mode not set up")
    private fun waitFor(what: String, ms: Long = 20_000, cond: () -> Boolean) {
        try { compose.waitUntil(ms) { runCatching(cond).getOrDefault(false) } } catch (e: Throwable) { throw AssertionError("timed out waiting for $what") }
    }
    private fun shot(name: String) { compose.waitForIdle(); Thread.sleep(500); compose.waitForIdle()
        ua.takeScreenshot()?.let { b -> storage.openOutputFile("c14/%02d_%s.png".format(++shotNo, name)).use { b.compress(Bitmap.CompressFormat.PNG, 100, it) } } }
    private fun sh(cmd: String) { ua.executeShellCommand(cmd).use { pfd -> ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes() } } }
    private fun press(text: String) { val n = compose.onAllNodes(hasText(text) and hasClickAction())[0]; runCatching { n.performScrollTo() }; n.performClick(); compose.waitForIdle() }
    private fun screen(p: Vec3): Offset = act.testViewport.worldToScreen(p)?.let { Offset(it.first, it.second) } ?: throw AssertionError("point $p behind the camera")
    private fun tapAt(o: Offset) { compose.onRoot().performTouchInput { click(o) }; compose.waitForIdle() }
    private fun pause(ms: Long) { compose.waitForIdle(); Thread.sleep(ms) }
    private fun member(set: String, o: String): BodyId {
        val st = JsonObject(LinkedHashMap<String, JsonElement>(mode().data.propertySet(set)!!.raw).apply { put("kind", JsonPrimitive("member")); putAll(Json.parseToJsonElement(o).jsonObject) })
        var id: BodyId? = null; act.runOnUiThread { id = mode().parts.add(st, "C14") }; compose.waitForIdle(); return id!!
    }
    private fun look(t: Vec3, dir: Vec3, d: Double) { val l = sqrt(dir.x * dir.x + dir.y * dir.y + dir.z * dir.z); val e = Vec3(t.x + dir.x / l * d, t.y + dir.y / l * d, t.z + dir.z / l * d)
        act.runOnUiThread { act.testViewport.camera.lookAt(t, e); act.testViewport.aim(t, d) } }
    private fun orbit(t: Vec3, d: Double, tag: String) {
        val views = listOf("3d" to Vec3(1.0, 1.2, 0.9), "end" to Vec3(0.02, 1.0, 0.1), "side" to Vec3(1.0, 0.02, 0.1), "top" to Vec3(0.05, 0.08, 1.0), "3d_under" to Vec3(-1.0, 1.0, -0.6), "3d" to Vec3(1.0, 1.2, 0.9))
        look(t, views[0].second, d); pause(600); shot("${tag}_3d")
        for (i in 1 until views.size) { val a = views[i - 1].second; val b = views[i].second
            for (f in 1..70) { val s = f / 70.0; val k = s * s * (3 - 2 * s); look(t, Vec3(a.x + (b.x - a.x) * k, a.y + (b.y - a.y) * k, a.z + (b.z - a.z) * k), d); Thread.sleep(33) }
            pause(700); if (i < views.size - 1) shot("${tag}_${views[i].first}") }
    }
    private fun orthoShot(t: Vec3, dir: Vec3, d: Double, name: String) {
        act.runOnUiThread { act.testViewport.camera.orthographic = true }; look(t, dir, d); pause(1800); shot(name)
        val px = act.testViewport.worldToScreen(t); val loc = IntArray(2); act.testViewportView?.getLocationOnScreen(loc)
        storage.openOutputFile("c14/%02d_%s.txt".format(shotNo, name)).use { it.write("${loc[0]} ${loc[1]} ${px?.first} ${px?.second} ${t.x} ${t.y} ${t.z} ${dir.x} ${dir.y} ${dir.z} $d\n".toByteArray()) }
        act.runOnUiThread { act.testViewport.camera.orthographic = false }
    }
    private fun field(k: String, v: String) { val n = compose.onAllNodes(hasSetTextAction() and hasAnyAncestor(hasTestTag("tk_$k")))[0]; runCatching { n.performScrollTo() }; n.performTextReplacement(v); pause(250) }
    private fun pick(k: String, option: String) { val n = compose.onAllNodesWithTag("tk_$k")[0]; runCatching { n.performScrollTo() }; n.performClick(); pause(500); compose.onAllNodes(hasText(option) and hasClickAction()).let { a -> a[a.fetchSemanticsNodes().size - 1] }.performClick(); pause(400) }
    private fun tab(name: String) { compose.onAllNodes(hasText(name) and hasClickAction())[0].performClick(); pause(700) }
    private fun tabCentre(): Vec3 { val id = act.testDoc.bodyIds().first { (mode().stOf(it)?.get("name") as? JsonPrimitive)?.contentOrNull == "SHEAR TAB" }
        val m = act.testDoc.bodyMesh(id)!!; val lo = DoubleArray(3) { 1e18 }; val hi = DoubleArray(3) { -1e18 }
        for (i in 0 until m.vertexCount) for (k in 0..2) { val v = m.positions[i * 3 + k].toDouble(); lo[k] = minOf(lo[k], v); hi[k] = maxOf(hi[k], v) }
        return Vec3((lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2, (lo[2] + hi[2]) / 2) }
    private fun refV(): String? = mode().comps!!.conns().lastOrNull()?.q?.get("refV")?.let { (it as? JsonPrimitive)?.contentOrNull }

    @Test fun c14Recording() {
        waitFor("the steel data", 60_000) { act.testSteel != null }
        compose.onAllNodes(hasText("Steel") and hasClickAction())[0].performClick()
        waitFor("the Steel panel") { compose.onAllNodes(hasText("Grid") and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
        waitFor("the component catalog data") { mode().comps != null }
        val j0 = Vec3(888.0, 16900.0, 3900.0); look(j0, Vec3(1.0, 1.2, 0.9), 4500.0); pause(800)
        val rec = ua.executeShellCommand("screenrecord --bit-rate 6000000 --time-limit 170 /sdcard/c14_rec.mp4")
        try {
            pause(1500)
            val g = member("BEAM", """{"prof":"BI320-8-120-10","p1":[334,16785,3840],"p2":[2334,16785,3840],"depth":"Middle"}"""); pause(900)
            val j = member("BEAM", """{"prof":"BI220-8-140-10","p1":[888.28,16809,3890],"p2":[888.28,19809,3890],"depth":"Middle"}"""); pause(1200)
            shot("members")
            // catalog -> Shear plate simple (146) -> its dialog: R12's settings
            press("Components")
            waitFor("the catalog") { compose.onAllNodes(hasText("Applications & components")).fetchSemanticsNodes().isNotEmpty() }
            compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("146"); pause(1200)
            val all = compose.onAllNodes(hasText("Properties") and hasClickAction()); val ns = all.fetchSemanticsNodes()
            all[ns.indices.filter { ns[it].boundsInRoot.width > 0f }.minBy { ns[it].boundsInRoot.top }].performClick()
            waitFor("the 146 dialog") { compose.onAllNodes(hasText("Pick parts") and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }; pause(1000)
            tab("Picture"); field("gap", "20"); field("t", "10"); shot("dialog_picture")
            tab("Plates"); pick("side", "Near side"); shot("dialog_plates")
            tab("Bolts"); pick("bolt", "M16"); field("e", "30"); field("rows", "2"); field("pitch", "70"); field("cols", "2"); field("pitchH", "60"); field("eh", "35"); field("posV", "77.2")
            pick("refV", "Top"); pause(600); shot("dialog_bolts")
            press("Pick parts"); pause(800)
            look(j0, Vec3(0.6, 0.5, 1.0), 2200.0); pause(900)
            val n0 = mode().comps!!.conns().size
            tapAt(screen(Vec3(1700.0, 16785.0, 3840.0))); pause(700)          // the girder (its axis, away from the joint)
            tapAt(screen(Vec3(888.28, 18800.0, 3990.0))); pause(700)          // the joist (its top, away from the joint)
            waitFor("the shear plate") { mode().comps!!.conns().size == n0 + 1 }
            act.runOnUiThread { mode().catDone() }; pause(800)
            assertTrue("new 146 refV ${refV()}", refV() == "Top")
            val c = tabCentre()
            orbit(c, 1200.0, "top77")
            orthoShot(c, Vec3(0.0, 1.0, 0.0), 700.0, "ortho_end")          // along the joist, from its far end: the vertical dimensions
            orthoShot(c, Vec3(-1.0, 0.0, 0.0), 700.0, "ortho_side")        // along the girder, onto the tab
            orthoShot(c, Vec3(0.0, 0.0, 1.0), 700.0, "ortho_top")
            look(c, Vec3(1.0, 1.2, 0.9), 1200.0); pause(600)
            run { val f = java.io.File(act.cacheDir, "c14_case.ss"); act.runOnUiThread { act.testSave(f) }; waitFor("saved", 60_000) { f.exists() && f.length() > 0 && !act.testBusy }; pause(500)
                storage.openOutputFile("c14/c14_case.ss").use { o -> f.inputStream().use { it.copyTo(o) } } }
            // the dialog of this component (its tab selected): Bolts -> Vertical measurement Middle, Modify
            val tb = act.testDoc.bodyIds().first { (mode().stOf(it)?.get("name") as? JsonPrimitive)?.contentOrNull == "SHEAR TAB" }
            act.runOnUiThread { act.testViewport.selection.setSelected(listOf(tb)) }; pause(800)
            press("Components")
            waitFor("the 146's dialog") { compose.onAllNodes(hasText("Bolts") and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }; pause(800)
            tab("Bolts"); pick("refV", "Middle"); field("posV", "0"); pause(600); shot("dialog_middle")
            press("Modify"); pause(800)
            waitFor("Vertical measurement Middle") { refV() == "Middle" }
            compose.onAllNodes(hasText("Cancel") and hasClickAction()).fetchSemanticsNodes().takeIf { it.isNotEmpty() }?.let { compose.onAllNodes(hasText("Cancel") and hasClickAction())[0].performClick() }
            act.runOnUiThread { act.testViewport.selection.setSelected(emptyList()) }; pause(800)
            orbit(tabCentre(), 1200.0, "middle")
            orthoShot(tabCentre(), Vec3(0.0, 1.0, 0.0), 700.0, "ortho_end_middle")
            pause(800)
        } finally { sh("pkill -INT screenrecord"); Thread.sleep(3000); rec.close() }
    }
}
