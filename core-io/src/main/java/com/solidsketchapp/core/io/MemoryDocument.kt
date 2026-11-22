package com.solidsketchapp.core.io

import com.solidsketchapp.core.api.BodyId
import com.solidsketchapp.core.api.DocumentApi
import com.solidsketchapp.core.api.Mesh

/**
 * In-memory document. Bodies are immutable values, so a snapshot is a cheap map copy;
 * [snapshot] / [restore] back the undo stack. S3 adds .ss load/save on top.
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
    private var fields = LinkedHashMap<String, String>()
    private var nextId = 1L
    private val listeners = mutableListOf<DocumentApi.Listener>()

    override fun bodyIds(): List<BodyId> = bodies.keys.toList()
    override fun bodyJson(id: BodyId): String? = bodies[id]?.json
    override fun bodyMesh(id: BodyId): Mesh? = bodies[id]?.mesh

    override fun addBody(json: String, mesh: Mesh?): BodyId {
        val id = BodyId((nextId++).toString())
        bodies[id] = Body(json, mesh)
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
        if (json == null) fields.remove(name) else fields[name] = json
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
