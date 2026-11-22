package com.solidsketchapp.core.view

import com.solidsketchapp.core.view.GestureTracker.Action
import com.solidsketchapp.core.view.GestureTracker.P
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureTrackerTest {
    private val log = mutableListOf<String>()
    private var orbit = 0f to 0f
    private var zoom = 1.0
    private val g = GestureTracker(10f, object : GestureTracker.Listener {
        override fun orbit(dx: Float, dy: Float) { orbit = (orbit.first + dx) to (orbit.second + dy); log += "orbit" }
        override fun pan(dx: Float, dy: Float) { log += "pan" }
        override fun zoom(factor: Double) { zoom *= factor; log += "zoom" }
        override fun tap(x: Float, y: Float) { log += "tap" }
        override fun doubleTap(x: Float, y: Float) { log += "double" }
    })

    @Test
    fun tapThenDoubleTap() {
        g.onEvent(Action.DOWN, listOf(P(100f, 100f)), 0); g.onEvent(Action.UP, listOf(P(101f, 100f)), 80)
        g.onEvent(Action.DOWN, listOf(P(102f, 101f)), 200); g.onEvent(Action.UP, listOf(P(102f, 101f)), 260)
        assertEquals(listOf("tap", "double"), log)
    }

    @Test
    fun dragOrbitsAndIsNotATap() {
        g.onEvent(Action.DOWN, listOf(P(0f, 0f)), 0)
        g.onEvent(Action.MOVE, listOf(P(5f, 0f)), 10)     // inside the slop: nothing yet
        g.onEvent(Action.MOVE, listOf(P(50f, 20f)), 20)
        g.onEvent(Action.MOVE, listOf(P(80f, 20f)), 30)
        g.onEvent(Action.UP, listOf(P(80f, 20f)), 40)
        assertEquals(75f, orbit.first, 1e-4f); assertEquals(20f, orbit.second, 1e-4f)
        assertTrue("tap" !in log)
    }

    @Test
    fun pinchOutZoomsIn() {
        g.onEvent(Action.DOWN, listOf(P(100f, 100f)), 0)
        g.onEvent(Action.POINTER_DOWN, listOf(P(100f, 100f), P(200f, 100f)), 5)
        g.onEvent(Action.MOVE, listOf(P(50f, 100f), P(250f, 100f)), 20)
        g.onEvent(Action.POINTER_UP, listOf(P(50f, 100f)), 30)
        g.onEvent(Action.MOVE, listOf(P(300f, 300f)), 40)   // remaining finger must not orbit
        g.onEvent(Action.UP, listOf(P(300f, 300f)), 50)
        assertEquals(0.5, zoom, 1e-9)
        assertTrue("orbit" !in log && "tap" !in log && "pan" in log)
    }
}
