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
 * C02 + C02h + C09, the owner's video check (docs/steel/PARITY.md "C02h", "C09"), each its own screen recording:
 *  - c02Recording: R12's eave (BI320 column, BI220 rafter at 12.8 deg). End plate (144) through its dialog with R12's settings (Picture
 *    t 20, 1 = -10, 2 = -104.385, gap 0; Bolts M20, 4 rows, 6 = 50.045, s = 70 70 80, 5 = 70, 3 = 40, Bolt length Tekla, Bolting
 *    direction 2), Pick parts (column, rafter); then the Eave haunch (C02h) through its dialog (D = depth at the column 82.91, L = haunch length
 *    2000; Parts: web t 8, flange 10 x 150), Pick parts; orbit, orthographic stills, the model saved (c02/c02_case.ss), then the
 *    haunch's dialog again: Haunch length 1500, Modify, orbit again. /sdcard/c02_rec.mp4.
 *  - c09Recording: R12's monitor post (BI150 post on the BI220 rafter). End plate (144) with R12's settings (t 12, 1 = -55.358, 2 = -60.814,
 *    gap 0; Haunch tab: Top / Bottom plate 8 x 60 x 60, chamfers 15 line, Top and bottom created; Bolts M16, 4 rows, 6 = 30.025,
 *    s = 70 70 70, 5 = 90, 3 = 35, Tekla length, direction 2), Pick parts (rafter, post); orbit, stills, saved (c09/c09_case.ss), then
 *    Haunch tab Top plate b 40, Modify, orbit again. /sdcard/c09_rec.mp4.
 */
