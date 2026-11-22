package com.solidsketchapp.core.view

import kotlin.math.hypot

/**
 * Touch gestures without Android types (unit-tested): one finger orbits, two fingers pan
 * and pinch-zoom, a short tap picks, a double tap fits all.
 */
class GestureTracker(private val slopPx: Float, private val listener: Listener) {
    interface Listener {
        fun orbit(dx: Float, dy: Float)
        fun pan(dx: Float, dy: Float)
        /** factor < 1 moves closer (fingers spread). */
        fun zoom(factor: Double)
        fun tap(x: Float, y: Float)
        fun doubleTap(x: Float, y: Float)
    }

    enum class Action { DOWN, POINTER_DOWN, MOVE, POINTER_UP, UP, CANCEL }
    data class P(val x: Float, val y: Float)

    private var last: List<P> = emptyList()
    private var start = P(0f, 0f)
    private var startMs = 0L
    private var dragging = false
    private var multi = false
    private var lastTap: Triple<Float, Float, Long>? = null

    /** [pointers] are the pointers down after the event (for POINTER_UP: the ones that stay). */
    fun onEvent(action: Action, pointers: List<P>, timeMs: Long) {
        when (action) {
            Action.DOWN -> { start = pointers[0]; startMs = timeMs; dragging = false; multi = false; last = pointers }
            Action.POINTER_DOWN -> { multi = true; last = pointers }
            Action.MOVE -> {
                if (pointers.size == 1 && !multi) {
                    val p = pointers[0]
                    if (!dragging && hypot(p.x - start.x, p.y - start.y) > slopPx) dragging = true
                    if (dragging && last.size == 1) listener.orbit(p.x - last[0].x, p.y - last[0].y)
                } else if (pointers.size >= 2 && last.size >= 2) {
                    val c0 = centroid(last); val c1 = centroid(pointers)
                    listener.pan(c1.x - c0.x, c1.y - c0.y)
                    val s0 = span(last); val s1 = span(pointers)
                    if (s0 > 1f && s1 > 1f) listener.zoom((s0 / s1).toDouble())
                }
                last = pointers
            }
            Action.POINTER_UP -> last = pointers
            Action.UP -> {
                val p = pointers.firstOrNull() ?: start
                if (!dragging && !multi && timeMs - startMs < TAP_MS) {
                    val prev = lastTap
                    if (prev != null && timeMs - prev.third < DOUBLE_TAP_MS && hypot(p.x - prev.first, p.y - prev.second) < slopPx * 3) {
                        listener.doubleTap(p.x, p.y); lastTap = null
                    } else {
                        listener.tap(p.x, p.y); lastTap = Triple(p.x, p.y, timeMs)
                    }
                }
                last = emptyList()
            }
            Action.CANCEL -> last = emptyList()
        }
    }

    private fun centroid(ps: List<P>) = P(ps.take(2).map { it.x }.average().toFloat(), ps.take(2).map { it.y }.average().toFloat())
    private fun span(ps: List<P>) = hypot(ps[0].x - ps[1].x, ps[0].y - ps[1].y)

    companion object {
        const val TAP_MS = 400L
        const val DOUBLE_TAP_MS = 300L
    }
}
