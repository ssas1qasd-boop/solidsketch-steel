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
 * Cleat location → v69 (the result before v70), Modify, and the same orbit. Runs in its own pass after EmulatorFlowTest (its own screenrecord).
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
    /** A button: panel buttons sit in a sideways-scrolling row (scroll them into view first); dialog buttons do not scroll. */
    private fun press(text: String) { val n = compose.onAllNodes(hasText(text) and hasClickAction())[0]; runCatching { n.performScrollTo() }; n.performClick(); compose.waitForIdle() }
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

    private val VIEWS = listOf("3d" to Vec3(1.0, -1.2, 0.9), "front" to Vec3(0.02, -1.0, 0.12), "end" to Vec3(1.0, 0.02, 0.12), "top" to Vec3(0.05, -0.08, 1.0), "3d_back" to Vec3(-1.0, 1.2, 0.9))

    /**
     * The "R11 (Tekla)" row of docs/steel/parity/C04.png: R11 itself opened in the app (R10's Tekla meshes with the Tekla-proven C04
     * cleats, stiffeners and bolts, docs/steel/PARITY.md), the same views at the same joint as c04Recording (cleat 3185, rafter R3022).
     */
    @Test fun c04R11Views() {
        waitFor("the steel data", 60_000) { act.testSteel != null }
        val f = java.io.File(act.cacheDir, "r11.ss")
        InstrumentationRegistry.getInstrumentation().context.assets.open("Frame_28x53m_R11.ss").use { i -> f.outputStream().use { i.copyTo(it) } }
        act.runOnUiThread { act.testOpen(f) }
        waitFor("R11 opened", 240_000) { act.testToast?.startsWith("Opened") == true && !act.testBusy }
        pause(3000)
        val want = Vec3(6480.0, 22355.0, 8990.0)
        val cl = act.testDoc.bodyIds().filter { (mode().stOf(it)?.get("name") as? JsonPrimitive)?.contentOrNull == "Cleat Angle" }.minBy { centre(it).let { c -> (c.x - want.x) * (c.x - want.x) + (c.y - want.y) * (c.y - want.y) + (c.z - want.z) * (c.z - want.z) } }
        val c = centre(cl)
        for ((name, dir) in VIEWS) { look(c, dir, 1500.0); pause(2500); shot("r11_$name") }
    }
    private fun centre(id: BodyId): Vec3 {
        val m = act.testDoc.bodyMesh(id)!!; val lo = doubleArrayOf(1e18, 1e18, 1e18); val hi = doubleArrayOf(-1e18, -1e18, -1e18)
        for (i in 0 until m.vertexCount) for (k in 0..2) { val v = m.positions[i * 3 + k].toDouble(); lo[k] = minOf(lo[k], v); hi[k] = maxOf(hi[k], v) }
        return Vec3((lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2, (lo[2] + hi[2]) / 2)
    }

    /** A point on a member's solid seen from above: on its axis, moved down to the middle of its depth (RAFTER set: depth Behind). */
    private fun onMember(id: BodyId, f: Double): Vec3 {
        val st = mode().stOf(id)!!; fun v(k: String) = (st[k] as kotlinx.serialization.json.JsonArray).map { (it as JsonPrimitive).content.toDouble() }
        val p1 = v("p1"); val p2 = v("p2"); val c = Vec3(p1[0] + (p2[0] - p1[0]) * f, p1[1] + (p2[1] - p1[1]) * f, p1[2] + (p2[2] - p1[2]) * f)
        val m = act.testDoc.bodyMesh(id)!!; var zlo = 1e18; var zhi = -1e18
        for (i in 0 until m.vertexCount) { val x = m.positions[i * 3].toDouble(); val y = m.positions[i * 3 + 1].toDouble(); if (kotlin.math.abs(x - c.x) < 60 && kotlin.math.abs(y - c.y) < 60) { val z = m.positions[i * 3 + 2].toDouble(); zlo = minOf(zlo, z); zhi = maxOf(zhi, z) } }
        return if (zlo < zhi) Vec3(c.x, c.y, zhi - 5) else c
    }
    private fun named(n: String) = act.testDoc.bodyIds().filter { (mode().stOf(it)?.get("name") as? JsonPrimitive)?.contentOrNull == n }
    private fun comp(search: String, title: String) {
        press("Components")
        waitFor("the catalog") { compose.onAllNodes(hasText("Applications & components")).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement(search); pause(1200)
        val all = compose.onAllNodes(hasText("Properties") and hasClickAction()); val ns = all.fetchSemanticsNodes()
        all[ns.indices.filter { ns[it].boundsInRoot.width > 0f }.minBy { ns[it].boundsInRoot.top }].performClick()
        waitFor("the $title dialog") { compose.onAllNodes(hasText("Pick parts") and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }; pause(1000)
    }
    private fun orthoShot(t: Vec3, dir: Vec3, d: Double, name: String) {
        act.runOnUiThread { act.testViewport.camera.orthographic = true }; look(t, dir, d); pause(1800); shot(name)
        val px = act.testViewport.worldToScreen(t); val loc = IntArray(2); act.testViewportView?.getLocationOnScreen(loc)
        storage.openOutputFile("c04/%02d_%s.txt".format(shotNo, name)).use { it.write("${loc[0]} ${loc[1]} ${px?.first} ${px?.second} ${t.x} ${t.y} ${t.z} ${dir.x} ${dir.y} ${dir.z} $d\n".toByteArray()) }
        act.runOnUiThread { act.testViewport.camera.orthographic = false }
    }

    /**
     * C04 in the owner's workflow (2026-11-26), through the app's own dialogs: three rafters from the default RAFTER property set
     * (IPE360, depth Behind, 12.8°), purlins by "Generation of purlins" (its dialog: Profile CC200-2-20-60, Pick parts: rafter A, B,
     * then B, C), then "Purlin cleat L" through its dialog on rafter B and the two purlins that butt over it (the generator's plate
     * cleats there go: one cleat per joint, as in Tekla). Orbit, orthographic stills (end view along the purlins, front, top), the
     * model as built is saved (c04/c04_workflow.ss: rule B and the dimension on the end view are measured on it), then Cleat location
     * -> v69, Modify, orbit again. Screen recording /sdcard/c04_rec.mp4 (ci-out/C04_recording.mp4).
     */
    @Test fun c04Recording() {
        waitFor("the steel data", 60_000) { act.testSteel != null }
        compose.onAllNodes(hasText("Steel") and hasClickAction())[0].performClick()
        waitFor("the Steel panel") { compose.onAllNodes(hasText("Grid") and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
        waitFor("the component catalog data") { mode().comps != null }
        val mid = Vec3(3234.0, 5570.0, 8000.0); look(mid, Vec3(1.0, -1.3, 1.1), 16000.0); pause(800)
        val rec = ua.executeShellCommand("screenrecord --bit-rate 6000000 --time-limit 175 /sdcard/c04_rec.mp4")
        try {
            pause(1500)
            val raf = listOf(0.0, 5570.0, 11140.0).map { y -> member("RAFTER", """{"p1":[0,$y,7500],"p2":[6468,$y,8970]}""").also { pause(700) } }
            shot("rafters")
            // Generation of purlins: its dialog, Profile CC200-2-20-60, Pick parts (rafter A, B, then B, C)
            comp("Generation of purlins", "Generation of purlins")
            compose.onAllNodes(hasText("Parameters") and hasClickAction())[0].performClick(); pause(900)
            compose.onAllNodes(hasSetTextAction() and hasText("IPE160"))[0].performTextReplacement("CC200-2-20-60"); pause(1200); shot("purlins_dialog")
            press("Pick parts"); pause(800); look(mid, Vec3(0.15, -0.25, 1.0), 16000.0); pause(800)
            // each run: tap the two rafters between purlins; if no purlins came (a tap missed: a toast or a purlin under it), try another point
            for ((a, b) in listOf(raf[0] to raf[1], raf[1] to raf[2])) {
                val before = named("PURLIN").size
                for (f in listOf(0.12, 0.33, 0.56, 0.79)) {
                    if (mode().catK == null) { comp("Generation of purlins", "Generation of purlins"); press("Pick parts"); pause(800); look(mid, Vec3(0.15, -0.25, 1.0), 16000.0); pause(800) }
                    act.runOnUiThread { mode().catPick(mode().catK ?: "T50p") }; pause(300)
                    tapAt(screen(onMember(a, f))); pause(700); tapAt(screen(onMember(b, f))); pause(1500)
                    if (named("PURLIN").size > before) break
                }
                shot("purlins_${named("PURLIN").size}")
            }
            act.runOnUiThread { mode().catDone() }; pause(800)
            val purs = named("PURLIN"); assertTrue("purlins made: ${purs.size}", purs.size >= 8); shot("purlins")
            // Purlin cleat L: its dialog, Pick parts (rafter B, the purlin from A, the purlin to C)
            fun pv(id: BodyId) = (mode().stOf(id)!!["p1"] as kotlinx.serialization.json.JsonArray).map { (it as JsonPrimitive).content.toDouble() } to (mode().stOf(id)!!["p2"] as kotlinx.serialization.json.JsonArray).map { (it as JsonPrimitive).content.toDouble() }
            val at = purs.map { it to pv(it) }.sortedBy { it.second.first[0] }
            val pAB = at.first { (_, p) -> maxOf(p.first[1], p.second[1]) in 5400.0..5700.0 }.first
            val xAB = pv(pAB).first[0]; val pBC = at.first { (id, p) -> id != pAB && kotlin.math.abs(p.first[0] - xAB) < 1 && minOf(p.first[1], p.second[1]) in 5400.0..5700.0 }.first
            comp("Purlin cleat", "Purlin cleat L")
            compose.onAllNodes(hasText("Parts") and hasClickAction())[0].performClick(); pause(1200); shot("cleat_dialog")
            press("Pick parts"); pause(800)
            val j = Vec3(xAB, 5570.0, pv(pAB).first[2]); look(j, Vec3(0.35, -0.55, 1.0), 2600.0); pause(1000)
            val n0 = mode().comps!!.conns().size; val tpc0 = mode().comps!!.conns().count { it.type == "Tpc" }
            fun purAt(id: BodyId, y: Double): Vec3 { val (p1, p2) = pv(id); val f = (y - p1[1]) / (p2[1] - p1[1]); val c = Vec3(p1[0] + (p2[0] - p1[0]) * f, y, p1[2] + (p2[2] - p1[2]) * f); return Vec3(c.x, c.y, c.z + 90) }
            tapAt(screen(onMember(raf[1], (xAB - 60) / 6468.0 * 0 + ((xAB + 260) / 6468.0)))); pause(700)
            tapAt(screen(purAt(pAB, 5570.0 - 420))); pause(700)
            tapAt(screen(purAt(pBC, 5570.0 + 420))); pause(700)
            waitFor("the purlin cleat") { mode().comps!!.conns().any { it.type == "Tcl" } }
            act.runOnUiThread { mode().catDone() }; pause(800)
            val cs = mode().comps!!.conns(); val left = cs.count { it.type == "Tpc" && it.parts[0] == raf[1].value && (pAB.value in it.parts || pBC.value in it.parts) }
            assertTrue("one cleat per joint: plate cleats left at the C04 joint $left (Tpc ${cs.count { it.type == "Tpc" }}, was $tpc0)", left == 0)
            assertTrue("new cleat loc ${locOf()}", locOf() == "back")
            val c = cleatCentre()
            orbit(c, 1500.0, "back")
            // orthographic stills: end view along the purlins (the purlin underside on the rafter top), front (along the rafter), top
            orthoShot(c, Vec3(0.0, -1.0, 0.0), 1100.0, "ortho_end")
            orthoShot(c, Vec3(1.0, 0.0, 0.0), 1100.0, "ortho_front")
            orthoShot(c, Vec3(0.0, 0.0, 1.0), 1100.0, "ortho_top")
            look(c, Vec3(1.0, -1.2, 0.9), 1500.0); pause(600)
            run { val f = java.io.File(act.cacheDir, "c04_workflow.ss"); act.runOnUiThread { act.testSave(f) }; waitFor("saved", 60_000) { f.exists() && f.length() > 0 && !act.testBusy }; pause(500)
                storage.openOutputFile("c04/c04_workflow.ss").use { o -> f.inputStream().use { it.copyTo(o) } } }
            // the dialog of this cleat (its part selected), Parts -> Cleat location -> v69, Modify
            val tclId = mode().comps!!.conns().last { it.type == "Tcl" }.id   // the purlin cleat L's own cleat (the generator's plate cleats are named CLEAT too)
            val cl = act.testDoc.bodyIds().first { (mode().stOf(it)?.get("conn") as? JsonPrimitive)?.contentOrNull == tclId && (mode().stOf(it)?.get("name") as? JsonPrimitive)?.contentOrNull == "CLEAT" }
            act.runOnUiThread { act.testViewport.selection.setSelected(listOf(cl)) }; pause(800)
            press("Components")
            waitFor("the cleat's dialog") { compose.onAllNodes(hasText("Parts") and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
            pause(800); compose.onAllNodes(hasText("Parts") and hasClickAction())[0].performClick(); pause(1000)
            compose.onAllNodesWithTag("tk_loc")[0].let { runCatching { it.performScrollTo() }; it.performClick() }; pause(900)
            compose.onAllNodes(hasText("v69 (before v70)") and hasClickAction()).let { a -> a[a.fetchSemanticsNodes().size - 1] }.performClick(); pause(900); shot("dialog_v69")
            press("Modify"); pause(800)
            waitFor("Cleat location v69") { locOf() == "v69" }
            compose.onAllNodes(hasText("Cancel") and hasClickAction()).fetchSemanticsNodes().takeIf { it.isNotEmpty() }?.let { compose.onAllNodes(hasText("Cancel") and hasClickAction())[0].performClick() }
            act.runOnUiThread { act.testViewport.selection.setSelected(emptyList()) }; pause(800)
            orbit(cleatCentre(), 1500.0, "v69")
            pause(1000)
        } finally {
            sh("pkill -INT screenrecord"); Thread.sleep(3000); rec.close()
        }
    }
}
