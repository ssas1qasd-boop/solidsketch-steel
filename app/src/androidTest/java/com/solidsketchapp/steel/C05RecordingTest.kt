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
 * C05 Tube gusset (20), the tube end (docs/steel/PARITY.md "C05"), the owner's video check: R12's node on rafter R3019 (BI220) placed
 * one by one: HZ6014 (SHS100*3) and strut ST5008, each with Tube gusset (20) through its dialog with R12's settings (Picture: plate L 285
 * / 315, 150 inside; Gusset tab: one gusset at the node, Tube end at the brace end, Connection plate against the gusset, the strut's
 * plate in the rafter plane; Bolts: M16, e 30 / 40, p 50 / 60, gauge 50 / 60, Tekla bolt length), Pick parts (rafter, then brace); orbit;
 * orthographic stills in landscape; saved (c05/c05_case.ss). Then the HZ brace's dialog again: e 30 -> 40, Modify, orbit, stills, saved
 * (c05_e40.ss). /sdcard/c05_rec.mp4.
 */
@RunWith(AndroidJUnit4::class)
class C05RecordingTest {
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
    private val node = Vec3(42760.0, 22620.0, 8110.0)
    private fun stills(tag: String) {   // plan from above the rafter (the gusset, the plates, the end plates, the bolts) and along the strut
        val c = Vec3(node.x, node.y - 130.0, node.z)   // landscape: the panel covers the lower third, so the gusset edge on the rafter sits mid-screen
        landscape(true); orthoShot(c, Vec3(0.0, -0.02, 1.0), 1200.0, "ortho_top$tag"); orthoShot(c, Vec3(0.02, -1.0, 0.05), 1200.0, "ortho_strut$tag"); landscape(false)
    }
    private fun tg(L: String, e: String, p: String, g: String, plane: String?) {
        openCatalog("Tongue plate")
        tab("Picture"); field("L", L); field("ins", "150"); shot("dialog20_picture")
        tab("Gusset"); pick("node", "Yes"); if (plane != null) pick("plane", plane); pick("tend", "At the brace end (as Tekla)"); pick("tpos", "Against the gusset (as Tekla)"); pick("side", "Side 1"); shot("dialog20_gusset")
        tab("Bolts"); pick("bolt", "M16"); field("e", e); field("p", p); field("g", g); pick("blrule", "Tekla (grip + nut + washer + 5, up to 5 mm)"); shot("dialog20_bolts")
    }

    @Test fun c05Recording() {
        dir = "c05"; start()
        look(node, Vec3(0.6, -1.0, 0.9), 3000.0); pause(800)
        val rec = ua.executeShellCommand("screenrecord --bit-rate 6000000 --time-limit 175 /sdcard/c05_rec.mp4")
        try {
            pause(1500)
            member("RAFTER", """{"prof":"BI220-8-150-10","p1":[39848.613,22355,7422.572],"p2":[46290.5,22355,8887.194],"rot":"Top","plane":"Middle","depth":"Middle"}"""); pause(900)
            member("BRACING", """{"name":"HZ Bracing","prof":"SHS100*3","p1":[41609.01,24871.307,7833.07],"p2":[42819.298,22586.496,8108.24],"rot":"Top","rAng":191.298,"plane":"Middle","depth":"Middle"}"""); pause(900)
            member("BRACING", """{"name":"Strut","prof":"SHS100*3","p1":[43035.665,22664.389,8147.745],"p2":[43035.665,27616.278,8147.745],"rot":"Top","rAng":167.191,"plane":"Middle","depth":"Middle"}"""); pause(1200)
            shot("members")
            // picks from above: the rafter's top flange, then the brace's top face 0.6 m from its end
            val cam = { look(Vec3(42670.0, 22850.0, 8100.0), Vec3(0.0, -0.02, 1.0), 3000.0) }
            tg("285", "30", "50", "50", null)
            pickParts("Ttg", cam, listOf(42300.0, 42150.0, 42450.0, 42000.0).map { rx -> Vec3(rx, 22355.0, 7422.572 + (rx - 39848.613) * 0.227362 + 112.0) to Vec3(42516.0, 23157.0, 8040.0 + 50.0) })
            tg("315", "40", "60", "60", "Main part plane")
            pickParts("Ttg", cam, listOf(42300.0, 42150.0, 42450.0, 42000.0).map { rx -> Vec3(rx, 22355.0, 7422.572 + (rx - 39848.613) * 0.227362 + 112.0) to Vec3(43035.0, 23300.0 + (rx - 42300.0), 8147.745 + 52.0) })
            waitFor("two Ttg") { mode().comps!!.conns().count { it.type == "Ttg" } == 2 }
            assertTrue("tube end ${q("Ttg", "tend")}", q("Ttg", "tend") == "end")
            orbit(node, 1600.0, "r12")
            stills("")
            save("c05_case.ss")
            // the HZ brace's dialog again: Bolts tab e 30 -> 40, Modify
            act.runOnUiThread { act.testViewport.selection.setSelected(listOf(act.testDoc.bodyIds().first { (mode().stOf(it)?.get("name") as? JsonPrimitive)?.contentOrNull == "TONGUE PLATE" })) }; pause(800)
            press("Components")
            waitFor("the Ttg's dialog") { compose.onAllNodes(hasText("Bolts") and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }; pause(800)
            tab("Bolts"); field("e", "40"); pause(600); shot("dialog20_e40")
            press("Modify"); pause(800)
            waitFor("e 40") { mode().comps!!.conns().count { it.type == "Ttg" && (it.q["e"] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull() == 40.0 } == 2 }
            closeDialog()
            orbit(node, 1600.0, "e40")
            stills("_e40")
            save("c05_e40.ss")
            pause(800)
        } finally { runCatching { ua.setRotation(android.app.UiAutomation.ROTATION_UNFREEZE) }; sh("pkill -INT screenrecord"); Thread.sleep(3000); rec.close() }
    }
}
