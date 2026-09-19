package app.folio.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UndoStackTest {

    @Test
    fun undoThenRedoReturnsTheSameEdit() {
        val stack = UndoStack<String>()
        stack.push("a")
        stack.push("b")
        assertEquals("b", stack.undo())
        assertTrue(stack.canRedo)
        assertEquals("b", stack.redo())
        assertFalse(stack.canRedo)
    }

    @Test
    fun aNewEditClearsRedo() {
        val stack = UndoStack<String>()
        stack.push("a")
        stack.undo()
        stack.push("c")
        assertFalse(stack.canRedo)
        assertNull(stack.redo())
    }

    @Test
    fun emptyStackReturnsNull() {
        val stack = UndoStack<String>()
        assertFalse(stack.canUndo)
        assertNull(stack.undo())
    }

    @Test
    fun oldestEditsFallOffAtTheLimit() {
        val stack = UndoStack<Int>(limit = 2)
        stack.push(1)
        stack.push(2)
        stack.push(3)
        assertEquals(3, stack.undo())
        assertEquals(2, stack.undo())
        assertNull(stack.undo())
    }
}
