package com.solidsketchapp.core.view

import com.solidsketchapp.core.api.BodyId
import com.solidsketchapp.core.api.DocumentApi
import com.solidsketchapp.core.api.Mesh
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewportControllerTest {
    /** Minimal document for the test (core-view does not depend on core-io). */
    private class Doc : DocumentApi {
        val bodies = LinkedHashMap<BodyId, Pair<String, Mesh?>>()
        val ls = mutableListOf<DocumentApi.Listener>()
        var next = 1
        override fun bodyIds() = bodies.keys.toList()
        override fun bodyJson(id: BodyId) = bodies[id]?.first
        override fun bodyMesh(id: BodyId) = bodies[id]?.second
        override fun addBody(json: String, mesh: Mesh?) = BodyId("${next++}").also { bodies[it] = json to mesh; ls.forEach { l -> l.onDocumentChanged() } }
        override fun replaceBody(id: BodyId, json: String, mesh: Mesh?) { bodies[id] = json to mesh; ls.forEach { it.onDocumentChanged() } }
        override fun removeBody(id: BodyId) { bodies.remove(id); ls.forEach { it.onDocumentChanged() } }
        override fun field(name: String): String? = null
        override fun setField(name: String, json: String?) {}
        override fun addListener(listener: DocumentApi.Listener) { ls += listener }
        override fun removeListener(listener: DocumentApi.Listener) { ls -= listener }
    }

    private fun style(json: String?) = BodyStyle(0x123456, json?.contains("\"hidden\":true") == true)

    @Test
    fun tapSelectsBodyUnderCentreAndDeleteClearsSelection() {
        val doc = Doc()
        val c = ViewportController(doc, ::style)
        c.onSize(1000, 1000)
        val a = doc.addBody("{}", boxMesh(-500f, -500f, -500f, 500f, 500f, 500f))
        c.fitAll()
        c.tap(500f, 500f)
        assertEquals(listOf(a), c.selection.selected())
        doc.removeBody(a)
        assertEquals(emptyList<BodyId>(), c.selection.selected())
        assertNull(c.pickAt(500f, 500f))
    }

    @Test
    fun hiddenBodiesAreNotPickedAndTapOnNothingClears() {
        val doc = Doc()
        val c = ViewportController(doc, ::style)
        c.onSize(800, 600)
        val shown = doc.addBody("{}", boxMesh(0f, 0f, 0f, 100f, 100f, 100f))
        doc.addBody("""{"hidden":true}""", boxMesh(-1000f, -1000f, -1000f, 1000f, 1000f, 1000f))
        c.fitAll()
        assertEquals(shown, c.pickAt(400f, 300f)?.body)
        c.tap(400f, 300f); assertEquals(listOf(shown), c.selection.selected())
        c.tap(1f, 1f); assertTrue(c.selection.selected().isEmpty())
    }
}
