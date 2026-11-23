package com.solidsketchapp.steel

import android.net.Uri
import android.os.Bundle
import android.os.Debug
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import com.solidsketchapp.core.api.Fmt
import com.solidsketchapp.core.api.Vec3
import com.solidsketchapp.core.io.MemoryDocument
import com.solidsketchapp.core.io.SsCodec
import com.solidsketchapp.core.io.SsNames
import com.solidsketchapp.core.view.Aabb
import com.solidsketchapp.core.view.ManifoldBooleanEngine
import com.solidsketchapp.core.view.MeshOps
import com.solidsketchapp.core.view.NativeCore
import com.solidsketchapp.core.view.UndoStack
import com.solidsketchapp.core.view.ViewportController
import com.solidsketchapp.core.view.ViewportView
import com.solidsketchapp.steel.license.LicenseGate
import com.solidsketchapp.steel.license.OpenLicenseGate
import com.solidsketchapp.steel.mode.NativeSteel
import com.solidsketchapp.steel.data.SteelAssets
import com.solidsketchapp.steel.geom.StBuild
import com.solidsketchapp.steel.mode.SteelMode
import com.solidsketchapp.steel.mode.SteelOverlay
import com.solidsketchapp.steel.mode.SteelPanel
import com.solidsketchapp.steel.model.StGridLines
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * S3: open and save `.ss` files (web v69 format), render big models (R10) with a debug overlay
 * for frame time and memory; plus the S2 checks (orbit, picking, undo/redo, booleans).
 */
class MainActivity : ComponentActivity() {
    // License gate postponed (owner, 2026-11-21): always allows.
    private val gate: LicenseGate = OpenLicenseGate
    private val doc = MemoryDocument()
    private val undo = UndoStack(capture = doc::snapshot, restore = doc::restore)
    private val engine = ManifoldBooleanEngine()
    private lateinit var viewport: ViewportController
    private lateinit var session: DocumentSession

    private var status by mutableStateOf("")
    private var debugLine by mutableStateOf("")
    private var busy by mutableStateOf(false)
    private var fileName by mutableStateOf<String?>(null)
    private var tick by mutableIntStateOf(0)
    private var dataLine by mutableStateOf("data: loading…")
    private var steel by mutableStateOf<SteelMode?>(null)
    private var compPics by mutableStateOf<com.solidsketchapp.steel.comp.CompPics?>(null)
    private var viewTick by mutableIntStateOf(0)
    /** S9b: v69's chrome state. Toast (2.6 s, v69 toast()), ⋮ menu, About sheet, theme (v69 data-theme; "system" = the phone), debug overlay (off). */
    private var toastMsg by mutableStateOf<Pair<String, Long>?>(null)
    private var menuOpen by mutableStateOf(false)
    private var aboutOpen by mutableStateOf(false)
    private var themePref by mutableStateOf("system")
    private var debugOn by mutableStateOf(false)
    private var tool by mutableStateOf("select")
    private fun toast(msg: String) { toastMsg = msg to System.nanoTime() }

