package com.solidsketchapp.steel

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
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
 * T144 · End plate (144), the Group 1 two-video test (docs/steel/PARITY.md "T144 · End plate (144): the two-video test"): the shared steps
 * of T144V1RecordingTest / T144V2RecordingTest (place the video's members, End plate (144) through its real dialog with the video's values,
 * Pick parts, orbit front / end / top / 3D, orthographic stills, the model saved, one option changed + Modify, orbit again).
 */
abstract class T144RecordingBase {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    protected val act get() = compose.activity
    protected val storage = TestStorage()
    protected val ua get() = InstrumentationRegistry.getInstrumentation().uiAutomation
    protected var shotNo = 0; protected var dir = "t144"

    protected fun mode(): SteelMode = act.testSteel ?: throw AssertionError("Steel mode not set up")
    protected fun waitFor(what: String, ms: Long = 20_000, cond: () -> Boolean) {
        try { compose.waitUntil(ms) { runCatching(cond).getOrDefault(false) } } catch (e: Throwable) { throw AssertionError("timed out waiting for $what") }
    }
    protected fun shot(name: String) { compose.waitForIdle(); Thread.sleep(500); compose.waitForIdle()
        ua.takeScreenshot()?.let { b -> storage.openOutputFile("$dir/%02d_%s.png".format(++shotNo, name)).use { b.compress(Bitmap.CompressFormat.PNG, 100, it) } } }
    protected fun sh(cmd: String) { ua.executeShellCommand(cmd).use { pfd -> ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes() } } }
    protected fun press(text: String) { val n = compose.onAllNodes(hasText(text) and hasClickAction())[0]; runCatching { n.performScrollTo() }; n.performClick(); compose.waitForIdle() }
    protected fun screen(p: Vec3): Offset = act.testViewport.worldToScreen(p)?.let { Offset(it.first, it.second) } ?: throw AssertionError("point $p behind the camera")
    protected fun tapAt(o: Offset) { compose.onRoot().performTouchInput { click(o) }; compose.waitForIdle() }
    protected fun pause(ms: Long) { compose.waitForIdle(); Thread.sleep(ms) }
    protected fun member(set: String, o: String): BodyId {
        val st = JsonObject(LinkedHashMap<String, JsonElement>(mode().data.propertySet(set)!!.raw).apply { put("kind", JsonPrimitive("member")); putAll(Json.parseToJsonElement(o).jsonObject) })
        var id: BodyId? = null; act.runOnUiThread { id = mode().parts.add(st, dir.uppercase()) }; compose.waitForIdle(); return id!!
    }
    protected fun look(t: Vec3, d0: Vec3, d: Double) { val l = sqrt(d0.x * d0.x + d0.y * d0.y + d0.z * d0.z); val e = Vec3(t.x + d0.x / l * d, t.y + d0.y / l * d, t.z + d0.z / l * d)
        act.runOnUiThread { act.testViewport.camera.lookAt(t, e); act.testViewport.aim(t, d) } }
    protected fun orbit(t: Vec3, d: Double, tag: String) {   // front (along -y: the haunch's profile) / end / top / 3D
        val views = listOf("3d" to Vec3(0.9, -1.2, 0.7), "front" to Vec3(0.02, -1.0, 0.05), "end" to Vec3(1.0, -0.02, 0.05), "top" to Vec3(0.05, -0.08, 1.0), "3d_under" to Vec3(-0.8, -1.0, -0.6), "3d" to Vec3(0.9, -1.2, 0.7))
        look(t, views[0].second, d); pause(600); shot("${tag}_3d")
        for (i in 1 until views.size) { val a = views[i - 1].second; val b = views[i].second
            for (f in 1..60) { val s = f / 60.0; val k = s * s * (3 - 2 * s); look(t, Vec3(a.x + (b.x - a.x) * k, a.y + (b.y - a.y) * k, a.z + (b.z - a.z) * k), d); Thread.sleep(33) }
            pause(600); if (i < views.size - 1) shot("${tag}_${views[i].first}") }
    }
    protected fun orthoShot(t: Vec3, d0: Vec3, d: Double, name: String) {
        act.runOnUiThread { act.testViewport.camera.orthographic = true }; look(t, d0, d); pause(1800); shot(name)
        val px = act.testViewport.worldToScreen(t); val loc = IntArray(2); act.testViewportView?.getLocationOnScreen(loc)
        storage.openOutputFile("$dir/%02d_%s.txt".format(shotNo, name)).use { it.write("${loc[0]} ${loc[1]} ${px?.first} ${px?.second} ${t.x} ${t.y} ${t.z} ${d0.x} ${d0.y} ${d0.z} $d\n".toByteArray()) }
        act.runOnUiThread { act.testViewport.camera.orthographic = false }
    }
    protected fun field(k: String, v: String) { val n = compose.onAllNodes(hasSetTextAction() and hasAnyAncestor(hasTestTag("tk_$k")))[0]; runCatching { n.performScrollTo() }; n.performTextReplacement(v); pause(250) }
    protected fun pick(k: String, option: String) { val n = compose.onAllNodesWithTag("tk_$k")[0]; runCatching { n.performScrollTo() }; n.performClick(); pause(500); compose.onAllNodes(hasText(option) and hasClickAction()).let { a -> a[a.fetchSemanticsNodes().size - 1] }.performClick(); pause(400) }
    protected fun tab(name: String) { compose.onAllNodes(hasText(name) and hasClickAction())[0].performClick(); pause(700) }
    protected fun bodyNamed(name: String) = act.testDoc.bodyIds().last { (mode().stOf(it)?.get("name") as? JsonPrimitive)?.contentOrNull == name }
    protected fun centre(id: BodyId): Vec3 { val m = act.testDoc.bodyMesh(id)!!; val lo = DoubleArray(3) { 1e18 }; val hi = DoubleArray(3) { -1e18 }
        for (i in 0 until m.vertexCount) for (k in 0..2) { val v = m.positions[i * 3 + k].toDouble(); lo[k] = minOf(lo[k], v); hi[k] = maxOf(hi[k], v) }
        return Vec3((lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2, (lo[2] + hi[2]) / 2) }
    protected fun q(type: String, k: String): String? = mode().comps!!.conns().lastOrNull { it.type == type }?.q?.get(k)?.let { (it as? JsonPrimitive)?.contentOrNull }
    protected fun openCatalog(search: String) {
        press("Components"); waitFor("the catalog") { compose.onAllNodes(hasText("Applications & components")).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement(search); pause(1200)
        val all = compose.onAllNodes(hasText("Properties") and hasClickAction()); val ns = all.fetchSemanticsNodes()
        all[ns.indices.filter { ns[it].boundsInRoot.width > 0f }.minBy { ns[it].boundsInRoot.top }].performClick()
        waitFor("the dialog") { compose.onAllNodes(hasText("Pick parts") and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }; pause(1000)
    }
    /** Pick parts: the two parts at the given points (another pair if a tap missed), until a component of [type] is added. */
    protected fun pickParts(type: String, cam: () -> Unit, pairs: List<Pair<Vec3, Vec3>>) {
        press("Pick parts"); pause(800); cam(); pause(1200)
        val n0 = mode().comps!!.conns().count { it.type == type }
        for ((a, b) in pairs) { if (mode().comps!!.conns().count { it.type == type } > n0) break
            act.runOnUiThread { mode().catPick(mode().catK ?: type) }; pause(400); shot("pick_${type}"); tapAt(screen(a)); pause(700); tapAt(screen(b)); pause(1300) }
        waitFor("the $type") { mode().comps!!.conns().count { it.type == type } == n0 + 1 }
        act.runOnUiThread { mode().catDone() }; pause(800)
    }
    protected fun save(name: String) { val f = java.io.File(act.cacheDir, name); act.runOnUiThread { act.testSave(f) }; waitFor("saved", 60_000) { f.exists() && f.length() > 0 && !act.testBusy }; pause(500)
        storage.openOutputFile("$dir/$name").use { o -> f.inputStream().use { it.copyTo(o) } } }
    protected fun start() {
        waitFor("the steel data", 60_000) { act.testSteel != null }
        compose.onAllNodes(hasText("Steel") and hasClickAction())[0].performClick()
        waitFor("the Steel panel") { compose.onAllNodes(hasText("Grid") and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
        waitFor("the component catalog data") { mode().comps != null }
    }

    protected fun landscape(on: Boolean) { ua.setRotation(if (on) android.app.UiAutomation.ROTATION_FREEZE_90 else android.app.UiAutomation.ROTATION_FREEZE_0); pause(2500) }
    protected fun endPlateCentre() = centre(act.testDoc.bodyIds().last { (mode().stOf(it)?.get("name") as? JsonPrimitive)?.contentOrNull == "END PLATE" })

    protected fun reopen144(tabName: String) {
        act.runOnUiThread { act.testViewport.selection.setSelected(listOf(act.testDoc.bodyIds().last { (mode().stOf(it)?.get("name") as? JsonPrimitive)?.contentOrNull == "END PLATE" })) }; pause(800)
        press("Components")
        waitFor("the 144's dialog") { compose.onAllNodes(hasText(tabName) and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }; pause(800)
    }
    protected fun modifyAndClose(what: String, cond: () -> Boolean) {
        press("Modify"); pause(800); waitFor(what, cond = cond)
        compose.onAllNodes(hasText("Cancel") and hasClickAction()).fetchSemanticsNodes().takeIf { it.isNotEmpty() }?.let { compose.onAllNodes(hasText("Cancel") and hasClickAction())[0].performClick() }
        act.runOnUiThread { act.testViewport.selection.setSelected(emptyList()) }; pause(800)
    }
    protected fun stopRec(rec: ParcelFileDescriptor) { runCatching { ua.setRotation(android.app.UiAutomation.ROTATION_UNFREEZE) }; sh("pkill -INT screenrecord"); Thread.sleep(3000); rec.close() }
}
