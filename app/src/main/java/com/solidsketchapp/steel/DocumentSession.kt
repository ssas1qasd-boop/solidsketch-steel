package com.solidsketchapp.steel

import com.solidsketchapp.core.io.MemoryDocument
import com.solidsketchapp.core.view.UndoStack
import com.solidsketchapp.core.view.ViewportController

/**
 * The open document's life cycle: Open and New replace the whole document, so the selection,
 * the highlight and the undo history of the old one go (owner, S3 check: "Selected: BEAM (2)"
 * stayed after Open because the new file also has a body 2).
 */
class DocumentSession(
    val doc: MemoryDocument,
    val undo: UndoStack<MemoryDocument.Snapshot>,
    val viewport: ViewportController,
) {
    /** Replaces the document with [loaded] (a file read off the UI thread). Call on the UI thread. */
    fun replaceWith(loaded: MemoryDocument) {
        // Clear first: ids repeat between files, so the selection would otherwise survive.
        viewport.selection.setSelected(emptyList())
        viewport.selection.setHighlighted(emptyList())
        doc.replaceAllFrom(loaded)
        undo.clear()
        viewport.fitAll()
    }

    /** New: an empty document with v69's default doc fields. */
    fun newDocument() = replaceWith(MemoryDocument())
}
