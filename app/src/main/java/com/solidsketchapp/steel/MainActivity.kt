package com.solidsketchapp.steel

import android.net.Uri
import android.os.Bundle
import android.os.Debug
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
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

    private var status by mutableStateOf("Building…")
    private var debugLine by mutableStateOf("")
    private var busy by mutableStateOf(false)
    private var fileName by mutableStateOf<String?>(null)
    private var tick by mutableIntStateOf(0)
    private var dataLine by mutableStateOf("data: loading…")

    private val openLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(::openSs) }
    private val saveLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri -> uri?.let(::saveSs) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewport = ViewportController(doc)
        session = DocumentSession(doc, undo, viewport)
        viewport.selection.addListener { tick++ }
        doc.addListener { tick++ }
        lifecycleScope.launch {
            val t0 = System.nanoTime()
            val parts = withContext(Dispatchers.Default) { CoreDemo.build(engine) }
            val ms = (System.nanoTime() - t0) / 1e6
            CoreDemo.addTo(doc, parts)
            viewport.setOverlay("grid", CoreDemo.grid(), 0xFFA3ABB8.toInt())
            viewport.fitAll()
            status = Fmt.f("Plate with 8 holes: %.0f mm³ (built in %.0f ms)", engine.volume(parts.plate), ms)
        }
        // S4: the steel tables from v69 (assets/steel/*.json).
        lifecycleScope.launch {
            dataLine = runCatching { withContext(Dispatchers.IO) { SteelAssets.load(assets) } }.fold(
                { d -> Fmt.f("data: %d profiles · %d property sets · %d components in %d groups", d.profiles.cat.size, d.propertySets.size, d.tk.components.size, d.tk.groups.size) },
                { "data: could not load (${it.message})" },
            )
        }
        if (BuildConfig.DEBUG) lifecycleScope.launch { while (true) { debugLine = debugText(); delay(500) } }
        // The app is LTR like the web build, whatever the phone's language (owner, S2 phone check).
        setContent { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { MaterialTheme { Screen() } } }
    }

    override fun onDestroy() {
        viewport.dispose()
        super.onDestroy()
    }

    @androidx.compose.runtime.Composable
    private fun Screen() {
        @Suppress("UNUSED_EXPRESSION") tick
        val sel = viewport.selection.selected()
        Box(Modifier.fillMaxSize().background(Color(0xFFEEF1F5))) {
            AndroidView(factory = { ViewportView(it, viewport) }, modifier = Modifier.fillMaxSize())
            Column(Modifier.safeDrawingPadding().padding(8.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    ToolButton("New", enabled = !busy) { newDocument() }
                    ToolButton("Open", enabled = !busy) { openLauncher.launch(arrayOf("*/*")) }
                    ToolButton("Save", enabled = !busy) { saveLauncher.launch(suggestedName()) }
                    ToolButton("Fit") { viewport.fitAll() }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ToolButton("Undo", enabled = !busy && undo.canUndo()) { undo.undo() }
                    ToolButton("Redo", enabled = !busy && undo.canRedo()) { undo.redo() }
                    ToolButton("Cut hole", enabled = !busy && sel.isNotEmpty()) { cutHole() }
                    ToolButton("Delete", enabled = !busy && sel.isNotEmpty()) { undo.group("Delete") { sel.forEach(doc::removeBody) } }
                }
                val name = sel.firstOrNull()?.let { doc.bodyJson(it) }?.let { Regex("\"name\":\"([^\"]*)\"").find(it)?.groupValues?.get(1) }
                Text(if (sel.isEmpty()) "Tap a part to select it. Double tap: fit." else "Selected: $name (${sel.first().value})", fontSize = 13.sp)
                Text(status, fontSize = 12.sp)
                Text("Steel ${BuildConfig.VERSION_NAME} · ${NativeCore.version()} · ${NativeSteel.version()} · license: ${if (gate.isUnlocked) "open" else "locked"}", fontSize = 11.sp, color = Color(0xFF5B6270))
                Text(dataLine, fontSize = 11.sp, color = Color(0xFF5B6270))
            }
            if (BuildConfig.DEBUG && debugLine.isNotEmpty()) {
                Text(debugLine, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF262B34),
                    modifier = Modifier.align(Alignment.BottomStart).safeDrawingPadding().padding(8.dp).background(Color(0xCCFFFFFF)).padding(4.dp))
            }
        }
    }

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
        busy = true; status = "Opening $name…"
        lifecycleScope.launch {
            val t0 = System.nanoTime()
            val loaded = runCatching { withContext(Dispatchers.IO) { contentResolver.openInputStream(uri)!!.use { SsCodec.read(it) } } }
            loaded.onSuccess { d ->
                viewport.removeOverlay("grid")
                session.replaceWith(d)   // also clears the selection and the undo history
                fileName = name
                val n = doc.bodyIds().size
                val tris = doc.bodyIds().sumOf { doc.bodyMesh(it)?.triangleCount?.toLong() ?: 0L }
                // Web v69 toast: "Opened <file> · <n> bodies"; plus the time for the S3 check.
                status = Fmt.f("Opened %s · %d bod%s · %.2f M triangles · %.1f s", name, n, if (n == 1) "y" else "ies", tris / 1e6, (System.nanoTime() - t0) / 1e9)
            }.onFailure { status = "Could not open $name: ${it.message}" }
            busy = false
        }
    }

    private fun newDocument() {
        viewport.removeOverlay("grid")
        session.newDocument()
        fileName = null
        status = "New document"
    }

    private fun saveSs(uri: Uri) {
        val name = displayName(uri)
        val copy = MemoryDocument().also { it.replaceAllFrom(doc) }   // cheap: meshes are shared, maps copied
        busy = true; status = "Saving $name…"
        lifecycleScope.launch {
            val t0 = System.nanoTime()
            runCatching { withContext(Dispatchers.IO) { contentResolver.openOutputStream(uri, "wt")!!.use { SsCodec.write(copy, it, name = SsNames.base(name)) } } }
                .onSuccess { fileName = name; status = Fmt.f("Saved %s (%.1f s)", name, (System.nanoTime() - t0) / 1e9) }
                .onFailure { status = "Could not save $name: ${it.message}" }
            busy = false
        }
    }

    /** Boolean on the selected part: a 60 mm hole through its box centre, along Z. One undo step. */
    private fun cutHole() {
        val id = viewport.selection.selected().firstOrNull() ?: return
        val mesh = doc.bodyMesh(id) ?: return
        val box = Aabb.of(mesh) ?: return
        val c = Vec3((box.min.x + box.max.x) / 2, (box.min.y + box.max.y) / 2, box.min.z - 10)
        val cutter = MeshOps.translate(NativeCore.cylinder(30f, (box.max.z - box.min.z + 20).toFloat(), 48), c)
        val json = doc.bodyJson(id) ?: return
        lifecycleScope.launch {
            val result = runCatching { withContext(Dispatchers.Default) { engine.subtract(mesh, listOf(cutter)) } }
            result.onSuccess { r -> undo.group("Cut hole") { doc.replaceBody(id, json, r) }; status = Fmt.f("Cut: %.0f → %.0f mm³", engine.volume(mesh), engine.volume(r)) }
                .onFailure { status = "Cut failed: ${it.message}" }
        }
    }
}
