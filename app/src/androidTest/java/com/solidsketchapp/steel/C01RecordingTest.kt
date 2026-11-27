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
 * C01 / C16 Base plate 1042 as Tekla (docs/steel/PARITY.md "C01 / C16"), the owner's video check: R12's frame column C2020
 * (BI320-8-150-10), Base plate (1042) from the catalog with R12's settings through its dialog: plate 20 (ext 20, side 25), grout 30 with
 * slopes (margin 30) under the plate, cast plate 4 under the grout (6 = 30), 4 anchors M20 at 100 x 100, Base plate with = Anchor rods:
 * hooked ROD20 L600 (a 40, b 120) outward along the column depth, washer + nut PD40*10; Pick parts (the column); orbit; orthographic
 * elevations in landscape; saved (c01/c01_case.ss). Then the dialog again: Hook direction -> along Y, Modify, orbit, stills, saved
 * (c01_hooky.ss). /sdcard/c01_rec.mp4.
 */
@RunWith(AndroidJUnit4::class)
class C01RecordingTest {
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
    private fun closeDialog() { compose.onAllNodes(hasText("Cancel") and hasClickAction()).fetchSemanticsNodes().takeIf { it.isNotEmpty() }?.let { compose.onAllNodes(hasText("Cancel") and hasClickAction())[0].performClick() }
        act.runOnUiThread { act.testViewport.selection.setSelected(emptyList()) }; pause(800) }
    private val base = Vec3(52838.0, 16785.0, 0.0)
    private fun stills(tag: String) {
        val c = Vec3(base.x, base.y, 30.0)
        landscape(true); orthoShot(c, Vec3(0.02, -1.0, 0.05), 900.0, "ortho_front$tag"); orthoShot(c, Vec3(1.0, 0.02, 0.05), 900.0, "ortho_side$tag"); orthoShot(Vec3(base.x, base.y, 250.0), Vec3(0.02, -0.03, 1.0), 700.0, "ortho_top$tag"); landscape(false)
    }

    @Test fun c01Recording() {
        dir = "c01"; start()
        look(Vec3(base.x, base.y, 600.0), Vec3(0.7, -1.0, 0.5), 3500.0); pause(800)
        val rec = ua.executeShellCommand("screenrecord --bit-rate 6000000 --time-limit 175 /sdcard/c01_rec.mp4")
        try {
            pause(1500)
            member("COLUMN", """{"prof":"BI320-8-150-10","p1":[52838,16785,230],"p2":[52838,16785,7547.745],"rot":"Below","rAng":0,"plane":"Middle","depth":"Middle"}"""); pause(1200)
            shot("members")
            openCatalog("1042")
            tab("Picture"); field("ext", "20"); field("side", "25"); field("t", "20"); shot("dialog1042_picture")
            tab("Parameters"); field("gt", "30"); field("gm", "30"); shot("dialog1042_parameters")
            tab("Bolts"); pick("anchors", "4"); field("gx", "100"); field("gy", "100"); pick("bolt", "M20"); shot("dialog1042_bolts")
            tab("Anchor rods"); field("cast", "4"); pick("awith", "Anchor rods (parts: rod, nut, washer, as Tekla)")
            field("aL", "600"); field("aup", "50"); field("ahr", "40"); field("ahw", "120"); pick("ahdir", "Outward along the plate X (column depth)"); pick("anut", "1"); pick("awash", "Created (PD40*10 style ring)")
            pick("gorder", "Tekla: grout under the plate, cast plate under the grout"); field("castd", "30"); pick("gslope", "With slopes (the plate size at the top, the margin at the bottom)"); shot("dialog1042_anchor_rods")
            press("Pick parts"); pause(800); look(Vec3(base.x, base.y, 1500.0), Vec3(0.6, -1.0, 0.3), 5000.0); pause(1200)
            for (z in listOf(1500.0, 2500.0, 1000.0)) { if (mode().comps!!.conns().any { it.type == "T1042" }) break
                act.runOnUiThread { mode().catPick(mode().catK ?: "T1042") }; pause(400); shot("pick_T1042"); tapAt(screen(Vec3(base.x, base.y, z))); pause(1500) }
            waitFor("the T1042") { mode().comps!!.conns().count { it.type == "T1042" } == 1 }
            act.runOnUiThread { mode().catDone() }; pause(800)
            assertTrue("anchors ${q("T1042", "awith")}", q("T1042", "awith") == "rods")
            waitFor("the anchor rods") { act.testDoc.bodyIds().count { (mode().stOf(it)?.get("name") as? JsonPrimitive)?.contentOrNull == "ANCHOR ROD" } == 4 }
            orbit(Vec3(base.x, base.y, 0.0), 1400.0, "r12")
            stills("")
            save("c01_case.ss")
            // the dialog again: Hook direction -> along Y, Modify
            act.runOnUiThread { act.testViewport.selection.setSelected(listOf(bodyNamed("BASE PLATE"))) }; pause(800)
            press("Components")
            waitFor("the T1042's dialog") { compose.onAllNodes(hasText("Anchor rods") and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }; pause(800)
            tab("Anchor rods"); pick("ahdir", "Outward along the plate Y"); pause(600); shot("dialog1042_hooky")
            press("Modify"); pause(800)
            waitFor("hook y") { mode().comps!!.conns().count { it.type == "T1042" && (it.q["ahdir"] as? JsonPrimitive)?.contentOrNull == "y" } == 1 }
            closeDialog()
            orbit(Vec3(base.x, base.y, 0.0), 1400.0, "hooky")
            stills("_hooky")
            save("c01_hooky.ss")
            pause(800)
        } finally { runCatching { ua.setRotation(android.app.UiAutomation.ROTATION_UNFREEZE) }; sh("pkill -INT screenrecord"); Thread.sleep(3000); rec.close() }
    }
}
