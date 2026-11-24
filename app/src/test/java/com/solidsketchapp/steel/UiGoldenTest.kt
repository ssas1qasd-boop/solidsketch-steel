package com.solidsketchapp.steel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import com.github.takahirom.roborazzi.captureRoboImage
import com.solidsketchapp.core.api.BodyId
import com.solidsketchapp.core.api.Vec3
import com.solidsketchapp.core.io.MemoryDocument
import com.solidsketchapp.core.view.CoreSelection
import com.solidsketchapp.core.view.OrbitCamera
import com.solidsketchapp.core.view.UndoStack
import com.solidsketchapp.steel.comp.CompPics
import com.solidsketchapp.steel.comp.Components
import com.solidsketchapp.steel.comp.MemoryStore
import com.solidsketchapp.steel.comp.TkCheck
import com.solidsketchapp.steel.comp.TkUi
import com.solidsketchapp.steel.data.SteelData
import com.solidsketchapp.steel.geom.StBuild
import com.solidsketchapp.steel.mode.SteelMode
import com.solidsketchapp.steel.mode.SteelOverlay
import com.solidsketchapp.steel.mode.SteelPanel
import com.solidsketchapp.steel.model.StGrid
import com.solidsketchapp.steel.ui.AppLayout
import com.solidsketchapp.steel.ui.ChromeState
import com.solidsketchapp.steel.ui.LocalSysInsets
import com.solidsketchapp.steel.ui.SsColors
import com.solidsketchapp.steel.ui.SsTheme
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * UI golden (owner, 2026-11-23): the app against web v69 on the owner's phone (412 × 891 dp, density 2.625, dark), like the
 * geometry golden. tools/golden/ui_golden.js runs v69 through fixed scripted states and records, in phone px, where every grid
 * label, part-end handle and component cone is, and the box of every button and sheet (tests/steel/ui/golden/ui_golden.json).
 * This test runs the same script on the app's Compose UI (Robolectric native graphics; no emulator in the cloud session: no
 * KVM): the same parts, component, selection and grid, the camera placed where v69's camera is, then reads what the overlay
 * actually drew.
 *  - labels and handles within 2 px, cones within 2 px;
 *  - button and sheet boxes within 4 px (differences that are known and accepted are listed in
 *    tests/steel/ui/differences.json under "boxes", each with a reason);
 *  - with a phone's system bars (status bar 63 px, 3-button navigation bar 126 px) every control is inside the visible area.
 * Screenshots go to tests/steel/ui/compare/app_*.png (tools/golden/ui_sidebyside.py puts them next to v69's).
 * Runs before every APK (app/build.gradle.kts: assemble depends on the UI golden).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h891dp-port-night-420dpi")
class UiGoldenTest {
    @get:Rule val compose = createComposeRule()
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
    private val out = File(root, "tests/steel/ui/compare").apply { mkdirs() }
    private val data by lazy { SteelData.load { File(root, "steel/src/main/assets/steel/$it").readText() } }
    private val ui by lazy { TkUi.parse(File(root, "steel/src/main/assets/steel/tk_ui.json").readText()) }
    private val gold by lazy { Json.parseToJsonElement(File(root, "tests/steel/ui/golden/ui_golden.json").readText()).jsonObject }
    private val known by lazy { File(root, "tests/steel/ui/differences.json").takeIf { it.exists() }?.let { Json.parseToJsonElement(it.readText()).jsonObject["boxes"]?.jsonObject?.keys } ?: emptySet() }

    private class Setup(val m: SteelMode, val cam: OrbitCamera, val pics: CompPics, val beam: BodyId)

    /** v69's script: Steel mode, a column and a beam into it with End plate 144, the beam selected. */
    private fun setup(select: Boolean = true): Setup {
        val cam = OrbitCamera()
        val doc = MemoryDocument(); val undo = UndoStack(capture = doc::snapshot, restore = doc::restore)
        val sel = CoreSelection({ _, _ -> null }, {})
        val build = StBuild(data.profiles)
        val m = SteelMode(data, doc, undo, sel, build, project = { cam.worldToScreen(it) }, ray = { x, y -> cam.screenRay(x, y).let { it.origin to it.direction } }, px = 2.625f)
        val comps = Components(ui, doc, undo, MemoryStore(), null); m.comps = comps
        m.checks = TkCheck(comps, doc, object : com.solidsketchapp.core.api.BooleanEngine {
            override fun subtract(a: com.solidsketchapp.core.api.Mesh, cutters: List<com.solidsketchapp.core.api.Mesh>) = a
            override fun union(parts: List<com.solidsketchapp.core.api.Mesh>) = parts.first()
            override fun intersect(a: com.solidsketchapp.core.api.Mesh, b: com.solidsketchapp.core.api.Mesh) = a
            override fun isClosedSolid(m: com.solidsketchapp.core.api.Mesh) = true
            override fun volume(m: com.solidsketchapp.core.api.Mesh) = 0.0
        }, data.profiles)
        m.toggle(); m.lastToast = null
        fun mem(set: String, o: String) = m.parts.add(JsonObject(LinkedHashMap(data.propertySet(set)!!.raw).apply { put("kind", JsonPrimitive("member")); putAll(Json.parseToJsonElement(o).jsonObject) }), "golden")
        val a = mem("COLUMN", """{"prof":"HEA300","p1":[0,0,0],"p2":[0,0,7000]}""")
        val b = mem("BEAM", """{"prof":"IPE360","p1":[0,0,6500],"p2":[6000,0,6500]}""")
        comps.add("T144", listOf(a, b), comps.cq("T144"))
        if (select) sel.setSelected(listOf(b))
        val pics = CompPics(ui) { path -> File(root, "steel/src/main/assets/steel/$path").inputStream().use { android.graphics.BitmapFactory.decodeStream(it) }?.asImageBitmap() }
        return Setup(m, cam, pics, b)
    }

    /** v69's camera: eye = target + dist · (cos p cos y, cos p sin y, sin p), Z up, fov 45°. */
    private fun place(cam: OrbitCamera, s: JsonObject) {
        val t = s["target"]!!.jsonArray.map { it.jsonPrimitive.double }; val d = s["dist"]!!.jsonPrimitive.double
        val yaw = s["yaw"]!!.jsonPrimitive.double; val p = s["pitch"]!!.jsonPrimitive.double
        val tv = Vec3(t[0], t[1], t[2])
        cam.lookAt(tv, Vec3(tv.x + d * cos(p) * cos(yaw), tv.y + d * cos(p) * sin(yaw), tv.z + d * sin(p)))
    }

    private val noBars = WindowInsets(0, 0, 0, 0)
    private val phoneBars = WindowInsets(0, 63, 0, 126)
    private fun state() = ChromeState(0, false, false, false, "select", "Tap a body to select · drag to orbit · pinch to zoom", false)

    private fun content(s: Setup, sheet: String?, insets: WindowInsets, rev: () -> Int = { 0 },
                        onLabels: ((List<Triple<String, Float, Float>>) -> Unit)? = null, onMarks: ((List<Pair<Float, Float>>, List<Pair<Float, Float>>) -> Unit)? = null,
                        onSizes: ((List<Float>) -> Unit)? = null) {
        compose.setContent { CompositionLocalProvider(LocalSysInsets provides insets) { SsTheme(SsColors.Dark) { Box(Modifier.fillMaxSize()) {
            AppLayout(state(), view = {}, steel = {
                SteelOverlay(s.m, 0, project = { s.cam.worldToScreen(it) }, viewRev = rev, onDrawn = onLabels, onDrawnMarks = onMarks,
                    scaleAt = { s.cam.pxPerUnit(it)?.toFloat() }, onLabelSizes = onSizes)
                SteelPanel(s.m, 0, pics = s.pics, initialSheet = sheet)
            }, onUndo = {}, onRedo = {}, onDelete = {}, onSoon = {}, onMore = {}, onTool = {}, toast = null, menu = null, about = null, debug = null) } } } }
        compose.waitForIdle()
    }

    private fun pts(a: JsonArray) = a.map { it.jsonArray }.map { Triple(it[0].jsonPrimitive.double.toFloat(), it[1].jsonPrimitive.double.toFloat(), it[2].jsonPrimitive.boolean) }

    /**
     * Every camera state of the v69 script: the labels (in v69's order: X, Y, levels), the handles and the cones the overlay
     * draws must be where v69 draws them, within 2 px. The camera changes the way the app's view does: the camera moves, the
     * view's revision goes up, a frame is drawn. A label that does not follow the view fails here.
     */
    @Test fun overlayFollowsEveryCameraState() {
        val s = setup(); val rev = mutableIntStateOf(0)
        var labels: List<Triple<String, Float, Float>> = emptyList(); var lsizes: List<Float> = emptyList(); var marks: List<Pair<Float, Float>> = emptyList(); var cones: List<Pair<Float, Float>> = emptyList()
        content(s, null, noBars, { rev.intValue }, { labels = it }, { h, c -> marks = h; cones = c }, { lsizes = it })
        val size = compose.onRoot().fetchSemanticsNode().size
        s.cam.viewportWidth = size.width; s.cam.viewportHeight = size.height
        val bad = mutableListOf<String>()
        for ((name, st) in gold["states"]!!.jsonObject) { val g = st.jsonObject
            g["grid"]?.let { it as? JsonObject }?.takeIf { name == "cam5_apply" }?.let { gr -> s.m.setGrid(StGrid(gr["x"]!!.jsonPrimitive.content, gr["y"]!!.jsonPrimitive.content, gr["z"]!!.jsonPrimitive.content)) }
            // What is on screen after the change: what the overlay draws in the next frame, or, if it does not redraw (the S9b
            // grid-label bug), what it drew last; so a stale overlay shows up as labels left where they were.
            place(s.cam, g); rev.intValue++; compose.waitForIdle(); var redrawn = false; val was = labels
            val l0 = labels; labels = emptyList(); val m0 = marks; val c0 = cones
            compose.onRoot().captureRoboImage(File(out, "app_$name.png").path)   // renders a frame (as the phone does every frame)
            if (labels.isEmpty() && l0.isNotEmpty()) { labels = l0; marks = m0; cones = c0 } else redrawn = true
            if (!redrawn) bad += "$name: the overlay was not redrawn after the camera change (labels, handles and cones stay where they were)"
            check(was === l0)
            // Points v69 draws on screen are compared (2 px); a point off screen in v69 must be off screen in the app too (far
            // off screen the 0.5 px difference in screen width, 1081.5 vs 1081, scales up and says nothing about what is seen).
            fun onScreen(x: Float, y: Float) = x >= 0f && y >= 0f && x <= size.width && y <= size.height
            fun cmp(what: String, want: List<Triple<Float, Float, Boolean>>, got: List<Pair<Float, Float>>, tags: List<String>? = null) {
                val vis = want.filter { it.third }
                if (vis.size != got.size) { bad += "$name: $what drawn ${got.size}, v69 ${vis.size}"; return }
                vis.forEachIndexed { i, w -> val e = hypot(got[i].first - w.first, got[i].second - w.second); val tag = tags?.get(i) ?: "$i"
                    if (onScreen(w.first, w.second)) { if (e > 2f) bad += "$name: $what $tag at (${Fmt.p(got[i].first)}, ${Fmt.p(got[i].second)}), v69 (${Fmt.p(w.first)}, ${Fmt.p(w.second)}): off by ${Fmt.p(e)} px" }
                    else if (onScreen(got[i].first, got[i].second)) bad += "$name: $what $tag on screen at (${Fmt.p(got[i].first)}, ${Fmt.p(got[i].second)}), v69 has it off screen (${Fmt.p(w.first)}, ${Fmt.p(w.second)})" }
            }
            cmp("label", pts(g["labels"]!!.jsonArray), labels.map { it.second to it.third }, labels.map { it.first })
            // label size: v69's sprite size on screen, within 2 px (labels on screen)
            g["labelSizes"]?.jsonArray?.map { it.jsonPrimitive.double.toFloat() }?.let { want -> val vis = pts(g["labels"]!!.jsonArray)
                if (want.size == lsizes.size) want.forEachIndexed { i, w -> val (x, y, front) = vis[i]
                    if (front && onScreen(x, y) && abs(lsizes[i] - w) > 2f) bad += "$name: label ${labels.getOrNull(i)?.first} ${Fmt.p(lsizes[i])} px across, v69 ${Fmt.p(w)} px" }
                else bad += "$name: ${lsizes.size} label sizes, v69 ${want.size}" }
            cmp("handle", pts(g["handles"]!!.jsonArray), marks)
            cmp("cone", pts(g["cones"]!!.jsonArray), cones)
        }
        File(out, "overlay_report.txt").writeText(if (bad.isEmpty()) "all labels, handles and cones within 2 px of v69\n" else bad.joinToString("\n", postfix = "\n"))
        assertTrue("overlay vs v69:\n" + bad.joinToString("\n"), bad.isEmpty())
    }

    private object Fmt { fun p(v: Float) = String.format(java.util.Locale.ROOT, "%.1f", v) }

    private fun SemanticsNode.text(): String = (config.getOrNull(SemanticsProperties.EditableText)?.text ?: config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }
        ?: children.firstNotNullOfOrNull { c -> c.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text } } ?: "").trim()
    private fun nodes(m: androidx.compose.ui.test.SemanticsMatcher) = compose.onAllNodes(m, useUnmergedTree = false).fetchSemanticsNodes()

    private fun boxOf(o: JsonObject) = Rect(o["x"]!!.jsonPrimitive.double.toFloat(), o["y"]!!.jsonPrimitive.double.toFloat(),
        (o["x"]!!.jsonPrimitive.double + o["w"]!!.jsonPrimitive.double).toFloat(), (o["y"]!!.jsonPrimitive.double + o["h"]!!.jsonPrimitive.double).toFloat())

    /** v69's boxes of one group against the app's boxes with the same text (in order when texts repeat); within 4 px. */
    private fun cmpBoxes(scr: String, group: String, want: JsonArray, got: List<Pair<String, Rect>>, bad: MutableList<String>, rep: StringBuilder) {
        val used = HashSet<Int>(); val byOrder = group == "inputs" || group == "search"
        val shown = got.withIndex().filter { (_, g) -> g.second.width > 0.5f && g.second.height > 0.5f }.map { it.index }
        val screen = Rect(0f, 0f, gold["canvas"]!!.jsonArray[0].jsonPrimitive.double.toFloat(), gold["canvas"]!!.jsonArray[1].jsonPrimitive.double.toFloat())
        val seen = HashMap<String, Int>()
        for ((wi, w) in want.map { it.jsonObject }.withIndex()) { val t = w["text"]!!.jsonPrimitive.contentOrNull ?: ""; val wb = boxOf(w)
            val nth = seen.merge(t, 1, Int::plus)!! - 1   // repeated labels (the catalog's Properties) are numbered: Properties#10
            val key = "$scr/$group/${if (byOrder) "#$wi" else if (want.count { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull == t } > 1) "$t#$nth" else t}"
            val i = if (byOrder) shown.getOrNull(wi) else (shown.firstOrNull { it !in used && got[it].first == t } ?: shown.firstOrNull { it !in used && t.isNotEmpty() && got[it].first.startsWith(t) })
            // a control scrolled out of view in v69 (a scrolling row or list) is not compared: what counts is what is seen
            if (wb.left >= screen.right - 1f || wb.top >= screen.bottom - 1f) { rep.append("$key: scrolled out of view in v69, not compared\n"); continue }
            if (i == null) { rep.append("$key: not found in the app${if (key in known) "  (known: see differences.json)" else "  FAIL"}\n"); if (key !in known) bad += "$key: not found in the app"; continue }
            used += i; val g = got[i].second
            val e = maxOf(abs(g.left - wb.left), abs(g.top - wb.top), abs(g.right - wb.right), abs(g.bottom - wb.bottom))
            val line = "$key: app [${Fmt.p(g.left)}, ${Fmt.p(g.top)}, ${Fmt.p(g.width)} × ${Fmt.p(g.height)}], v69 [${Fmt.p(wb.left)}, ${Fmt.p(wb.top)}, ${Fmt.p(wb.width)} × ${Fmt.p(wb.height)}], off ${Fmt.p(e)} px"
            rep.append(line).append(if (e > 4f) (if (key in known) "  (known)" else "  FAIL") else "").append('\n')
            if (e > 4f && key !in known) bad += line }
    }

    /** A node's whole box, also where a scrolling row or list clips it (as getBoundingClientRect gives it in v69). */
    private val SemanticsNode.box get() = Rect(positionInRoot, androidx.compose.ui.geometry.Size(size.width.toFloat(), size.height.toFloat()))
    private fun buttons() = nodes(hasClickAction()).map { it.text() to it.box }
    private fun tagged(tag: String) = nodes(hasTestTag(tag)).map { tag to it.box }

    private fun screenBoxes(name: String, sheet: String?) {
        val s = setup(select = name != "panel");   // v69's panel boxes are taken with nothing selected
        content(s, sheet, noBars)
        val size = compose.onRoot().fetchSemanticsNode().size; s.cam.viewportWidth = size.width; s.cam.viewportHeight = size.height
        place(s.cam, gold["states"]!!.jsonObject["cam1_overview"]!!.jsonObject)
        if (System.getenv("UI_TREE") != null) File(out, "tree0_$name.txt").writeText(compose.onRoot(useUnmergedTree = true).printToString(Int.MAX_VALUE))
        compose.onRoot().captureRoboImage(File(out, "app_$name.png").path)
        val bad = mutableListOf<String>(); val rep = StringBuilder()
        // on a sheet, only the sheet's own controls (the panel's buttons are behind it)
        val sheetR = (tagged("st-sheet") + tagged("tkdlg")).firstOrNull()?.second
        val bs = buttons().filter { (_, r) -> name == "panel" || sheetR == null || (r.top >= sheetR.top - 1f && r.left >= sheetR.left - 1f && r.top <= sheetR.bottom && r.left <= sheetR.right) }
        when (name) {
            "panel" -> cmpBoxes(name, "buttons", gold["panel"]!!.jsonArray, bs, bad, rep)
            else -> { val g = gold["boxes"]!!.jsonObject[name]!!.jsonObject
                val sheetBox = (tagged("st-sheet") + tagged("tkdlg")).map { (_, r) -> (g["sheet"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content) to r }
                for ((grp, arr) in g) { val want = arr.jsonArray
                    when (grp) { "sheet" -> cmpBoxes(name, grp, want, sheetBox, bad, rep)
                        "inputs", "search" -> cmpBoxes(name, grp, want, nodes(hasSetTextAction()).map { it.text() to it.box }, bad, rep)
                        else -> cmpBoxes(name, grp, want, bs, bad, rep) } } }
        }
        File(out, "boxes_$name.txt").writeText(rep.toString())
        if (System.getenv("UI_TREE") != null) File(out, "tree_$name.txt").writeText(compose.onRoot(useUnmergedTree = true).printToString(Int.MAX_VALUE))
        // every text the app shows on this screen, with its box (to find where a difference starts)
        File(out, "nodes_$name.txt").writeText(compose.onAllNodes(androidx.compose.ui.test.SemanticsMatcher("any") { true }, useUnmergedTree = true).fetchSemanticsNodes()
            .filter { it.config.getOrNull(SemanticsProperties.Text) != null || it.config.getOrNull(SemanticsProperties.EditableText) != null }
            .joinToString("\n") { n -> val b = n.box; "${n.text().take(40)}: [${Fmt.p(b.left)}, ${Fmt.p(b.top)}, ${Fmt.p(b.width)} × ${Fmt.p(b.height)}]" } + "\n")
        assertTrue("$name boxes vs v69 (4 px):\n" + bad.joinToString("\n"), bad.isEmpty())
    }

    @Test fun panelBoxes() = screenBoxes("panel", null)
    @Test fun gridSheetBoxes() = screenBoxes("grid_sheet", "grid")
    @Test fun catalogBoxes() = screenBoxes("catalog", "cat")
    @Test fun dialogT144Boxes() = screenBoxes("dialog_t144", "dlg:T144")
    @Test fun portalSheetBoxes() = screenBoxes("portal_sheet", "portal")

    /** With a phone's status bar and 3-button navigation bar, every control of every screen is inside the visible area. */
    private fun visible(name: String, sheet: String?) {
        val s = setup(); content(s, sheet, phoneBars)
        val h = compose.onRoot().fetchSemanticsNode().size.height; val w = compose.onRoot().fetchSemanticsNode().size.width
        compose.onRoot().captureRoboImage(File(out, "app_bars_$name.png").path)
        // the part of each control that is drawn (scrolling rows and lists clip the rest): it must not be under a system bar
        val bad = nodes(hasClickAction()).map { it.text() to it.boundsInRoot }.filter { (_, r) -> r.width > 0 && r.height > 0 && (r.top < 63f - 0.5f || r.bottom > h - 126f + 0.5f || r.left < -0.5f || r.right > w + 0.5f) }
            .map { (t, r) -> "\"$t\" at [${Fmt.p(r.left)}, ${Fmt.p(r.top)} – ${Fmt.p(r.right)}, ${Fmt.p(r.bottom)}] outside 63 … ${h - 126}" }
        assertTrue("$name: controls under the system bars:\n" + bad.joinToString("\n"), bad.isEmpty())
    }
    @Test fun visibleMain() = visible("main", null)
    @Test fun visibleGridSheet() = visible("grid_sheet", "grid")
    @Test fun visibleCatalog() = visible("catalog", "cat")
    @Test fun visibleDialogT144() = visible("dialog_t144", "dlg:T144")
    @Test fun visiblePortalSheet() = visible("portal_sheet", "portal")
}
