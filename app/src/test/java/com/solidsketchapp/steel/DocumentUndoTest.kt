package com.solidsketchapp.steel

import com.solidsketchapp.core.api.Mesh
import com.solidsketchapp.core.io.MemoryDocument
import com.solidsketchapp.core.view.UndoStack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** The undo stack on the real document, as MainActivity wires them. */
class DocumentUndoTest {
    private val m1 = Mesh(floatArrayOf(0f, 0f, 0f), intArrayOf())
    private val m2 = Mesh(floatArrayOf(1f, 1f, 1f), intArrayOf())

    @Test
    fun replaceAndDeleteUndoRedo() {
        val doc = MemoryDocument()
        val undo = UndoStack(capture = doc::snapshot, restore = doc::restore)
        val id = doc.addBody("{}", m1)
        undo.group("Cut hole") { doc.replaceBody(id, "{}", m2) }
        undo.group("Delete") { doc.removeBody(id) }
        assertEquals(emptyList<Any>(), doc.bodyIds())
        undo.undo(); assertSame(m2, doc.bodyMesh(id))
        undo.undo(); assertSame(m1, doc.bodyMesh(id))
        undo.redo(); undo.redo(); assertEquals(emptyList<Any>(), doc.bodyIds())
    }
}