    private val openLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(::openSs) }
    private val saveLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri -> uri?.let(::saveSs) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewport = ViewportController(doc)
        session = DocumentSession(doc, undo, viewport)
        viewport.selection.addListener { tick++ }
        doc.addListener { tick++ }
        // v69 starts with an empty model and its ground grid (no demo parts).
        val prefs = getSharedPreferences("app", MODE_PRIVATE); themePref = prefs.getString("theme", "system") ?: "system"; debugOn = prefs.getBoolean("debug", false)
        // S4: the steel tables from v69 (assets/steel/*.json).
        lifecycleScope.launch {
            dataLine = runCatching { withContext(Dispatchers.IO) { SteelAssets.load(assets) } }.fold(
                { d -> setUpSteel(d); Fmt.f("data: %d profiles · %d property sets · %d components in %d groups · %s = %s", d.profiles.cat.size, d.propertySets.size, d.tk.components.size, d.tk.groups.size, "PHI300-600-8-200-12", d.profiles.lookup("PHI300-600-8-200-12")?.let { Fmt.f("%.1f kg/m", it.m) } ?: "?") },
                { "data: could not load (${it.message})" },
            )
        }
        viewport.setTapHandler { x, y -> steel?.tap(x, y) ?: false }
        viewport.addViewListener { viewTick++ }
        lifecycleScope.launch { while (true) { if (debugOn) debugLine = debugText(); delay(500) } }
        // targetSdk 35 draws edge to edge: every bar, panel and sheet pads itself with the system bar insets (UiRulesTest).
        enableEdgeToEdge()
        // The app is LTR like the web build, whatever the phone's language (owner, S2 phone check).
        setContent {
            val colors = com.solidsketchapp.steel.ui.SsColors.pick(themePref, androidx.compose.foundation.isSystemInDarkTheme())
            androidx.compose.runtime.LaunchedEffect(colors) {
                viewport.renderer.background = colors.scene; viewport.setOverlay("grid", CoreDemo.grid(), (0xFF000000 or colors.gridMajor.toLong()).toInt()); viewport.requestRedraw()
                // status / navigation bar icons follow the theme (dark theme: light icons)
                enableEdgeToEdge(androidx.activity.SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { colors.dark })
            }
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { MaterialTheme { com.solidsketchapp.steel.ui.SsTheme(colors) { Screen() } } }
        }
    }

    /** S5: Steel mode over the app's document, viewport and undo (only :core-api interfaces reach :steel). */
    private fun setUpSteel(d: com.solidsketchapp.steel.data.SteelData) {
        val build = StBuild(d.profiles, union = { engine.union(it) })
        val regen = com.solidsketchapp.steel.conn.StRegen(build, engine, d.profiles, d.tk)
        steel = SteelMode(d, doc, undo, viewport.selection, build,
            project = { viewport.worldToScreen(it) },
            ray = { x, y -> viewport.screenRay(x, y).let { r -> r.origin to r.direction } },
            px = resources.displayMetrics.density, changed = { tick++; drawSteelGrid() },
            regen = regen)
        doc.addListener { drawSteelGrid() }
        // S7: the component catalog and dialogs (tk_ui.json, 1.6 MB) load in the background.
        lifecycleScope.launch {
            val ui = runCatching { withContext(Dispatchers.IO) { assets.open("steel/tk_ui.json").use { com.solidsketchapp.steel.comp.TkUi.parse(it.readBytes().decodeToString()) } } }.getOrNull() ?: return@launch
            val prefs = getSharedPreferences("steel", MODE_PRIVATE)
            val store = object : com.solidsketchapp.steel.comp.SteelStore {
                override fun get(key: String): String? = prefs.getString(key, null)
                override fun put(key: String, value: String) { prefs.edit().putString(key, value).apply() }
            }
            val comps = com.solidsketchapp.steel.comp.Components(ui, doc, undo, store, regen)
            steel?.comps = comps; steel?.checks = com.solidsketchapp.steel.comp.TkCheck(comps, doc, engine, d.profiles)
            compPics = com.solidsketchapp.steel.comp.CompPics(ui) { path -> assets.open("steel/$path").use { android.graphics.BitmapFactory.decodeStream(it) }?.asImageBitmap() }
            tick++
        }
    }

    /** v69 stGridDraw: all levels faint, the work level strong; only while Steel mode is on. */
    private fun drawSteelGrid() {
        val m = steel; val lines = m?.grid?.takeIf { m.on }?.let { StGridLines.build(it, m.level) }
        if (lines == null) { viewport.removeOverlay("st.grid"); viewport.removeOverlay("st.grid.work"); return }
        viewport.setOverlay("st.grid", lines.all, StGridLines.ALL_ARGB); viewport.setOverlay("st.grid.work", lines.work, StGridLines.WORK_ARGB)
    }

    override fun onDestroy() {
        viewport.dispose()
        super.onDestroy()
    }

    @androidx.compose.runtime.Composable
    private fun Screen() {
        @Suppress("UNUSED_EXPRESSION") tick
        val c = com.solidsketchapp.steel.ui.LocalSs.current
        val sel = viewport.selection.selected()
        val m = steel
        val state = com.solidsketchapp.steel.ui.ChromeState(doc.bodyIds().size, !busy && undo.canUndo(), !busy && undo.canRedo(), !busy && sel.isNotEmpty(), tool,
            "Tap a body to select · drag to orbit · pinch to zoom", menuOpen)
        com.solidsketchapp.steel.ui.AppLayout(state,
            view = { AndroidView(factory = { ViewportView(it, viewport) }, modifier = Modifier.fillMaxSize()) },
            steel = if (m != null && m.on) ({ SteelOverlay(m, tick + viewTick, project = { viewport.worldToScreen(it) }, viewRev = { viewTick }); SteelPanel(m, tick, pics = compPics) }) else null,
            onUndo = { undo.undo() }, onRedo = { undo.redo() }, onDelete = { undo.group("Delete") { sel.forEach(doc::removeBody) } },
            onSoon = { toast("$it comes with the main SolidSketch port") }, onMore = { menuOpen = !menuOpen }, onTool = ::pickTool,
            toast = toastMsg?.first,
            menu = if (menuOpen) ({ com.solidsketchapp.steel.ui.MainMenu(onPick = { menuOpen = false; menu(it) }, onDismiss = { menuOpen = false }) }) else null,
            about = if (aboutOpen) ({ com.solidsketchapp.steel.ui.AboutSheet(aboutLines(), themePref, { themePref = it; getSharedPreferences("app", MODE_PRIVATE).edit().putString("theme", it).apply() },
                debugOn, { debugOn = it; debugLine = ""; getSharedPreferences("app", MODE_PRIVATE).edit().putBoolean("debug", it).apply() }) { aboutOpen = false } }) else null,
            debug = debugLine.takeIf { debugOn && it.isNotEmpty() })
        toastMsg?.let { (_, at) -> androidx.compose.runtime.LaunchedEffect(at) { delay(2600); if (toastMsg?.second == at) toastMsg = null } }
    }

    /** v69 #tools: Steel enters Steel mode; Select is the app's tap-to-select; the others are the main port's tools. */
    private fun pickTool(id: String) {
        when (id) {
            "steel" -> steel?.let { if (!it.on) { it.toggle(); viewport.fitAll() } } ?: toast("Steel is loading…")   // v69 stToggle shows its toast (Steel panel)
            "select" -> tool = "select"
            else -> toast("${com.solidsketchapp.steel.ui.V69_TOOLS.first { it.first == id }.second} comes with the main SolidSketch port")
        }
    }

    /** v69 #menu items the app has; the others say they come later. */
    private fun menu(id: String) {
        when (id) {
            "open" -> openLauncher.launch(arrayOf("*/*"))
            "save" -> saveLauncher.launch(suggestedName())
            "reset" -> viewport.fitAll()
            "clear" -> if (doc.bodyIds().isNotEmpty()) undo.group("Clear all") { doc.bodyIds().forEach(doc::removeBody); doc.setField("stConns", null) }
            "units" -> toast("Units: mm")
            "about" -> aboutOpen = true
            else -> toast("${com.solidsketchapp.steel.ui.V69_MENU.first { it.first == id }.second.trimEnd('…')} comes with the main SolidSketch port")
        }
    }

    private fun aboutLines() = listOf(
        "SolidSketch Steel ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
        "${NativeCore.version()} · ${NativeSteel.version()}",
        "License: ${if (gate.isUnlocked) "open" else "locked"}",
        dataLine,
        "PHI300-600-8-200-12: " + (steel?.data?.profiles?.lookup("PHI300-600-8-200-12")?.let { Fmt.f("%.1f kg/m", it.m) } ?: "?"),
    ) + listOfNotNull(status.takeIf { it.isNotEmpty() })

    /** Debug overlay: frame time, frame rate, draw calls, triangles, GPU buffers and memory. */
    private fun debugText(): String {
        val s = viewport.renderer.stats
        val rt = Runtime.getRuntime()
        val heap = (rt.totalMemory() - rt.freeMemory()) / 1048576.0
        return Fmt.f(
            "frame %.1f ms cpu · %.0f fps · %d calls\n%.2f M tris · %d bodies · %d chunks\nGPU %.0f MB · heap %.0f/%.0f MB · native %.0f MB",
            s.cpuMs, s.fps, s.drawCalls, s.triangles / 1e6, s.bodies, s.chunks,
            s.gpuBytes / 1048576.0, heap, rt.maxMemory() / 1048576.0, Debug.getNativeHeapAllocatedSize() / 1048576.0,
        )
    }

    private fun suggestedName(): String =
        SsNames.suggest(fileName, doc.header.firstOrNull { it.first == "name" }?.second?.trim('"'))

    private fun displayName(uri: Uri): String = runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull() ?: uri.lastPathSegment ?: "file.ss"

    private fun openSs(uri: Uri) {
        val name = displayName(uri)
        busy = true; toast("Opening $name…")
        lifecycleScope.launch {
            val t0 = System.nanoTime()
            val loaded = runCatching { withContext(Dispatchers.IO) { contentResolver.openInputStream(uri)!!.use { SsCodec.read(it) } } }
            loaded.onSuccess { d ->
                session.replaceWith(d)   // also clears the selection and the undo history
                fileName = name
                val n = doc.bodyIds().size
                val tris = doc.bodyIds().sumOf { doc.bodyMesh(it)?.triangleCount?.toLong() ?: 0L }
                // Web v69 toast: "Opened <file> · <n> bodies"; plus the time for the S3 check.
                status = Fmt.f("Opened %s · %d bod%s · %.2f M triangles · %.1f s", name, n, if (n == 1) "y" else "ies", tris / 1e6, (System.nanoTime() - t0) / 1e9)
                toast(Fmt.f("Opened %s · %d bod%s", name, n, if (n == 1) "y" else "ies"))   // v69's toast
            }.onFailure { toast("Could not open $name: ${it.message}") }
            busy = false
        }
    }

    private fun newDocument() {
        session.newDocument()
        fileName = null
    }

    private fun saveSs(uri: Uri) {
        val name = displayName(uri)
        val copy = MemoryDocument().also { it.replaceAllFrom(doc) }   // cheap: meshes are shared, maps copied
        busy = true; toast("Saving $name…")
        lifecycleScope.launch {
            val t0 = System.nanoTime()
            runCatching { withContext(Dispatchers.IO) { contentResolver.openOutputStream(uri, "wt")!!.use { SsCodec.write(copy, it, name = SsNames.base(name)) } } }
                .onSuccess { fileName = name; toast("Saved $name") }
                .onFailure { toast("Could not save $name: ${it.message}") }
            busy = false
        }
    }

}