@RunWith(AndroidJUnit4::class)
class C02C09RecordingTest {
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
            act.runOnUiThread { mode().catPick(mode().catK ?: type) }; pause(400); tapAt(screen(a)); pause(700); tapAt(screen(b)); pause(1300) }
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

    // R12 rafter R3023: top of its section on the web plane at x (horizontal), slope 0.227356, half depth 110 / cos
    private fun rafterTop(x: Double, x1: Double, z1: Double) = z1 + (x1 - x) * 0.227356 + 110.0 * sqrt(1 + 0.227356 * 0.227356)

    @Test fun c02Recording() {
        dir = "c02"; start()
        val j = Vec3(52300.0, 22355.0, 7450.0); look(j, Vec3(0.9, -1.2, 0.7), 6000.0); pause(800)
        val rec = ua.executeShellCommand("screenrecord --bit-rate 6000000 --time-limit 175 /sdcard/c02_rec.mp4")
        try {
            pause(1500)
            member("COLUMN", """{"prof":"BI320-8-150-10","p1":[52838,22355,230],"p2":[52838,22355,7547.745],"rot":"Below","plane":"Middle","depth":"Middle"}"""); pause(900)
            member("RAFTER", """{"prof":"BI220-8-150-10","p1":[52682.387,22355,7433.94],"p2":[46290.5,22355,8887.194],"rot":"Top","plane":"Middle","depth":"Middle"}"""); pause(1200)
            shot("members")
            val cam = { look(Vec3(51400.0, 22355.0, 7300.0), Vec3(0.35, -1.0, 0.45), 6500.0) }
            val pairs = listOf(52998.0 to 50300.0, 52998.0 to 49800.0, 52998.0 to 50800.0, 52998.0 to 49300.0).mapIndexed { i, (cx, rx) ->
                Vec3(cx, 22355.0 - 40.0 * (i % 2), 6300.0 - 300.0 * i) to Vec3(rx, 22355.0, rafterTop(rx, 52682.387, 7433.94)) }
            // End plate (144), R12's eave settings
            openCatalog("144")
            tab("Picture"); field("tp", "20"); field("ext", "-10"); field("extb", "-104.385"); field("gap", "0"); shot("dialog144_picture")
            tab("Bolts"); pick("bolt", "M20"); field("rows", "4"); field("e", "50.045"); field("pitches", "70 70 80"); field("gauge", "70"); field("side", "40")
            pick("blrule", "Tekla (grip + nut + washer + 5, up to 5 mm)"); pick("bdir", "Bolting direction 2"); shot("dialog144_bolts")
            pickParts("T144", cam, pairs)
            assertTrue("144 bolt length rule ${q("T144", "blrule")}", q("T144", "blrule") == "tekla")
            // Eave haunch (C02h)
            openCatalog("teh")
            tab("Picture"); field("hdep", "82.91"); field("hlen", "2000"); shot("dialogEH_picture")
            tab("Parts"); field("tw", "8"); field("tf", "10"); field("bf", "150"); shot("dialogEH_parts")
            pickParts("Teh", cam, pairs)
            val c = centre(bodyNamed("Web"))
            orbit(c, 3200.0, "len2000")
            orthoShot(c, Vec3(0.0, -1.0, 0.0), 2600.0, "ortho_front")     // along the frame line: the haunch's profile, length and depth
            orthoShot(Vec3(52668.0, 22355.0, 7390.0), Vec3(-1.0, 0.0, 0.0), 900.0, "ortho_end")   // onto the end plate from the column side
            orthoShot(c, Vec3(0.0, 0.0, -1.0), 2600.0, "ortho_under")
            look(c, Vec3(0.9, -1.2, 0.7), 3200.0); pause(600)
            save("c02_case.ss")
            // the haunch's dialog again: Haunch length 1500, Modify
            act.runOnUiThread { act.testViewport.selection.setSelected(listOf(bodyNamed("Web"))) }; pause(800)
            press("Components")
            waitFor("the haunch's dialog") { compose.onAllNodes(hasText("Parts") and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }; pause(800)
            tab("Picture"); field("hlen", "1500"); pause(600); shot("dialogEH_len1500")
            press("Modify"); pause(800)
            waitFor("Haunch length 1500") { q("Teh", "hlen")?.toDoubleOrNull() == 1500.0 }
            compose.onAllNodes(hasText("Cancel") and hasClickAction()).fetchSemanticsNodes().takeIf { it.isNotEmpty() }?.let { compose.onAllNodes(hasText("Cancel") and hasClickAction())[0].performClick() }
            act.runOnUiThread { act.testViewport.selection.setSelected(emptyList()) }; pause(800)
            val c2 = centre(bodyNamed("Web")); orbit(c2, 3000.0, "len1500")
            orthoShot(c2, Vec3(0.0, -1.0, 0.0), 2600.0, "ortho_front_len1500")
            pause(800)
        } finally { sh("pkill -INT screenrecord"); Thread.sleep(3000); rec.close() }
    }

    @Test fun c09Recording() {
        dir = "c09"; start()
        val j = Vec3(34040.0, 27925.0, 8850.0); look(j, Vec3(0.9, -1.2, 0.7), 3000.0); pause(800)
        val rec = ua.executeShellCommand("screenrecord --bit-rate 6000000 --time-limit 175 /sdcard/c09_rec.mp4")
        try {
            pause(1500)
            member("RAFTER", """{"prof":"BI220-8-150-10","p1":[39537.387,27925,7422.572],"p2":[33095.5,27925,8887.194],"rot":"Top","plane":"Middle","depth":"Middle"}"""); pause(900)
            member("COLUMN", """{"prof":"BI150-6-120-8","p1":[34039.28,27925,8782.496],"p2":[34039.28,27925,9579.802],"rot":"Below","plane":"Middle","depth":"Middle"}"""); pause(1200)
            shot("members")
            openCatalog("144")
            tab("Picture"); field("tp", "12"); field("ext", "-55.358"); field("extb", "-60.814"); field("gap", "0"); shot("dialog144_picture")
            tab("Haunch"); field("hpt1", "8"); field("hpb1", "60"); field("hph1", "60"); field("hpt2", "8"); field("hpb2", "60"); field("hph2", "60")
            field("hc1", "15"); field("hc2", "15"); field("hc3", "15"); field("hc4", "15"); pick("hct", "Line chamfer"); pick("hpc", "Top and bottom haunch plates are created"); shot("dialog144_haunch")
            tab("Bolts"); pick("bolt", "M16"); field("rows", "4"); field("e", "30.025"); field("pitches", "70 70 70"); field("gauge", "90"); field("side", "35")
            pick("blrule", "Tekla (grip + nut + washer + 5, up to 5 mm)"); pick("bdir", "Bolting direction 2"); shot("dialog144_bolts")
            val cam = { look(Vec3(35000.0, 27925.0, 8900.0), Vec3(0.35, -1.0, 0.5), 3800.0) }
            val pairs = listOf(36500.0 to 9300.0, 37000.0 to 9450.0, 36000.0 to 9200.0, 37500.0 to 9400.0).map { (rx, pz) ->
                Vec3(rx, 27925.0, rafterTop(rx, 39537.387, 7422.572)) to Vec3(34114.28, 27925.0, pz) }
            pickParts("T144", cam, pairs)
            assertTrue("144 haunch plates ${q("T144", "hpc")}", q("T144", "hpc") == "both")
            val c = centre(bodyNamed("END PLATE"))
            orbit(c, 1100.0, "b60")
            orthoShot(c, Vec3(0.0, -1.0, 0.0), 700.0, "ortho_front")     // along the frame line: the haunch plates' outline
            orthoShot(c, Vec3(1.0, 0.0, 0.0), 700.0, "ortho_end")
            orthoShot(c, Vec3(0.0, 0.0, 1.0), 700.0, "ortho_top")
            look(c, Vec3(0.9, -1.2, 0.7), 1100.0); pause(600)
            save("c09_case.ss")
            act.runOnUiThread { act.testViewport.selection.setSelected(listOf(bodyNamed("END PLATE"))) }; pause(800)
            press("Components")
            waitFor("the 144's dialog") { compose.onAllNodes(hasText("Haunch") and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }; pause(800)
            tab("Haunch"); field("hpb1", "40"); pause(600); shot("dialog144_b40")
            press("Modify"); pause(800)
            waitFor("Top plate b 40") { q("T144", "hpb1")?.toDoubleOrNull() == 40.0 }
            compose.onAllNodes(hasText("Cancel") and hasClickAction()).fetchSemanticsNodes().takeIf { it.isNotEmpty() }?.let { compose.onAllNodes(hasText("Cancel") and hasClickAction())[0].performClick() }
            act.runOnUiThread { act.testViewport.selection.setSelected(emptyList()) }; pause(800)
            orbit(c, 1100.0, "b40")
            orthoShot(c, Vec3(0.0, -1.0, 0.0), 700.0, "ortho_front_b40")
            pause(800)
        } finally { sh("pkill -INT screenrecord"); Thread.sleep(3000); rec.close() }
    }
}
