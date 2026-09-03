package com.isuara.app.avatar.gl

import android.annotation.SuppressLint
import android.content.Context
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import com.isuara.app.avatar.engine.MotionPlayer

@SuppressLint("ViewConstructor")
class AvatarGLSurfaceView(
    context: Context,
    val motionPlayer: MotionPlayer
) : GLSurfaceView(context) {

    val renderer: AvatarGLRenderer
    private val scaleDetector: ScaleGestureDetector

    init {
        setEGLContextClientVersion(2)
        // 8 bits per channel, 16-bit depth
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)

        renderer = AvatarGLRenderer(motionPlayer)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY

        scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                renderer.orbitController.onScale(detector.scaleFactor)
                return true
            }
        })
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val handled = renderer.orbitController.onTouchEvent(event, scaleDetector)
        return if (handled) true else super.onTouchEvent(event)
    }
}
