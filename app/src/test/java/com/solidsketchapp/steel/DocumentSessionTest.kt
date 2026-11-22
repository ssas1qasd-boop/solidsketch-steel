package com.solidsketchapp.steel

import com.solidsketchapp.core.api.BodyId
import com.solidsketchapp.core.api.Mesh
import com.solidsketchapp.core.io.MemoryDocument
import com.solidsketchapp.core.io.SsCodec
import com.solidsketchapp.core.view.BodyStyle
import com.solidsketchapp.core.view.UndoStack
import com.solidsketchapp.core.view.ViewportController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringWriter

/** Owner S3 check: the selection ("Selected: BEAM (2)") stayed after Open. */
class DocumentSessionTest {
    private fun box(x: Float) = Mesh(
        floatArrayOf(x, 0f, 0f, x + 1, 0f, 0f, x + 1, 1f, 0f, x, 1f, 0f, x, 0f, 1f, x + 1, 0f, 1f, x + 1, 1f, 1f, x, 1f, 1f),
        intArrayOf(0, 2, 1, 0, 3, 2, 4, 5, 6, 4, 6, 7, 0, 1, 5, 0, 5, 4, 1, 2, 6, 1, 6, 5, 2, 3, 7, 2, 7, 6, 3, 0, 4, 3, 4, 7),
    )

    private fun session(): DocumentSession {
        val doc = MemoryDocument()
        val vp = ViewportController(doc) { BodyStyle(0x808080, false) }   // no org.json on the JVM
        return DocumentSession(doc, UndoStack(doc::snapshot, doc::restore), vp)
    }

    @Test
    fun openClearsSelectionHighlightAndUndoEvenWhenIdsRepeat() {
        val s = session()
        s.doc.addBody("{\"name\":\"COLUMN\"}", box(0f)); s.doc.addBody("{\"name\":\"BEAM\"}", box(2f))
        s.undo.group("Delete") { s.doc.removeBody(BodyId("1")) }
        s.viewport.selection.setSelected(listOf(BodyId("2"))); s.viewport.selection.setHighlighted(listOf(BodyId("2")))
        // The opened file also has bodies 1 and 2 (and 3).
        val loaded = MemoryDocument().apply { repeat(3) { addBody("{}", box(it * 2f)) } }
        s.replaceWith(loaded)
        assertEquals(listOf("1", "2", "3"), s.doc.bodyIds().map { it.value })
        assertTrue(s.viewport.selection.selected().isEmpty())
        assertTrue(s.viewport.selection.highlighted.isEmpty())
        assertFalse(s.undo.canUndo()); assertFalse(s.undo.canRedo())
    }

    @Test
    fun newGivesAnEmptyV69DocumentWithNoSelection() {
        val s = session()
        s.doc.addBody("{}", box(0f)); s.viewport.selection.setSelected(listOf(BodyId("1")))
        s.newDocument()
        assertTrue(s.doc.bodyIds().isEmpty())
        assertTrue(s.viewport.selection.selected().isEmpty())
        assertEquals(MemoryDocument.V69_DEFAULTS.map { it.first }, s.doc.fieldNames())
        // And it saves with all 24 v69 doc fields (owner item 1).
        val sw = StringWriter(); SsCodec.writeJson(s.doc, sw, "t")
        assertTrue(sw.toString().contains(""""doc":{"v":1,"nextId":1,"colorIdx":0,"sketchLines":[],"sketchPlane":null,"paths":[],"nextPathId":1,"profiles":[],"nextProfileId":1,"bodies":[],"folders":[],"""))
    }
}
