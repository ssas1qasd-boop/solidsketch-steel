package com.solidsketchapp.steel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.compose.ui.test.onRoot
import com.solidsketchapp.core.api.Vec3
import com.solidsketchapp.core.io.MemoryDocument
import com.solidsketchapp.core.view.CoreSelection
import com.solidsketchapp.core.view.OrbitCamera
import com.solidsketchapp.core.view.UndoStack
import com.solidsketchapp.core.view.math.minus
import com.solidsketchapp.core.view.math.plus
import com.solidsketchapp.core.view.math.times
import com.solidsketchapp.core.view.math.normalized
import com.solidsketchapp.steel.data.SteelData
import com.solidsketchapp.steel.geom.StBuild
import com.solidsketchapp.steel.mode.SteelMode
import com.solidsketchapp.steel.mode.SteelOverlay
import com.solidsketchapp.steel.model.StGrid
import com.solidsketchapp.steel.model.StGridLines
import com.solidsketchapp.steel.ui.AppLayout
import com.solidsketchapp.steel.ui.ChromeState
import com.solidsketchapp.steel.ui.SsColors
import com.solidsketchapp.steel.ui.SsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs
import kotlin.math.hypot

/**
 * S9b regression (owner's recording of the S9b APK): the grid labels stayed where they were first drawn while the grid
 * orbited, panned and zoomed, and were bunched together after Apply. The overlay is composed the way MainActivity does it
 * (inside AppLayout's Steel slot, the camera revision as state) over a real OrbitCamera. After orbit, pan, zoom, a viewport
 * resize (system bar insets change) and a new grid, every drawn label must be at the projected end of its grid line
 * (StGridLines' own line ends, the label 540 mm beyond: v69 0.6 × 900 mm), within 1 px.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h891dp-port-night-420dpi")
class GridLabelsFollowViewTest {
    @get:Rule val compose = createComposeRule()
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
    private val data by lazy { SteelData.load { File(root, "steel/src/main/assets/steel/$it").readText() } }

    /** Where each label must be, from the grid lines themselves: X labels past the start (low Y end) of each X line, Y labels past the low X end of each Y line. */
    private fun expected(g: StGrid, cam: OrbitCamera): Map<String, Pair<Float, Float>> {
        val lines = StGridLines.build(g, 0)!!.work; val out = LinkedHashMap<String, Pair<Float, Float>>()
        val nx = g.xs.size
        for (i in 0 until nx + g.ys.size) { val k = i * 6
            val a = Vec3(lines[k].toDouble(), lines[k + 1].toDouble(), lines[k + 2].toDouble()); val b = Vec3(lines[k + 3].toDouble(), lines[k + 4].toDouble(), lines[k + 5].toDouble())
            val d = (a - b).normalized(); val anchor = a + d * 540.0
            out[if (i < nx) g.xLabel(i) else g.yLabel(i - nx)] = cam.worldToScreen(anchor)!! }
        return out
    }

    @Test fun labelsFollowEveryViewChange() {
        val cam = OrbitCamera().apply { viewportWidth = 1080; viewportHeight = 2339 }
        val doc = MemoryDocument(); val undo = UndoStack(capture = doc::snapshot, restore = doc::restore)
        val m = SteelMode(data, doc, undo, CoreSelection({ _, _ -> null }, {}), StBuild(data.profiles), project = { cam.worldToScreen(it) }, ray = { x, y -> cam.screenRay(x, y).let { it.origin to it.direction } }, px = 2.625f)
        m.toggle(); m.lastToast = null
        cam.fit(Vec3(-1500.0, -1500.0, 0.0), Vec3(37500.0, 21500.0, 7000.0))
        val rev = mutableIntStateOf(0); var drawn: List<Triple<String, Float, Float>> = emptyList()
        val state = ChromeState(0, false, false, false, "select", "", false)
        compose.setContent { SsTheme(SsColors.Dark) { Box(Modifier.fillMaxSize()) {
            AppLayout(state, view = {}, steel = { SteelOverlay(m, 0, project = { cam.worldToScreen(it) }, viewRev = { rev.intValue }, onDrawn = { drawn = it }) },
                onUndo = {}, onRedo = {}, onDelete = {}, onSoon = {}, onMore = {}, onTool = {}, toast = null, menu = null, about = null, debug = null) } } }
        val bad = mutableListOf<String>()
        fun check(step: String) {
            compose.waitForIdle(); drawn = emptyList()
            compose.onRoot().captureRoboImage(File(root, "app/build/labels/$step.png").path)   // Robolectric draws when a frame is rendered: render one (as the phone does every frame); the image is kept for a look
            assertTrue("$step: nothing drawn (the overlay did not redraw)", drawn.isNotEmpty())
            val g = m.grid!!; val exp = expected(g, cam)
            for ((t, ex) in exp) { val got = drawn.firstOrNull { it.first == t }
                if (got == null) { bad += "$step: label $t not drawn"; continue }
                val e = hypot(got.second - ex.first, got.third - ex.second); if (e > 1f) bad += "$step: label $t at (${got.second}, ${got.third}), its line end at (${ex.first}, ${ex.second}) — off by $e px" }
        }
        fun change(step: String, f: () -> Unit) { f(); rev.intValue++; check(step) }
        check("first draw")
        change("orbit") { cam.orbit(220f, -90f) }
        change("pan") { cam.pan(-160f, 240f) }
        change("zoom in") { cam.zoom(0.55) }
        change("zoom out") { cam.zoom(2.4) }
        change("viewport resize (insets)") { cam.viewportWidth = 1080; cam.viewportHeight = 2004 }
        change("new grid (Apply)") { assertEquals(null, m.setGrid(StGrid("0 4*13195", "0 5*5570", "0 7500 1500"))) }
        change("orbit after Apply") { cam.orbit(-300f, 60f) }
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
        assertTrue("labels drawn: ${drawn.size}", drawn.size == m.grid!!.xs.size + m.grid!!.ys.size + m.grid!!.zs.size)
    }
}
