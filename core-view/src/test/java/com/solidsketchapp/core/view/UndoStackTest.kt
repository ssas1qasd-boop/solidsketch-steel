package com.solidsketchapp.core.view

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UndoStackTest {
    private var doc = listOf<String>()
    private val stack = UndoStack(capture = { doc }, restore = { doc = it })

    @Test
    fun groupIsOneStep() {
        stack.group("add two") {
            doc = doc + "a"
            stack.group("inner") { doc = doc + "b" }
        }
        assertEquals(listOf("a", "b"), doc)
        assertEquals("add two", stack.undoLabel())
        stack.undo()
        assertEquals(emptyList<String>(), doc)
        assertFalse(stack.canUndo())
        stack.redo()
        assertEquals(listOf("a", "b"), doc)
    }

    @Test
    fun noChangeNoStep() {
        stack.group("nothing") {}
        assertFalse(stack.canUndo())
    }

    @Test
    fun newChangeClearsRedo() {
        stack.group("a") { doc = doc + "a" }
        stack.undo()
        assertTrue(stack.canRedo())
        stack.group("b") { doc = doc + "b" }
        assertFalse(stack.canRedo())
    }

    @Test
    fun clearForgetsEverything() {
        stack.group("a") { doc = doc + "a" }
        stack.undo()
        stack.clear()
        assertFalse(stack.canUndo()); assertFalse(stack.canRedo())
    }
}
