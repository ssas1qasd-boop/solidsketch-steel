package com.solidsketchapp.steel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.solidsketchapp.core.api.Vec3
import com.solidsketchapp.core.io.MemoryDocument
import com.solidsketchapp.core.view.CoreSelection
import com.solidsketchapp.core.view.UndoStack
import com.solidsketchapp.steel.comp.CompPics
import com.solidsketchapp.steel.comp.Components
import com.solidsketchapp.steel.comp.MemoryStore
import com.solidsketchapp.steel.comp.TkUi
import com.solidsketchapp.steel.data.SteelData
import com.solidsketchapp.steel.geom.StBuild
import com.solidsketchapp.steel.mode.SteelMode
import com.solidsketchapp.steel.mode.SteelOverlay
import com.solidsketchapp.steel.mode.SteelPanel
import com.solidsketchapp.steel.model.StGridLines
import com.solidsketchapp.steel.ui.AppLayout
import com.solidsketchapp.steel.ui.ChromeState
import com.solidsketchapp.steel.ui.MainMenu
import com.solidsketchapp.steel.ui.SsColors
import com.solidsketchapp.steel.ui.SsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * S9b: the app's screens on the owner's phone size (1080 × 2340 px, 420 dpi = density 2.625, 412 × 891 dp, dark), rendered
 * without a device (Robolectric native graphics + Roborazzi) to tests/steel/ui/app/, the same screens as
 * tools/golden/ui_capture.js takes from v69 (tests/steel/ui/v69/). The 3D view is drawn here as v69's background and a flat
 * projection of the ground and steel grids (no OpenGL in the JVM).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h891dp-port-night-420dpi")
class UiScreensTest {
    @get:Rule val compose = createComposeRule()
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
    private val out = File(root, "tests/steel/ui/app").apply { mkdirs() }
    private val data by lazy { SteelData.load { File(root, "steel/src/main/assets/steel/$it").readText() } }
    private val ui by lazy { TkUi.parse(File(root, "steel/src/main/assets/steel/tk_ui.json").readText()) }
    private val colors = SsColors.Dark

    /** A flat view of the model for the JVM: x right, y up into the screen (oblique), z up; 1 m ≈ 12 dp. */
    private fun project(w: Float, h: Float): (Vec3) -> Pair<Float, Float>? = { p -> (w * 0.18f + (p.x * 0.042 + p.y * 0.018).toFloat()) to (h * 0.78f - (p.y * 0.022 + p.z * 0.03).toFloat()) }

    @Composable private fun View(lines: List<Pair<FloatArray, Int>>) = Canvas(Modifier.fillMaxSize()) {
        drawRect(Color(0xFF000000.toInt() or colors.scene))
        val pr = project(size.width, size.height)
        for ((ls, argb) in lines) { var i = 0; while (i + 5 < ls.size) {
            val a = pr(Vec3(ls[i].toDouble(), ls[i + 1].toDouble(), ls[i + 2].toDouble()))!!; val b = pr(Vec3(ls[i + 3].toDouble(), ls[i + 4].toDouble(), ls[i + 5].toDouble()))!!
            drawLine(Color(argb), Offset(a.first, a.second), Offset(b.first, b.second), 1f); i += 6 } }
    }

    private fun state(tool: String = "select") = ChromeState(0, false, false, false, tool, "Tap a body to select · drag to orbit · pinch to zoom", false)

    private fun shot(name: String, content: @Composable () -> Unit) {
        compose.setContent { SsTheme(colors) { content() } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(File(out, "$name.png").path)
    }

    private fun steelMode(): Pair<SteelMode, CompPics> {
        val doc = MemoryDocument(); val undo = UndoStack(capture = doc::snapshot, restore = doc::restore)
        val sel = CoreSelection({ _, _ -> null }, {})
        val m = SteelMode(data, doc, undo, sel, StBuild(data.profiles), project = project(1080f, 2339f), ray = { _, _ -> null }, px = 2.625f)
        m.comps = Components(ui, doc, undo, MemoryStore(), null); m.toggle(); m.lastToast = null   // v69's toast has faded in the reference shots
        val pics = CompPics(ui) { path -> File(root, "steel/src/main/assets/steel/$path").inputStream().use { android.graphics.BitmapFactory.decodeStream(it) }?.asImageBitmap() }
        return m to pics
    }

    private fun steelShot(name: String, sheet: String?, tab: String? = null) {
        val (m, pics) = steelMode(); val g = StGridLines.build(m.grid!!, m.level)!!
        shot(name) { AppLayout(state(), view = { View(listOf(g.all to StGridLines.ALL_ARGB, g.work to StGridLines.WORK_ARGB)) },
            steel = { SteelOverlay(m, 0, project = project(1080f, 2339f)); SteelPanel(m, 0, pics = pics, initialSheet = sheet, initialTab = tab) },
            onUndo = {}, onRedo = {}, onDelete = {}, onSoon = {}, onMore = {}, onTool = {}, toast = null, menu = null, about = null, debug = null) }
    }

    @Composable private fun main(menu: Boolean) = AppLayout(state(), view = { View(emptyList()) }, steel = null, onUndo = {}, onRedo = {}, onDelete = {}, onSoon = {}, onMore = {}, onTool = {},
        toast = null, menu = if (menu) ({ MainMenu(onPick = {}, onDismiss = {}) }) else null, about = null, debug = null)

    @Test fun s01_main() = shot("01_main") { main(false) }
    @Test fun s02_steel_grid() = steelShot("02_steel_grid", null)
    @Test fun s03_grid_sheet() = steelShot("03_grid_sheet", "grid")
    @Test fun s04_catalog() = steelShot("04_catalog", "cat")
    @Test fun s05_dialog_t144() = steelShot("05_dialog_t144", "dlg:T144")
    @Test fun s06_dialog_t144_bolts() = steelShot("06_dialog_t144_bolts", "dlg:T144", "Bolts")
    @Test fun s07_menu() = shot("07_menu") { main(true) }
}
