package com.solidsketchapp.core.io

import com.solidsketchapp.core.api.BodyId
import com.solidsketchapp.core.api.DocumentApi
import com.solidsketchapp.core.api.Mesh

/**
 * In-memory document. Bodies are immutable values, so a snapshot is a cheap map copy;
 * [snapshot] / [restore] back the undo stack. [SsCodec] loads and saves it as `.ss`.
 * Every top-level and `doc` field is kept as raw JSON in its original order, so fields this
 * app does not know round-trip unchanged.
 * Not thread-safe: use it from one thread (the UI thread).
 */
class MemoryDocument : DocumentApi {
    data class Body(val json: String, val mesh: Mesh?)
    data class Snapshot internal constructor(
        internal val bodies: Map<BodyId, Body>,
        internal val fields: Map<String, String>,
        internal val nextId: Long,
    )

    private var bodies = LinkedHashMap<BodyId, Body>()
    private var fields = LinkedHashMap<String, String>().apply { V69_DEFAULTS.forEach { (k, v) -> put(k, v) } }
    private var nextId = 1L
    private val listeners = mutableListOf<DocumentApi.Listener>()

    companion object {
        /**
         * Every `doc` field web v69 writes (docObject + ssDocument's `kept`), in its order, with the
         * values of a new v69 document. `bodies` goes at [V69_BODIES_INDEX]. A new document starts
         * with these, so a file made in this app has the same doc fields as one made in v69.
         */
        val V69_DEFAULTS: List<Pair<String, String>> = listOf(
            "v" to "1", "nextId" to "1", "colorIdx" to "0", "sketchLines" to "[]", "sketchPlane" to "null",
            "paths" to "[]", "nextPathId" to "1", "profiles" to "[]", "nextProfileId" to "1",
            "folders" to "[]", "notes28" to "[]", "refModels28" to "[]", "furn28" to "[]",
            "stGrid" to "null", "stGen" to "null", "stConns" to "[]", "stNextConn" to "1", "stNum" to "null",
            "stDwg" to "null", "curType" to "null", "curTag" to "\"\"", "unit" to "\"mm\"", "kept" to "[]",
        )
        const val V69_BODIES_INDEX = 9
    }

    /** Top-level `.ss` fields other than `doc` (format, version, app, unit, name, rev, savedAt, unknown ones), raw, in order. */
    var header: List<Pair<String, String>> = emptyList()
        internal set
    /** Position of `doc` among the top-level fields, and of `bodies` among the `doc` fields. */
    var headerDocIndex = 0
        internal set
    var bodiesIndex = V69_BODIES_INDEX
        internal set

    /** The id the next new body gets (the web build's `nextId`). */
    val nextBodyId: Long get() = nextId

    /** Replaces everything with a loaded file (no undo step; listeners are told once). */
    internal fun loadFrom(
        header: List<Pair<String, String>>, headerDocIndex: Int,
        fields: List<Pair<String, String>>, bodiesIndex: Int,
        bodies: List<Triple<BodyId, String, Mesh?>>, nextId: Long,
    ) {
        this.header = header; this.headerDocIndex = headerDocIndex; this.bodiesIndex = bodiesIndex
        this.fields = LinkedHashMap<String, String>().apply { fields.forEach { (k, v) -> put(k, v) } }
        this.bodies = LinkedHashMap<BodyId, Body>().apply { bodies.forEach { (id, j, m) -> put(id, Body(j, m)) } }
        this.nextId = nextId
        changed()
    }

    /**
     * Makes this document a copy of [other] (bodies and meshes are shared: they are immutable).
     * Use it to swap in a file read on a background thread, or to take a copy to save off the UI thread.
     */
    fun replaceAllFrom(other: MemoryDocument) {
        loadFrom(other.header, other.headerDocIndex, other.fields.entries.map { it.key to it.value }, other.bodiesIndex,
            other.bodies.entries.map { Triple(it.key, it.value.json, it.value.mesh) }, other.nextId)
    }

    /** Field names in their order (for the writer). */
    fun fieldNames(): List<String> = fields.keys.toList()

    override fun bodyIds(): List<BodyId> = bodies.keys.toList()
    override fun bodyJson(id: BodyId): String? = bodies[id]?.json
    override fun bodyMesh(id: BodyId): Mesh? = bodies[id]?.mesh

    override fun addBody(json: String, mesh: Mesh?): BodyId {
        val id = BodyId((nextId++).toString())
        bodies[id] = Body(json, mesh)
        // v69's makeBodyRecord advances colorIdx with every new body (nextId++ and colorIdx++ together).
        fields["colorIdx"]?.toLongOrNull()?.let { fields["colorIdx"] = (it + 1).toString() }
        changed()
        return id
    }

    override fun replaceBody(id: BodyId, json: String, mesh: Mesh?) {
        require(id in bodies) { "no body $id" }
        bodies[id] = Body(json, mesh)
        changed()
    }

    override fun removeBody(id: BodyId) {
        if (bodies.remove(id) != null) changed()
    }

    override fun field(name: String): String? = fields[name]
    override fun setField(name: String, json: String?) {
        if (json == null) fields.remove(name) else fields[name] = json   // a new field goes at the end
        changed()
    }

    override fun addListener(listener: DocumentApi.Listener) { listeners += listener }
    override fun removeListener(listener: DocumentApi.Listener) { listeners -= listener }

    fun snapshot(): Snapshot = Snapshot(LinkedHashMap(bodies), LinkedHashMap(fields), nextId)

    fun restore(s: Snapshot) {
        bodies = LinkedHashMap(s.bodies)
        fields = LinkedHashMap(s.fields)
        nextId = s.nextId
        changed()
    }

    private fun changed() = listeners.toList().forEach { it.onDocumentChanged() }
}
