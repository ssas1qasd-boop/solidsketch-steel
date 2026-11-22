package com.solidsketchapp.core.io

import com.solidsketchapp.core.api.Mesh
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class MemoryDocumentTest {
    private val tri = Mesh(floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f), intArrayOf(0, 1, 2))

    @Test
    fun addReplaceRemoveNotify() {
        val d = MemoryDocument()
        var n = 0
        d.addListener { n++ }
        val a = d.addBody("""{"name":"A"}""", tri)
        val b = d.addBody("""{"name":"B"}""", null)
        assertEquals(listOf(a, b), d.bodyIds())
        assertSame(tri, d.bodyMesh(a))
        d.replaceBody(b, """{"name":"B2"}""", tri)
        assertEquals("""{"name":"B2"}""", d.bodyJson(b))
        d.removeBody(a)
        assertNull(d.bodyJson(a))
        d.setField("stGrid", """{"x":"0 4*13195"}""")
        assertEquals(5, n)
    }

    @Test
    fun snapshotRestoreIncludesIdCounter() {
        val d = MemoryDocument()
        val a = d.addBody("{}", null)
        val s = d.snapshot()
        val b = d.addBody("{}", null)
        d.setField("x", "1")
        d.restore(s)
        assertEquals(listOf(a), d.bodyIds())
        assertNull(d.field("x"))
        assertEquals(b, d.addBody("{}", null)) // same id is handed out again after undo
    }

    @Test
    fun replaceAllFromCopiesAndNotifiesOnce() {
        val src = MemoryDocument(); val a = src.addBody("{}", tri); src.setField("stGrid", "{}")
        val dst = MemoryDocument(); dst.addBody("{\"old\":1}", null)
        var n = 0; dst.addListener { n++ }
        dst.replaceAllFrom(src)
        assertEquals(listOf(a), dst.bodyIds()); assertSame(tri, dst.bodyMesh(a)); assertEquals("{}", dst.field("stGrid"))
        assertEquals(1, n)
        src.removeBody(a); assertEquals(listOf(a), dst.bodyIds())   // independent maps
    }
}
