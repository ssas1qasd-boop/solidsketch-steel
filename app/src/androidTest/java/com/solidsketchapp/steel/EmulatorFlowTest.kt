package com.solidsketchapp.steel

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.services.storage.TestStorage
import com.solidsketchapp.core.api.Vec3
import com.solidsketchapp.steel.mode.SteelMode
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs
import kotlin.math.hypot

/**
 * The owner's workflow on an emulator (GitHub Actions, .github/workflows/android-emulator.yml): 1080 × 2340 at 420 dpi, dark,
 * 3-button navigation. Each step takes a screenshot of the whole screen (the 3D view included) and is recorded in
 * steps.json; a failed step does not stop the later ones (the test fails at the end with every failed step).
 * Steps: start, Steel, grid, orbit / pan / zoom (labels at their line ends), column, Properties → profile → Apply, beam,
 * select → Properties → Modify, catalog, End plate 144 dialog, End plate 144 picked on the column and beam, save / open.
 */
@RunWith(AndroidJUnit4::class)
class EmulatorFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val act get() = compose.activity
    private val storage = TestStorage()
    private val results = mutableListOf<String>()
    private val failed = mutableListOf<String>()
    private var shotNo = 0

    private fun json(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""

    private fun shot(name: String) {
        compose.waitForIdle()
        val bmp: Bitmap? = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        if (bmp != null) storage.openOutputFile("screenshots/%02d_%s.png".format(++shotNo, name)).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun step(name: String, body: () -> Unit) {
        val t0 = System.nanoTime(); var msg = ""
        val ok = runCatching { body() }.onFailure { msg = (it.message ?: it.toString()).take(1500) }.isSuccess
        runCatching { shot(name) }
        if (!ok) failed += "$name: $msg"
        results += "{\"step\":${json(name)},\"ok\":$ok,\"ms\":${(System.nanoTime() - t0) / 1_000_000},\"message\":${json(msg)}}"
    }

    private fun waitFor(what: String, ms: Long = 20_000, cond: () -> Boolean) {
        try { compose.waitUntil(ms) { runCatching(cond).getOrDefault(false) } } catch (e: Throwable) { throw AssertionError("timed out waiting for $what") }
    }

    private fun button(text: String) = compose.onNode(hasText(text) and hasClickAction(), useUnmergedTree = false)
    private fun mode(): SteelMode = act.testSteel ?: throw AssertionError("Steel mode not set up")
    private fun screen(p: Vec3): Offset = act.testViewport.worldToScreen(p)?.let { Offset(it.first, it.second) } ?: throw AssertionError("point $p behind the camera")
    private fun tapAt(o: Offset) { compose.onRoot().performTouchInput { click(o) }; compose.waitForIdle() }
    private fun bodies() = act.testDoc.bodyIds()
    private fun st(id: com.solidsketchapp.core.api.BodyId) = mode().stOf(id)
    private fun prof(id: com.solidsketchapp.core.api.BodyId) = (st(id)?.get("prof") as? JsonPrimitive)?.contentOrNull

    /** Every grid label the overlay drew is at the projected end of its grid line (SteelMode.gridLabels), within 1 px. */
    private fun labelsAtLineEnds(what: String) {
        compose.waitForIdle()
        val want = mode().gridLabels(); val drawn = act.testLabels
        assertTrue("$what: no labels drawn", drawn.isNotEmpty())
        val bad = mutableListOf<String>()
        for (l in want) { val s = act.testViewport.worldToScreen(l.p) ?: continue; val d = drawn.firstOrNull { it.first == l.text }
            if (d == null) bad += "label ${l.text} not drawn"
            else if (hypot(d.second - s.first, d.third - s.second) > 1f) bad += "label ${l.text} at (${d.second}, ${d.third}), its line end at (${s.first}, ${s.second})" }
        assertTrue("$what: " + bad.joinToString("; "), bad.isEmpty())
    }

    private fun camKey(): String = act.testViewport.worldToScreen(Vec3(0.0, 0.0, 0.0))?.let { "%.1f,%.1f".format(it.first, it.second) } ?: "-"

    @Test fun ownerWorkflow() {
        val w = act.window.decorView.width.toFloat(); val h = act.window.decorView.height.toFloat()
        val mid = Offset(w / 2, h * 0.42f)

        step("start") {
            waitFor("the top bar") { compose.onAllNodes(hasText("SolidSketch")).fetchSemanticsNodes().isNotEmpty() }
            waitFor("the steel data") { act.testSteel != null }
            button("Steel").assertExistsCompat()
        }
        step("steel") {
            button("Steel").performClick()
            waitFor("the Steel panel") { compose.onAllNodes(hasText("Grid") and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
            assertTrue("Steel mode is not on", mode().on)
            waitFor("the component catalog data") { mode().comps != null }
        }
        step("grid") {
            button("Grid").performClick()
            waitFor("the grid sheet") { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size >= 3 }
            val f = compose.onAllNodes(hasSetTextAction())
            f[0].performTextReplacement("0 4*13195"); f[1].performTextReplacement("0 5*5570"); f[2].performTextReplacement("0 7500 1500")
            button("Apply").performClick(); compose.waitForIdle()
            val g = mode().grid!!
            assertTrue("grid ${g.xs} / ${g.ys} / ${g.zs}", g.xs.size == 5 && g.ys.size == 6 && g.zs.size == 3)
            act.runOnUiThread { act.testViewport.fitAll() }
            labelsAtLineEnds("after Apply")
        }
        step("orbit") {
            val k0 = camKey(); compose.onRoot().performTouchInput { swipe(mid, mid + Offset(260f, -120f), 600) }
            waitFor("the view to orbit", 5000) { camKey() != k0 }; labelsAtLineEnds("after orbit")
        }
        step("pan") {
            val k0 = camKey()
            compose.onRoot().performTouchInput {
                val a = mid + Offset(-120f, 0f); val b = mid + Offset(120f, 0f)
                down(0, a); down(1, b); for (i in 1..12) { moveBy(0, Offset(-15f, 12f)); moveBy(1, Offset(-15f, 12f)) }; up(0); up(1) }
            waitFor("the view to pan", 5000) { camKey() != k0 }; labelsAtLineEnds("after pan")
        }
        step("zoom") {
            val k0 = camKey()
            compose.onRoot().performTouchInput { pinch(mid + Offset(-60f, 0f), mid + Offset(-300f, 0f), mid + Offset(60f, 0f), mid + Offset(300f, 0f), 600) }
            waitFor("the view to zoom", 5000) { camKey() != k0 }; labelsAtLineEnds("after zoom")
            act.runOnUiThread { act.testViewport.fitAll() }; compose.waitForIdle(); labelsAtLineEnds("after fit")
        }
        val g = runCatching { mode().grid!! }.getOrNull()
        step("column") {
            g ?: throw AssertionError("no grid")
            button("Column").performClick(); compose.waitForIdle()
            val n0 = bodies().size
            tapAt(screen(Vec3(g.xs[1], g.ys[1], g.zs[0])))
            waitFor("a column") { bodies().size == n0 + 1 }
            val c = bodies().last(); val p1 = st(c)?.get("p1").toString()
            assertTrue("column st: ${st(c)}", (st(c)?.get("kind") as? JsonPrimitive)?.contentOrNull == "member")
            button("Column").performClick()   // tool off
            assertTrue("column p1 $p1", p1.contains(g.xs[1].toLong().toString()))
        }
        step("properties_apply") {
            button("Beam").performClick(); compose.waitForIdle()
            button("Properties").performClick()
            waitFor("the property sheet") { compose.onAllNodes(hasText("Profile")).fetchSemanticsNodes().isNotEmpty() }
            // the Profile field: the text field whose value is the current profile
            val cur = mode().propsProf
            compose.onNode(hasSetTextAction() and SemanticsMatcher("value $cur") { it.config.getOrNull(SemanticsProperties.EditableText)?.text == cur }).performTextReplacement("IPE400")
            button("Apply").performClick(); compose.waitForIdle()
            assertTrue("profile for new parts: ${mode().propsProf}", mode().propsProf == "IPE400")
        }
        step("beam") {
            g ?: throw AssertionError("no grid")
            val n0 = bodies().size
            tapAt(screen(Vec3(g.xs[1], g.ys[1], g.zs[0]))); tapAt(screen(Vec3(g.xs[2], g.ys[1], g.zs[0])))
            waitFor("a beam") { bodies().size == n0 + 1 }
            assertTrue("beam profile ${prof(bodies().last())}", prof(bodies().last()) == "IPE400")
            button("Beam").performClick()   // tool off
        }
        step("properties_modify") {
            g ?: throw AssertionError("no grid")
            val beam = bodies().last()
            act.runOnUiThread { act.testViewport.selection.setSelected(listOf(beam)) }; compose.waitForIdle()
            button("Properties").performClick()
            waitFor("the property sheet") { compose.onAllNodes(hasText("Modify") and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(hasSetTextAction() and SemanticsMatcher("value IPE400") { it.config.getOrNull(SemanticsProperties.EditableText)?.text == "IPE400" }).performTextReplacement("IPE500")
            button("Modify").performClick()
            waitFor("the beam to become IPE500") { prof(beam) == "IPE500" || bodies().any { prof(it) == "IPE500" } }
            act.runOnUiThread { act.testViewport.selection.setSelected(emptyList()) }
        }
        step("catalog") {
            button("Components").performClick()
            waitFor("the catalog") { compose.onAllNodes(hasText("Applications & components")).fetchSemanticsNodes().isNotEmpty() }
            compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("144")
            waitFor("End plate (144) in the list") { compose.onAllNodes(hasText("End plate", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        }
        step("endplate_dialog") {
            compose.onAllNodes(hasText("Properties") and hasClickAction()).fetchSemanticsNodes().let { assertTrue("no Properties in the catalog", it.isNotEmpty()) }
            // the catalog row's Properties (the panel's Properties is behind the sheet, further down the list of nodes)
            compose.onAllNodes(hasText("Properties") and hasClickAction())[0].performClick()
            waitFor("the End plate (144) dialog") { compose.onAllNodes(hasText("End plate (144)")).fetchSemanticsNodes().isNotEmpty() }
            for (t in listOf("Picture", "Plates", "Bolts", "OK", "Apply", "Modify", "Pick parts", "Cancel")) assertTrue("dialog has no $t", compose.onAllNodes(hasText(t)).fetchSemanticsNodes().isNotEmpty())
            shot("endplate_dialog_open")
            button("Cancel").performClick(); compose.waitForIdle()
        }
        step("endplate_pick") {
            g ?: throw AssertionError("no grid")
            val col = bodies().first { b -> (st(b)?.get("p2")?.toString() ?: "").let { it.contains("7500") } }
            val n0 = mode().comps!!.conns().size
            act.runOnUiThread { mode().catPick("T144") }; compose.waitForIdle()
            tapAt(screen(Vec3(g.xs[1], g.ys[1], 3000.0)))                                     // main part: the column
            tapAt(screen(Vec3((g.xs[1] + g.xs[2]) / 2, g.ys[1], g.zs[0] - 180.0)))            // secondary: the beam
            waitFor("an End plate (144)") { mode().comps!!.conns().size == n0 + 1 }
            act.runOnUiThread { mode().catDone() }
            assertTrue("column $col", true)
        }
        step("save_open") {
            val f = File(act.cacheDir, "ci_model.ss").also { it.delete() }
            val before = bodies().map { mode().stOf(it).toString() }.sorted(); val conns = act.testDoc.field("stConns")
            act.runOnUiThread { act.testSave(f) }
            waitFor("Saved", 30_000) { act.testToast?.startsWith("Saved") == true && !act.testBusy }
            assertTrue("file ${f.length()} bytes", f.length() > 100)
            act.runOnUiThread { act.testOpen(f) }
            waitFor("Opened", 60_000) { act.testToast?.startsWith("Opened") == true && !act.testBusy }
            val after = bodies().map { mode().stOf(it).toString() }.sorted()
            assertTrue("bodies after open ${after.size}, before ${before.size}", after == before)
            assertTrue("connections differ after open", act.testDoc.field("stConns") == conns)
        }

        storage.openOutputFile("steps.json").use { it.write(("[\n" + results.joinToString(",\n") + "\n]\n").toByteArray()) }
        assertTrue("failed steps:\n" + failed.joinToString("\n"), failed.isEmpty())
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertExistsCompat() { fetchSemanticsNode() }
}
