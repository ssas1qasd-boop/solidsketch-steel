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
 * C17 Shear plate 146 as a hanger (docs/steel/PARITY.md "C17"), the owner's video check: R12's gable column top EGC2001 under rafter R3002,
 * Shear plate (146) from the catalog with R12's settings through its dialog (Plates: tab 10, b 190, Main part face = the secondary web's
 * uphill face; Picture gap 49.056; Bolts: 2 x 2 M16 at 60 / 60, e 30, horizontal edge 40.949, Vertical measurement Middle 34.994),
 * Pick parts (the rafter, then the column); orbit; orthographic elevations in landscape; saved (c17/c17_case.ss). Then the dialog again:
 * Main part face -> v69 (at the secondary's axis), Modify, orbit, stills, saved (c17_v69face.ss). /sdcard/c17_rec.mp4.
 */
@RunWith(AndroidJUnit4::class)
class C17RecordingTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val act get() = compose.activity
    private val storage = TestStorage()
    private val ua get() = InstrumentationRegistry.getInstrumentation().uiAutomation
    private var shotNo = 0; private var dir = "c02"

    private fun mode(): SteelMode = act.testSteel ?: throw AssertionError("Steel mode not set up")
    private fun waitFor(what: String, ms: Long = 20_000, cond: () -> Boolean) {
        try { compose.waitUntil(ms) { runCatching(cond).getOrDefault(false) } } catch (e: Throwable) { throw AssertionError("timed out waiting for $what") }
    }
    private fun shot(name: String) { compose.waitForIdle(); Thread.sleep(500); compose.waitForIdle()
        ua.takeScreenshot()?.let { b -> storage.openOutputFile("$dir/%02d_%s.png".format(++shotNo, name)).use { b.compress(Bitmap.CompressFormat.PNG, 100, it) } } }
    private fun sh(cmd: String) { ua.executeShellCommand(cmd).use { pfd -> ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes() } } }
    private fun press(text: String) { val n = compose.onAllNodes(hasText(text) and hasClickAction())[0]; runCatching { n.performScrollTo() }; n.performClick(); compose.waitForIdle() }
    private fun screen(p: Vec3): Offset = act.testViewport.worldToScreen(p)?.let { Offset(it.first, it.second) } ?: throw AssertionError("point $p behind the camera")
    /** The steel member nearest [p] (its mesh's box), for [hitParts]. */
    private fun nearMember(p: Vec3): BodyId? = act.testDoc.bodyIds().filter { (mode().stOf(it)?.get("kind") as? JsonPrimitive)?.contentOrNull == "member" }.minByOrNull { id ->
        val m = act.testDoc.bodyMesh(id)!!; val lo = DoubleArray(3) { 1e18 }; val hi = DoubleArray(3) { -1e18 }
        for (i in 0 until m.vertexCount) for (k in 0..2) { val v = m.positions[i * 3 + k].toDouble(); lo[k] = minOf(lo[k], v); hi[k] = maxOf(hi[k], v) }
        val q = doubleArrayOf(p.x, p.y, p.z); var d = 0.0; for (k in 0..2) { val e = maxOf(lo[k] - q[k], 0.0, q[k] - hi[k]); d += e * e }; d }
    /** When the taps on the parts were not taken (a stale view size after the dialog), the same picks by the parts under the points. */
    private fun hitParts(type: String, a: Vec3, b: Vec3) {
        if (mode().catK == null) { act.runOnUiThread { mode().catPick(type) }; pause(400) }
        for (p in listOf(a, b)) { val id = nearMember(p); act.runOnUiThread { mode().catHit(id, doubleArrayOf(p.x, p.y, p.z)) }; pause(900) }
    }
    private fun tapAt(o: Offset) { compose.onRoot().performTouchInput { click(o) }; compose.waitForIdle() }
    private fun pause(ms: Long) { compose.waitForIdle(); Thread.sleep(ms) }
    private fun member(set: String, o: String): BodyId {
        val st = JsonObject(LinkedHashMap<String, JsonElement>(mode().data.propertySet(set)!!.raw).apply { put("kind", JsonPrimitive("member")); putAll(Json.parseToJsonElement(o).jsonObject) })
        var id: BodyId? = null; act.runOnUiThread { id = mode().parts.add(st, dir.uppercase()) }; compose.waitForIdle(); return id!!
    }
    private fun look(t: Vec3, d0: Vec3, d: Double) { val l = sqrt(d0.x * d0.x + d0.y * d0.y + d0.z * d0.z); val e = Vec3(t.x + d0.x / l * d, t.y + d0.y / l * d, t.z + d0.z / l * d)
        act.runOnUiThread { act.testViewport.camera.lookAt(t, e); act.testViewport.aim(t, d) } }
    private fun orbit(t: Vec3, d: Double, tag: String) {   // front (along -y: the haunch's profile) / end / top / 3D
        val views = listOf("3d" to Vec3(0.9, -1.2, 0.7), "front" to Vec3(0.02, -1.0, 0.05), "end" to Vec3(1.0, -0.02, 0.05), "top" to Vec3(0.05, -0.08, 1.0), "3d_under" to Vec3(-0.8, -1.0, -0.6), "3d" to Vec3(0.9, -1.2, 0.7))
        look(t, views[0].second, d); pause(600); shot("${tag}_3d")
        for (i in 1 until views.size) { val a = views[i - 1].second; val b = views[i].second
            for (f in 1..60) { val s = f / 60.0; val k = s * s * (3 - 2 * s); look(t, Vec3(a.x + (b.x - a.x) * k, a.y + (b.y - a.y) * k, a.z + (b.z - a.z) * k), d); Thread.sleep(33) }
            pause(600); if (i < views.size - 1) shot("${tag}_${views[i].first}") }
    }
    private fun orthoShot(t: Vec3, d0: Vec3, d: Double, name: String) {
        act.runOnUiThread { act.testViewport.camera.orthographic = true }; look(t, d0, d); pause(1800); shot(name)
        val px = act.testViewport.worldToScreen(t); val loc = IntArray(2); act.testViewportView?.getLocationOnScreen(loc)
        storage.openOutputFile("$dir/%02d_%s.txt".format(shotNo, name)).use { it.write("${loc[0]} ${loc[1]} ${px?.first} ${px?.second} ${t.x} ${t.y} ${t.z} ${d0.x} ${d0.y} ${d0.z} $d\n".toByteArray()) }
        act.runOnUiThread { act.testViewport.camera.orthographic = false }
    }
    private fun field(k: String, v: String) { val n = compose.onAllNodes(hasSetTextAction() and hasAnyAncestor(hasTestTag("tk_$k")))[0]; runCatching { n.performScrollTo() }; n.performTextReplacement(v); pause(250) }
    private fun pick(k: String, option: String) { val n = compose.onAllNodesWithTag("tk_$k")[0]; runCatching { n.performScrollTo() }; n.performClick(); pause(500); compose.onAllNodes(hasText(option) and hasClickAction()).let { a -> a[a.fetchSemanticsNodes().size - 1] }.performClick(); pause(400) }
    private fun tab(name: String) { val n = compose.onAllNodes(hasText(name) and hasClickAction())[0]; runCatching { n.performScrollTo() }; n.performClick(); pause(700) }   // the tab row scrolls: 1042 has 9 tabs
    private fun bodyNamed(name: String) = act.testDoc.bodyIds().last { (mode().stOf(it)?.get("name") as? JsonPrimitive)?.contentOrNull == name }
    private fun centre(id: BodyId): Vec3 { val m = act.testDoc.bodyMesh(id)!!; val lo = DoubleArray(3) { 1e18 }; val hi = DoubleArray(3) { -1e18 }
        for (i in 0 until m.vertexCount) for (k in 0..2) { val v = m.positions[i * 3 + k].toDouble(); lo[k] = minOf(lo[k], v); hi[k] = maxOf(hi[k], v) }
        return Vec3((lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2, (lo[2] + hi[2]) / 2) }
    private fun q(type: String, k: String): String? = mode().comps!!.conns().lastOrNull { it.type == type }?.q?.get(k)?.let { (it as? JsonPrimitive)?.contentOrNull }
    private fun openCatalog(search: String) {
        press("Components"); waitFor("the catalog") { compose.onAllNodes(hasText("Applications & components")).fetchSemanticsNodes().isNotEmpty() }
        for (k in 0 until 4) { compose.onAllNodes(hasSetTextAction())[0].performTextReplacement(search); pause(1200 + 800L * k)   // the search may be cleared by a late recomposition: type it again
            if (compose.onAllNodes(hasText("Properties") and hasClickAction()).fetchSemanticsNodes().any { it.boundsInRoot.width > 0f }) break }
        val all = compose.onAllNodes(hasText("Properties") and hasClickAction()); val ns = all.fetchSemanticsNodes()
        all[ns.indices.filter { ns[it].boundsInRoot.width > 0f }.minBy { ns[it].boundsInRoot.top }].performClick()
        waitFor("the dialog") { compose.onAllNodes(hasText("Pick parts") and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }; pause(1000)
    }
    /** Pick parts: the two parts at the given points (another pair if a tap missed), until a component of [type] is added. */
    private fun pickParts(type: String, cam: () -> Unit, pairs: List<Pair<Vec3, Vec3>>) {
        sh("input keyevent 111"); pause(600); press("Pick parts"); pause(800); cam(); pause(1200)
        val n0 = mode().comps!!.conns().count { it.type == type }
        for ((a, b) in pairs) { if (mode().comps!!.conns().count { it.type == type } > n0) break
            act.runOnUiThread { mode().catPick(mode().catK ?: type) }; pause(400); shot("pick_${type}"); tapAt(screen(a)); pause(700); tapAt(screen(b)); pause(1300) }
        waitFor("the $type") { mode().comps!!.conns().count { it.type == type } == n0 + 1 }
        act.runOnUiThread { mode().catDone() }; pause(800)
    }
    private fun save(name: String) { val f = java.io.File(act.cacheDir, name); act.runOnUiThread { act.testSave(f) }; waitFor("saved", 60_000) { f.exists() && f.length() > 0 && !act.testBusy }; pause(500)
        storage.openOutputFile("$dir/$name").use { o -> f.inputStream().use { it.copyTo(o) } } }
    private fun start() {
        waitFor("the steel data", 60_000) { act.testSteel != null }
        compose.onAllNodes(hasText("Steel") and hasClickAction())[0].performClick()
        waitFor("the Steel panel") { compose.onAllNodes(hasText("Grid") and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
        waitFor("the component catalog data") { mode().comps != null }
    }

    private fun landscape(on: Boolean) { ua.setRotation(if (on) android.app.UiAutomation.ROTATION_FREEZE_90 else android.app.UiAutomation.ROTATION_FREEZE_0); pause(2500) }
    private fun closeDialog() { compose.onAllNodes(hasText("Cancel") and hasClickAction()).fetchSemanticsNodes().takeIf { it.isNotEmpty() }?.let { compose.onAllNodes(hasText("Cancel") and hasClickAction())[0].performClick() }
        act.runOnUiThread { act.testViewport.selection.setSelected(emptyList()) }; pause(800) }
    private val top = Vec3(43298.0, 75.0, 8000.0)
    private fun stills(tag: String) {
        landscape(true); orthoShot(top, Vec3(0.02, -1.0, 0.05), 420.0, "ortho_front$tag"); orthoShot(top, Vec3(1.0, 0.02, 0.05), 420.0, "ortho_side$tag"); landscape(false)
    }

    @Test fun c17Recording() {
        dir = "c17"; start()
        look(top, Vec3(0.8, -1.0, 0.5), 2500.0); pause(800)
        val rec = ua.executeShellCommand("screenrecord --bit-rate 6000000 --time-limit 175 /sdcard/c17_rec.mp4")
        try {
            pause(1500)
            member("RAFTER", """{"prof":"BI220-8-150-10","p1":[39798.613,75,7411.204],"p2":[46290.5,75,8887.194],"rot":"Top","rAng":0,"plane":"Middle","depth":"Middle"}"""); pause(900)
            member("COLUMN", """{"prof":"BI220-6-200-10","p1":[43290.5,110,230],"p2":[43290.5,110,8043.935],"rot":"Back","rAng":0,"plane":"Middle","depth":"Middle"}"""); pause(1200)
            shot("members")
            openCatalog("Shear plate simple (146)")
            tab("Picture"); field("gap", "49.056"); field("t", "10"); shot("dialog146_picture")
            tab("Plates"); field("tb", "190"); pick("hang", "At the secondary web's uphill face; the tab ends on the face (hanger under a sloped part)"); shot("dialog146_plates")
            tab("Bolts"); field("rows", "2"); field("e", "30"); field("pitch", "60"); field("cols", "2"); field("pitchH", "60"); field("posV", "34.994"); field("eh", "40.949"); pick("refV", "Middle"); pick("bolt", "M16"); shot("dialog146_bolts")
            sh("input keyevent 111"); pause(600); press("Pick parts"); pause(800); look(top, Vec3(0.6, -1.0, 0.3), 1800.0); pause(1200)
            for (k in 0 until 3) { if (mode().comps!!.conns().any { it.type == "T146" }) break
                if (compose.onAllNodes(hasText("Pick parts") and hasClickAction()).fetchSemanticsNodes().isNotEmpty()) { sh("input keyevent 111"); pause(500); press("Pick parts"); pause(800); look(top, Vec3(0.6, -1.0, 0.3), 1800.0); pause(800) }
                act.runOnUiThread { mode().catPick(mode().catK ?: "T146") }; pause(400); shot("pick_T146")
                tapAt(screen(Vec3(42800.0 - 200 * k, 75.0, 7985.0 - 45.0 * k))); pause(700); tapAt(screen(Vec3(43290.5, 30.0, 7500.0 - 300 * k))); pause(1500) }
            if (mode().comps!!.conns().none { it.type == "T146" }) { act.runOnUiThread { mode().catPick("T146") }; pause(400); hitParts("T146", Vec3(42800.0, 75.0, 7985.0), Vec3(43290.5, 30.0, 7500.0)) }
            waitFor("the T146") { mode().comps!!.conns().count { it.type == "T146" } == 1 }
            act.runOnUiThread { mode().catDone() }; pause(800)
            assertTrue("hanger ${q("T146", "hang")}", q("T146", "hang") == "face")
            orbit(top, 1200.0, "r12")
            stills("")
            save("c17_case.ss")
            // the dialog again: Main part face -> v69, Modify
            act.runOnUiThread { act.testViewport.selection.setSelected(listOf(bodyNamed("SHEAR TAB"))) }; pause(800)
            press("Components")
            waitFor("the T146 dialog") { compose.onAllNodes(hasText("Plates") and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }; pause(800)
            tab("Plates"); pick("hang", "At the secondary's axis (v69)"); pause(600); shot("dialog146_v69face")
            press("Modify"); pause(800)
            waitFor("v69 face") { mode().comps!!.conns().count { it.type == "T146" && ((it.q["hang"] as? JsonPrimitive)?.contentOrNull ?: "") == "" } == 1 }
            closeDialog()
            orbit(top, 1200.0, "v69face")
            stills("_v69face")
            save("c17_v69face.ss")
            pause(800)
        } finally { runCatching { ua.setRotation(android.app.UiAutomation.ROTATION_UNFREEZE) }; sh("pkill -INT screenrecord"); Thread.sleep(3000); rec.close() }
    }
}
