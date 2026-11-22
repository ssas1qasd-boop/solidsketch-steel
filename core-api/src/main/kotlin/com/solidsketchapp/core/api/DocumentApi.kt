package com.solidsketchapp.core.api

/**
 * The open document as Steel sees it. Bodies and extra document fields
 * (`stGrid`, `stConns`, `stNum`, `stDwg`, `stProject`...) are kept as raw JSON
 * so unknown fields round-trip unchanged (.ss rule). Each body may carry its
 * triangle mesh (the web build's `shape` / manifold), which the viewport draws.
 */
interface DocumentApi {
    fun bodyIds(): List<BodyId>
    /** The body's JSON object as text, or null if there is no such body. */
    fun bodyJson(id: BodyId): String?
    /** The body's mesh in model space (mm), or null if it has none. */
    fun bodyMesh(id: BodyId): Mesh?
    fun addBody(json: String, mesh: Mesh?): BodyId
    fun replaceBody(id: BodyId, json: String, mesh: Mesh?)
    fun removeBody(id: BodyId)

    /** A top-level document field as JSON text (e.g. "stConns"), or null. */
    fun field(name: String): String?
    fun setField(name: String, json: String?)

    fun addListener(listener: Listener)
    fun removeListener(listener: Listener)

    fun interface Listener {
        fun onDocumentChanged()
    }
}
