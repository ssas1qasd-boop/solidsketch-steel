package com.solidsketchapp.core.api

/**
 * The open document as Steel sees it. Bodies and extra document fields
 * (`stGrid`, `stConns`, `stNum`, `stDwg`, `stProject`...) are kept as raw JSON
 * so unknown fields round-trip unchanged (.ss rule).
 */
interface DocumentApi {
    fun bodyIds(): List<BodyId>
    /** The body's JSON object as text, or null if there is no such body. */
    fun bodyJson(id: BodyId): String?
    fun addBody(json: String): BodyId
    fun replaceBody(id: BodyId, json: String)
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
