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
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * C19 Railing welds as Tekla (docs/steel/PARITY.md "C19"): R13's lower flight, left railing (SR9002 / HR9002). The two sloped stringers
 * UPN240 (R13 844 / 845); Railing (S77) from the catalog, Parameters tab: Railing type = v69, Pick parts (the stringer at y 8335, the other
 * stringer at y 11810.441): v69's railing; orbit, orthographic stills, saved (c19/c19_v69.ss); Undo. Then the same dialog with Railing type =
 * Tekla (R13) and R13's values (posts CHS48*3 at 700 plan, hand rail CHS48*3 1000 above the foot centre, railing PD50*5 at 500, weld a 2),
 * the same picks: 6 posts welded to the stringer, each foot fitted on the stringer's sloped top flange (lowest point = R13's p1), the top
 * square at the hand rail's axis (R13's p2); orbit front / end / top / 3D, orthographic side / front / foot / top stills in landscape,
 * saved (c19_tekla.ss). /sdcard/c19_rec.mp4.
 */
@RunWith(AndroidJUnit4::class)
class C19RecordingTest {
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
    private val mid = Vec3(565.5, 10070.0, 1600.0)
    private val r13 = listOf(  // R13 806-809, 832, 833: p1 (foot's lowest point) / p2 (top, at the hand rail's axis)
        doubleArrayOf(8335.0, 224.348, 1236.481), doubleArrayOf(9035.0, 578.207, 1590.339), doubleArrayOf(9735.0, 932.066, 1944.198),
        doubleArrayOf(10435.0, 1285.924, 2298.056), doubleArrayOf(11135.0, 1639.783, 2651.915), doubleArrayOf(11810.441, 1981.226, 2993.359))
    private fun stills(tag: String) {
        landscape(true)
        orthoShot(mid, Vec3(-1.0, 0.0, 0.0), 2700.0, "ortho_side$tag")
        orthoShot(Vec3(565.5, 10435.0, 1300.0), Vec3(-1.0, 0.0, 0.0), 260.0, "ortho_foot$tag")
        orthoShot(Vec3(565.5, 10435.0, 2290.0), Vec3(-1.0, 0.0, 0.0), 260.0, "ortho_top$tag")
        orthoShot(Vec3(1108.0, 10435.0, 1700.0), Vec3(0.0, -1.0, 0.0), 1400.0, "ortho_front$tag")
        landscape(false)
    }
    private fun named(n: String) = act.testDoc.bodyIds().filter { (mode().stOf(it)?.get("name") as? JsonPrimitive)?.contentOrNull == n }
    private fun v(e: JsonElement?) = (e as kotlinx.serialization.json.JsonArray).map { (it as JsonPrimitive).content.toDouble() }
    private fun railing(mode: String, values: Boolean, s844: BodyId, s845: BodyId) {
        openCatalog("Railing")
        tab("Parameters"); pick("tmode", mode)
        if (values) { field("post", "CHS48*3"); field("rail", "CHS48*3"); field("knee", "PD50*5"); field("rh", "1000"); field("kh", "500"); field("ps", "700"); field("wa", "2") }
        shot("dialog_railing_" + (if (values) "tekla" else "v69"))
        sh("input keyevent 111"); pause(600); press("Pick parts"); pause(800); look(mid, Vec3(-1.0, -0.5, 0.6), 5200.0); pause(1200)
        // the generator takes the picked points: R13's foot centres on the stringers' top faces (exact picks; a tap's point is a few mm off)
        act.runOnUiThread { mode().catPick(mode().catK ?: "TS77") }; pause(600); shot("pick_railing")
        act.runOnUiThread { mode().catHit(s844, doubleArrayOf(565.5, 8335.0, 236.481)) }; pause(900)
        act.runOnUiThread { mode().catHit(s845, doubleArrayOf(1650.5, 11810.441, 1993.359)) }; pause(1500)
        waitFor("the railing", 60_000) { named("STANCHION").isNotEmpty() && !act.testBusy }
        act.runOnUiThread { mode().catDone() }; pause(800); closeDialog()
    }

    @Test fun c19Recording() {
        dir = "c19"; start()
        look(mid, Vec3(-1.0, -0.6, 0.7), 5200.0); pause(800)
        val rec = ua.executeShellCommand("screenrecord --bit-rate 6000000 --time-limit 180 /sdcard/c19_rec.mp4")
        try {
            pause(1500)
            val s844 = member("BEAM", """{"name":"STRINGER","prof":"UPN240","p1":[565.5,12035.972,1972.906],"p2":[565.5,8289.137,78.835],"rot":"Top","rAng":180,"plane":"Middle","depth":"Middle"}""")
            val s845 = member("BEAM", """{"name":"STRINGER","prof":"UPN240","p1":[1650.5,8289.137,78.835],"p2":[1650.5,12035.972,1972.906],"rot":"Top","rAng":180,"plane":"Middle","depth":"Middle"}""")
            pause(1200); shot("members")
            // before: v69's railing (Railing type = v69)
            railing("v69 (posts by spacing, rails welded to the posts)", false, s844, s845)
            orbit(mid, 4200.0, "v69"); stills("_v69"); save("c19_v69.ss")
            for (k in 0 until 6) { if (named("STANCHION").isEmpty()) break; press("Undo"); pause(1200) }
            assertTrue("v69 railing undone", named("STANCHION").isEmpty())
            // after: Tekla (R13)
            railing("Tekla (R13): posts fitted on the part, top square at the hand rail", true, s844, s845)
            val posts = named("STANCHION").map { mode().stOf(it)!! }
            assertTrue("6 posts, got ${posts.size}", posts.size == 6)
            for (r in r13) { val p = posts.firstOrNull { st -> val a = v(st["p1"]); abs(a[1] - r[0]) < 0.1 }
                assertTrue("post at y ${r[0]}", p != null); val a = v(p!!["p1"]); val b = v(p["p2"])
                assertTrue("post y ${r[0]}: p1 $a p2 $b", abs(a[0] - 565.5) < 0.1 && abs(a[2] - r[1]) < 0.1 && abs(b[2] - r[2]) < 0.1) }
            val welds = mode().comps!!.conns().filter { it.type == "weldonly" }
            assertTrue("6 welds fit face, got ${welds.size}", welds.size == 6 && welds.all { (it.q["fit"] as? JsonPrimitive)?.contentOrNull == "face" && it.parts[0] == s844.value })
            orbit(mid, 4200.0, "tekla"); stills("_tekla"); save("c19_tekla.ss")
            pause(800)
        } finally { runCatching { ua.setRotation(android.app.UiAutomation.ROTATION_UNFREEZE) }; sh("pkill -INT screenrecord"); Thread.sleep(3000); rec.close() }
    }
}
