package com.solidsketchapp.core.view

import android.annotation.SuppressLint
import android.content.Context
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import android.view.ViewConfiguration

/** The 3D view: GLSurfaceView (ES 3.0) driven by a [ViewportController]. */
@SuppressLint("ViewConstructor")
class ViewportView(context: Context, private val controller: ViewportController) : GLSurfaceView(context) {
    private val gestures = GestureTracker(ViewConfiguration.get(context).scaledTouchSlop.toFloat(), controller)

    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(8, 8, 8, 8, 24, 0)
        preserveEGLContextOnPause = true
        setRenderer(controller.renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
        controller.requestRender = ::requestRender
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        controller.onSize(w, h)
    }

    override fun onDetachedFromWindow() {
        if (controller.requestRender == ::requestRender) controller.requestRender = null
        super.onDetachedFromWindow()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val action = when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> GestureTracker.Action.DOWN
            MotionEvent.ACTION_POINTER_DOWN -> GestureTracker.Action.POINTER_DOWN
            MotionEvent.ACTION_MOVE -> GestureTracker.Action.MOVE
            MotionEvent.ACTION_POINTER_UP -> GestureTracker.Action.POINTER_UP
            MotionEvent.ACTION_UP -> GestureTracker.Action.UP
            MotionEvent.ACTION_CANCEL -> GestureTracker.Action.CANCEL
            else -> return true
        }
        val skip = if (e.actionMasked == MotionEvent.ACTION_POINTER_UP) e.actionIndex else -1
        val pts = (0 until e.pointerCount).filter { it != skip }.map { GestureTracker.P(e.getX(it), e.getY(it)) }
        gestures.onEvent(action, pts, e.eventTime)
        return true
    }
}
