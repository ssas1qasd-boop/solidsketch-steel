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
 * C18 Stairs as Tekla (docs/steel/PARITY.md "C18"), the owner's video check: R12's lower flight (SR9001 / SR9002: sloped, foot and landing
 * pieces UPN240) and landing posts STR9001 / STR9002; Stair treads from the catalog with R12's settings through its dialog (Treads =
 * Tekla: 12 bent grating treads, rise 156.667, first tread 145.863 along the run, 47.43 above the stringer line), Pick parts (the two
 * sloped stringers); Stair end plate on the left landing piece through the post STR9001 (320 x 150 x 12, 2 x 4 M16); orbit;
 * orthographic side elevation and a tread section in landscape; saved (c18/c18_case.ss). Then the treads' dialog again: 12 -> 10
 * treads, Modify, orbit, stills, saved (c18_ntr10.ss). /sdcard/c18_rec.mp4.
 */
@RunWith(AndroidJUnit4::class)
class C18RecordingTest {
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
    private fun nearMember(p: Vec3): BodyId? = act.testDoc.bodyIds().filter { (mode().stOf(it)?.get("kind") as? JsonPrimitive)?.contentOrNull == "member" }.minWithOrNull(compareBy({ boxDist(it, p) }, { boxVol(it) }))
    private fun boxOf(id: BodyId): Pair<DoubleArray, DoubleArray> { val m = act.testDoc.bodyMesh(id)!!; val lo = DoubleArray(3) { 1e18 }; val hi = DoubleArray(3) { -1e18 }
        for (i in 0 until m.vertexCount) for (k in 0..2) { val v = m.positions[i * 3 + k].toDouble(); lo[k] = minOf(lo[k], v); hi[k] = maxOf(hi[k], v) }; return lo to hi }
    private fun boxVol(id: BodyId): Double { val (lo, hi) = boxOf(id); return (hi[0] - lo[0]) * (hi[1] - lo[1]) * (hi[2] - lo[2]) }   // a point inside two boxes: the smaller part (a column under a rafter)
    private fun boxDist(id: BodyId, p: Vec3): Double = run {
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
    private val mid = Vec3(1108.0, 10160.0, 1030.0)
    private fun stills(tag: String) {
        landscape(true)
        orthoShot(mid, Vec3(-1.0, 0.02, 0.05), 2600.0, "ortho_side$tag")
        orthoShot(Vec3(1108.0, 8435.0, 160.0), Vec3(0.02, -1.0, 0.05), 650.0, "ortho_tread$tag")
        landscape(false)
    }
    private fun treads() = act.testDoc.bodyIds().count { (mode().stOf(it)?.get("name") as? JsonPrimitive)?.contentOrNull == "TREAD" }

    @Test fun c18Recording() {
        dir = "c18"; start()
        look(mid, Vec3(-1.0, -0.6, 0.7), 5200.0); pause(800)
        val rec = ua.executeShellCommand("screenrecord --bit-rate 6000000 --time-limit 175 /sdcard/c18_rec.mp4")
        try {
            pause(1500)
            fun str(o: String) = member("BEAM", """{"name":"STRINGER","prof":"UPN240",$o,"plane":"Middle","depth":"Middle"}""")
            str(""""p1":[565.5,12035.972,1972.906],"p2":[565.5,8289.137,78.835],"rot":"Top","rAng":180"""); str(""""p1":[1650.5,8289.137,78.835],"p2":[1650.5,12035.972,1972.906],"rot":"Top","rAng":180""")
            str(""""p1":[565.5,8355,185.929],"p2":[565.5,8355,12],"rot":"Top","rAng":90"""); str(""""p1":[565.5,12142,1960],"p2":[565.5,11981.834,1960],"rot":"Top","rAng":180""")
            str(""""p1":[1650.5,8355,12],"p2":[1650.5,8355,185.929],"rot":"Top","rAng":270"""); str(""""p1":[1650.5,11981.834,1960],"p2":[1650.5,12142,1960],"rot":"Top","rAng":180""")
            member("COLUMN", """{"prof":"BI162-6-150-6","p1":[598,12235.01,30],"p2":[598,12235.01,2080],"rot":"Back","rAng":0,"plane":"Middle","depth":"Middle"}""")
            member("COLUMN", """{"prof":"BI162-6-150-6","p1":[1619,12235.01,30],"p2":[1619,12235.01,2080],"rot":"Back","rAng":0,"plane":"Middle","depth":"Middle"}"""); pause(1200)
            shot("members")
            // Stair treads (Tekla mode) on the two sloped stringers
            openCatalog("Stair treads")
            tab("Treads"); pick("tmode", "Tekla (R12): bent grating treads on bolted brackets"); field("ntr", "12"); field("rise", "156.667"); field("s0", "145.863"); field("dz", "47.43"); shot("dialog_treads")
            tab("Bolts"); shot("dialog_tread_bolts")
            sh("input keyevent 111"); pause(600); press("Pick parts"); pause(800); look(mid, Vec3(-0.05, -0.08, 1.0), 4600.0); pause(1200)
            for (dy in listOf(0.0, 400.0, -400.0)) { if (mode().comps!!.conns().any { it.type == "stairtreads" }) break
                if (compose.onAllNodes(hasText("Pick parts") and hasClickAction()).fetchSemanticsNodes().isNotEmpty()) { sh("input keyevent 111"); pause(500); press("Pick parts"); pause(800); look(mid, Vec3(-0.05, -0.08, 1.0), 4600.0); pause(800) }
                act.runOnUiThread { mode().catPick(mode().catK ?: "stairtreads") }; pause(400); shot("pick_treads")
                val z = 1025.9 + dy * 0.5055 + 128.0
                tapAt(screen(Vec3(545.0, 10162.0 + dy, z))); pause(700); tapAt(screen(Vec3(1671.0, 10162.0 + dy, z))); pause(1500) }
            if (mode().comps!!.conns().none { it.type == "stairtreads" }) { act.runOnUiThread { mode().catPick("stairtreads") }; pause(400); hitParts("stairtreads", Vec3(545.0, 10162.0, 1025.9 + 128.0), Vec3(1671.0, 10162.0, 1025.9 + 128.0)) }
            waitFor("the stair treads") { mode().comps!!.conns().count { it.type == "stairtreads" } == 1 }
            act.runOnUiThread { mode().catDone() }; pause(800)
            assertTrue("treads ${q("stairtreads", "tmode")}", q("stairtreads", "tmode") == "tekla")
            waitFor("12 treads", 60_000) { treads() == 12 }
            // Stair end plate: the left landing piece through post STR9001
            openCatalog("Stair end plate")
            tab("Plates"); field("h", "320"); field("b", "150"); field("t", "12"); field("de", "40"); field("dw", "-32.5"); pick("end", "Start"); shot("dialog_sep_plates")
            tab("Bolts"); field("nr", "4"); field("nc", "2"); field("bp", "85"); field("bg", "70"); field("bx", "-32.9"); field("by", "38.7"); pick("bd", "M16"); pick("bside", "On the main part's far side"); field("mgrip", "6"); shot("dialog_sep_bolts")
            sh("input keyevent 111"); pause(600); press("Pick parts"); pause(800); look(Vec3(580.0, 12150.0, 1900.0), Vec3(-1.0, -0.35, 0.45), 1600.0); pause(1200)
            for (k in 0 until 3) { if (mode().comps!!.conns().any { it.type == "Tsep" }) break
                if (compose.onAllNodes(hasText("Pick parts") and hasClickAction()).fetchSemanticsNodes().isNotEmpty()) { sh("input keyevent 111"); pause(500); press("Pick parts"); pause(800); look(Vec3(580.0, 12150.0, 1900.0), Vec3(-1.0, -0.35, 0.45), 1600.0); pause(800) }
                act.runOnUiThread { mode().catPick(mode().catK ?: "Tsep") }; pause(400); shot("pick_sep")
                tapAt(screen(Vec3(530.0, 12060.0 + 20 * k, 2070.0))); pause(700); tapAt(screen(Vec3(560.0, 12235.0, 1500.0 - 100 * k))); pause(1500) }
            if (mode().comps!!.conns().none { it.type == "Tsep" }) { act.runOnUiThread { mode().catPick("Tsep") }; pause(400); hitParts("Tsep", Vec3(530.0, 12060.0, 2070.0), Vec3(560.0, 12235.0, 1500.0)) }
            waitFor("the stair end plate") { mode().comps!!.conns().count { it.type == "Tsep" } == 1 }
            act.runOnUiThread { mode().catDone() }; pause(800)
            orbit(mid, 3600.0, "r12")
            stills("")
            save("c18_case.ss")
            // the treads' dialog again: 12 -> 10 treads, Modify
            act.runOnUiThread { act.testViewport.selection.setSelected(listOf(bodyNamed("TREAD"))) }; pause(800)
            press("Components")
            waitFor("the treads' dialog") { compose.onAllNodes(hasText("Brackets") and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }; pause(800)
            tab("Treads"); field("ntr", "10"); pause(600); shot("dialog_ntr10")
            press("Modify"); pause(800)
            waitFor("10 treads", 60_000) { treads() == 10 }
            closeDialog()
            orbit(mid, 3600.0, "ntr10")
            stills("_ntr10")
            save("c18_ntr10.ss")
            pause(800)
        } finally { runCatching { ua.setRotation(android.app.UiAutomation.ROTATION_UNFREEZE) }; sh("pkill -INT screenrecord"); Thread.sleep(3000); rec.close() }
    }
}
