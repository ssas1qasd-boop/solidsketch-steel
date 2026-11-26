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
 * C13 End plate (144) at the mezzanine, girder -> column flange (docs/steel/PARITY.md "C13"), the owner's video check: R12's joint C2015 /
 * EB4002 (BI320 column, BI320 girder on its flange) placed one by one, the 144 through its dialog with R12's settings (Picture t 16,
 * 1 = -55, 2 = -55, gap 0; Haunch tab: Top / Bottom plate 10 x 55 x 50, chamfers 12.5 line, created = the girder-side 'Stiff.' plates;
 * Bolts M16, 4 rows, 6 = 30, s = 70 230 70, 5 = 60, 3 = 30, Tekla length, direction 2), Pick parts; orbit; the model saved
 * (c13/c13_case.ss: R12's joint). Then 144's Stiffeners tab (R12's own column stiffeners there are Column with stiffeners (186)'s; the
 * tab makes Tekla's Top / Bottom NS / FS): created, square, t 10, b 70, Modify, orbit, saved (c13_stiff.ss); then Top NS b 50, Modify,
 * orbit. Orthographic stills in landscape for the long girder. /sdcard/c13_rec.mp4.
 */
@RunWith(AndroidJUnit4::class)
class C13RecordingTest {
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
    private fun tab(name: String) { compose.onAllNodes(hasText(name) and hasClickAction())[0].performClick(); pause(700) }
    private fun bodyNamed(name: String) = act.testDoc.bodyIds().last { (mode().stOf(it)?.get("name") as? JsonPrimitive)?.contentOrNull == name }
    private fun centre(id: BodyId): Vec3 { val m = act.testDoc.bodyMesh(id)!!; val lo = DoubleArray(3) { 1e18 }; val hi = DoubleArray(3) { -1e18 }
        for (i in 0 until m.vertexCount) for (k in 0..2) { val v = m.positions[i * 3 + k].toDouble(); lo[k] = minOf(lo[k], v); hi[k] = maxOf(hi[k], v) }
        return Vec3((lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2, (lo[2] + hi[2]) / 2) }
    private fun q(type: String, k: String): String? = mode().comps!!.conns().lastOrNull { it.type == type }?.q?.get(k)?.let { (it as? JsonPrimitive)?.contentOrNull }
    private fun openCatalog(search: String) {
        press("Components"); waitFor("the catalog") { compose.onAllNodes(hasText("Applications & components")).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement(search); pause(1200)
        val all = compose.onAllNodes(hasText("Properties") and hasClickAction()); val ns = all.fetchSemanticsNodes()
        all[ns.indices.filter { ns[it].boundsInRoot.width > 0f }.minBy { ns[it].boundsInRoot.top }].performClick()
        waitFor("the dialog") { compose.onAllNodes(hasText("Pick parts") and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }; pause(1000)
    }
    /** Pick parts: the two parts at the given points (another pair if a tap missed), until a component of [type] is added. */
    private fun pickParts(type: String, cam: () -> Unit, pairs: List<Pair<Vec3, Vec3>>) {
        press("Pick parts"); pause(800); cam(); pause(1200)
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
    private fun endPlateCentre() = centre(act.testDoc.bodyIds().last { (mode().stOf(it)?.get("name") as? JsonPrimitive)?.contentOrNull == "END PLATE" })

    @Test fun c13Recording() {
        dir = "c13"; start()
        val j = Vec3(13140.0, 16785.0, 3840.0); look(j, Vec3(-0.9, -1.2, 0.7), 3500.0); pause(800)
        val rec = ua.executeShellCommand("screenrecord --bit-rate 6000000 --time-limit 178 /sdcard/c13_rec.mp4")
        try {
            pause(1500)
            member("COLUMN", """{"prof":"BI320-8-150-10","p1":[13303,16785,230],"p2":[13303,16785,7534.104],"rot":"Below","plane":"Middle","depth":"Middle"}"""); pause(900)
            member("BEAM", """{"prof":"BI320-8-120-10","p1":[334,16785,3840],"p2":[13127,16785,3840],"rot":"Top","plane":"Middle","depth":"Middle"}"""); pause(1200)
            shot("members")
            openCatalog("144")
            tab("Picture"); field("tp", "16"); field("ext", "-55"); field("extb", "-55"); field("gap", "0"); shot("dialog144_picture")
            tab("Haunch"); field("hpt1", "10"); field("hpb1", "55"); field("hph1", "50"); field("hpt2", "10"); field("hpb2", "55"); field("hph2", "50")
            field("hc1", "12.5"); field("hc2", "12.5"); field("hc3", "12.5"); field("hc4", "12.5"); pick("hct", "Line chamfer"); pick("hpc", "Top and bottom haunch plates are created"); shot("dialog144_haunch")
            tab("Stiffeners"); pick("scr", "No stiffeners are created"); shot("dialog144_stiffeners_none")
            tab("Bolts"); pick("bolt", "M16"); field("rows", "4"); field("e", "30"); field("pitches", "70 230 70"); field("gauge", "60"); field("side", "30")
            pick("blrule", "Tekla (grip + nut + washer + 5, up to 5 mm)"); pick("bdir", "Bolting direction 2"); shot("dialog144_bolts")
            // picks on the web faces, square to the camera: the column's web (y 16785 - 4) above the joint, the girder's web away from it
            val cam = { look(Vec3(12300.0, 16785.0, 3900.0), Vec3(-0.12, -1.0, 0.22), 6500.0) }
            val pairs = listOf(4700.0 to 11500.0, 5100.0 to 11000.0, 4400.0 to 12000.0, 5400.0 to 10600.0).map { (cz, bx) -> Vec3(13303.0, 16775.0, cz) to Vec3(bx, 16775.0, 3840.0) }
            pickParts("T144", cam, pairs)
            assertTrue("144 haunch plates ${q("T144", "hpc")}", q("T144", "hpc") == "both")
            val c = endPlateCentre()
            orbit(c, 1600.0, "r12")
            landscape(true); orthoShot(Vec3(c.x - 60.0, c.y, c.z), Vec3(0.0, -1.0, 0.0), 1100.0, "ortho_front"); landscape(false)   // along the frame line: end plate, Stiff. plates, bolts
            save("c13_case.ss")
            // 144's Stiffeners tab: Tekla's column stiffeners
            act.runOnUiThread { act.testViewport.selection.setSelected(listOf(act.testDoc.bodyIds().last { (mode().stOf(it)?.get("name") as? JsonPrimitive)?.contentOrNull == "END PLATE" })) }; pause(800)
            press("Components")
            waitFor("the 144's dialog") { compose.onAllNodes(hasText("Stiffeners") and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }; pause(800)
            tab("Stiffeners"); pick("scr", "Stiffeners are created"); pick("ssh", "Square stiffener plates")
            for (k in 1..4) { field("stt$k", "10"); field("stb$k", "70") }
            shot("dialog144_stiffeners"); press("Modify"); pause(800)
            waitFor("stiffeners created") { q("T144", "scr") == "both" }
            compose.onAllNodes(hasText("Cancel") and hasClickAction()).fetchSemanticsNodes().takeIf { it.isNotEmpty() }?.let { compose.onAllNodes(hasText("Cancel") and hasClickAction())[0].performClick() }
            act.runOnUiThread { act.testViewport.selection.setSelected(emptyList()) }; pause(800)
            val cs = Vec3(13303.0, c.y, c.z)
            orbit(cs, 1500.0, "stiff70")
            // onto the column's open side (along y): the stiffeners between its flanges, flush with the girder's flanges
            landscape(true); orthoShot(Vec3(cs.x - 120.0, cs.y, cs.z), Vec3(0.0, -1.0, 0.0), 1100.0, "ortho_front_stiff"); landscape(false)
            save("c13_stiff.ss")
            act.runOnUiThread { act.testViewport.selection.setSelected(listOf(act.testDoc.bodyIds().last { (mode().stOf(it)?.get("name") as? JsonPrimitive)?.contentOrNull == "END PLATE" })) }; pause(800)
            press("Components")
            waitFor("the 144's dialog again") { compose.onAllNodes(hasText("Stiffeners") and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }; pause(800)
            tab("Stiffeners"); field("stb1", "50"); pause(600); shot("dialog144_topNS_b50")
            press("Modify"); pause(800)
            waitFor("Top NS b 50") { q("T144", "stb1")?.toDoubleOrNull() == 50.0 }
            compose.onAllNodes(hasText("Cancel") and hasClickAction()).fetchSemanticsNodes().takeIf { it.isNotEmpty() }?.let { compose.onAllNodes(hasText("Cancel") and hasClickAction())[0].performClick() }
            act.runOnUiThread { act.testViewport.selection.setSelected(emptyList()) }; pause(800)
            orbit(cs, 1500.0, "topNS50")
            landscape(true); orthoShot(Vec3(cs.x - 120.0, cs.y, cs.z), Vec3(0.0, -1.0, 0.0), 1100.0, "ortho_front_b50"); landscape(false)
            pause(800)
        } finally { runCatching { ua.setRotation(android.app.UiAutomation.ROTATION_UNFREEZE) }; sh("pkill -INT screenrecord"); Thread.sleep(3000); rec.close() }
    }
}
