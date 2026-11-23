package com.solidsketchapp.core.view

import com.solidsketchapp.core.api.BodyId
import com.solidsketchapp.core.api.DocumentApi
import com.solidsketchapp.core.api.Mesh
import com.solidsketchapp.core.api.Ray
import com.solidsketchapp.core.api.SelectionApi
import com.solidsketchapp.core.api.Vec3
import com.solidsketchapp.core.api.ViewportApi
import com.solidsketchapp.core.view.gl.SceneRenderer

/** How a body is drawn, read from its JSON (web build: `color`, `hidden`). */
data class BodyStyle(val rgb: Int, val hidden: Boolean) {
    companion object {
        val DEFAULT = BodyStyle(0x8E8E93, false) // ST_CLASS 0 grey
        fun fromJson(json: String?): BodyStyle {
            if (json == null) return DEFAULT
            return try {
                val o = org.json.JSONObject(json)
                BodyStyle(if (o.has("color")) o.optInt("color", DEFAULT.rgb) else DEFAULT.rgb, o.optBoolean("hidden", false))
            } catch (e: org.json.JSONException) { DEFAULT }
        }
    }
}

/**
 * The viewport behind [ViewportApi] and [SelectionApi]: camera, picking, overlays, and the
 * link from the document to the renderer. Use it on the UI thread.
 */
class ViewportController(
    private val doc: DocumentApi,
    private val styleOf: (String?) -> BodyStyle = BodyStyle::fromJson,
) : ViewportApi, DocumentApi.Listener, GestureTracker.Listener {
    private var tapHandler: ((Float, Float) -> Boolean)? = null
    private val viewListeners = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()

    val camera = OrbitCamera()
    val renderer = SceneRenderer()
    val selection = CoreSelection(::pickAt, ::refresh)
    /** Called after every redraw request; the host can show frame info. */
    var onRedraw: (() -> Unit)? = null
    /** GLSurfaceView.requestRender, set by the view. */
    internal var requestRender: (() -> Unit)? = null

    private val sent = HashMap<BodyId, Pair<Mesh?, String?>>()
    private val pick = LinkedHashMap<BodyId, PickBody>()
    private var firstFit = true

    init {
        doc.addListener(this)
        sync()
    }

    fun dispose() = doc.removeListener(this)

    override fun onDocumentChanged() = sync()

    /** Sends changed bodies to the renderer and the picker. */
    fun sync() {
        val ids = doc.bodyIds()
        val alive = ids.toHashSet()
        for (id in sent.keys.filter { it !in alive }) { sent.remove(id); pick.remove(id); renderer.post(SceneRenderer.Op.RemoveBody(id)) }
        for (id in ids) {
            val mesh = doc.bodyMesh(id); val json = doc.bodyJson(id)
            val old = sent[id]
            if (old != null && old.first === mesh && old.second == json) continue
            sent[id] = mesh to json
            val style = styleOf(json)
            if (mesh == null) { renderer.post(SceneRenderer.Op.RemoveBody(id)); pick.remove(id); continue }
            renderer.post(SceneRenderer.Op.SetBody(id, mesh, style.rgb, style.hidden))
            val box = Aabb.of(mesh)
            if (style.hidden || box == null) pick.remove(id) else pick[id] = PickBody(id, mesh, box)
        }
        selection.retain(alive)
        sceneBox()?.let { b ->
            camera.sceneRadius = maxOf(1.0, kotlin.math.sqrt(((b.max.x - b.min.x).let { it * it }) + ((b.max.y - b.min.y).let { it * it }) + ((b.max.z - b.min.z).let { it * it })) / 2)
            if (firstFit) { firstFit = false; camera.fit(b.min, b.max) }
        }
        refresh()
    }

    fun pickAt(x: Float, y: Float): SelectionApi.Hit? = Picker.pick(camera.screenRay(x, y), pick.values)

    fun sceneBox(ids: Collection<BodyId>? = null): Aabb? =
        (if (ids == null) pick.values else ids.mapNotNull { pick[it] }).map { it.box }.reduceOrNull(Aabb::union)

    fun onSize(w: Int, h: Int) {
        camera.viewportWidth = w; camera.viewportHeight = h
        refresh()
    }

    /** Pushes the camera and selection to the GL thread and asks for a frame. */
    fun refresh() {
        renderer.frame = SceneRenderer.Frame(camera.viewProjection(), camera.viewMatrix(), selection.selected().toSet(), selection.highlighted)
        requestRender?.invoke()
        onRedraw?.invoke()
        for (l in viewListeners) l()
    }

    // ViewportApi
    override fun setOverlay(name: String, lines: FloatArray, colorArgb: Int) { renderer.post(SceneRenderer.Op.SetOverlay(name, lines.copyOf(), colorArgb)); refresh() }
    override fun removeOverlay(name: String) { renderer.post(SceneRenderer.Op.RemoveOverlay(name)); refresh() }
    override fun fitAll() { sceneBox()?.let { camera.fit(it.min, it.max) }; refresh() }
    override fun fitBodies(ids: Collection<BodyId>) { sceneBox(ids)?.let { camera.fit(it.min, it.max) }; refresh() }
    override fun screenRay(x: Float, y: Float): Ray = camera.screenRay(x, y)
    override fun worldToScreen(p: Vec3): Pair<Float, Float>? = camera.worldToScreen(p)
    override fun requestRedraw() = refresh()

    // Gestures
    override fun orbit(dx: Float, dy: Float) { camera.orbit(dx, dy); refresh() }
    override fun pan(dx: Float, dy: Float) { camera.pan(dx, dy); refresh() }
    override fun zoom(factor: Double) { camera.zoom(factor); refresh() }
    override fun tap(x: Float, y: Float) { if (tapHandler?.invoke(x, y) == true) return; selection.setSelected(listOfNotNull(pickAt(x, y)?.body)) }

    override fun setTapHandler(handler: ((x: Float, y: Float) -> Boolean)?) { tapHandler = handler }
    override fun addViewListener(listener: () -> Unit) { viewListeners += listener }
    override fun removeViewListener(listener: () -> Unit) { viewListeners -= listener }
    override fun doubleTap(x: Float, y: Float) = fitAll()
}
