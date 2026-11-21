package com.solidsketchapp.core.api

/** The 3D view. Steel draws its helpers (grid, previews, symbols...) as named overlay groups. */
interface ViewportApi {
    /** Adds or replaces overlay group [name] (e.g. "st.grid", "st.symbols"). */
    fun setOverlay(name: String, lines: FloatArray, colorArgb: Int)
    fun removeOverlay(name: String)
    fun fitAll()
    fun fitBodies(ids: Collection<BodyId>)
    /** Ray through screen pixel ([x], [y]). */
    fun screenRay(x: Float, y: Float): Ray
    /** Screen pixel of a model point, or null if behind the camera. */
    fun worldToScreen(p: Vec3): Pair<Float, Float>?
    fun requestRedraw()
}
