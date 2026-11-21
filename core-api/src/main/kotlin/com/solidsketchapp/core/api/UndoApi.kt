package com.solidsketchapp.core.api

/** Undo stack. Steel groups the changes of one command into one step. */
interface UndoApi {
    /** Runs [block]; every document change inside it becomes one undo step named [label]. */
    fun <T> group(label: String, block: () -> T): T
    fun canUndo(): Boolean
    fun canRedo(): Boolean
    fun undo()
    fun redo()
}
