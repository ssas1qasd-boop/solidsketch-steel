package com.solidsketchapp.core.view

import com.solidsketchapp.core.api.UndoApi

/**
 * Snapshot undo stack. [capture] returns the document state; [restore] puts it back.
 * Nested groups merge into the outermost one. A group that changes nothing adds no step.
 */
class UndoStack<S>(
    private val capture: () -> S,
    private val restore: (S) -> Unit,
    private val limit: Int = 200,
) : UndoApi {
    private data class Step<S>(val label: String, val before: S, val after: S)

    private val undo = ArrayDeque<Step<S>>()
    private val redo = ArrayDeque<Step<S>>()
    private var depth = 0

    override fun <T> group(label: String, block: () -> T): T {
        if (depth > 0) return block()
        val before = capture()
        depth++
        try {
            return block()
        } finally {
            depth--
            val after = capture()
            if (after != before) {
                undo.addLast(Step(label, before, after))
                if (undo.size > limit) undo.removeFirst()
                redo.clear()
            }
        }
    }

    override fun canUndo() = undo.isNotEmpty()
    override fun canRedo() = redo.isNotEmpty()

    override fun undo() {
        val s = undo.removeLastOrNull() ?: return
        restore(s.before)
        redo.addLast(s)
    }

    override fun redo() {
        val s = redo.removeLastOrNull() ?: return
        restore(s.after)
        undo.addLast(s)
    }

    /** Forgets every step (after opening a file). */
    fun clear() { undo.clear(); redo.clear() }

    /** Label of the step [undo] would revert, for the UI. */
    fun undoLabel(): String? = undo.lastOrNull()?.label
}
