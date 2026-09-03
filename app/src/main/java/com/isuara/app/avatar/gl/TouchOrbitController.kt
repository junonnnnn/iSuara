package com.isuara.app.avatar.gl

import android.view.MotionEvent
import android.view.ScaleGestureDetector
import kotlin.math.cos
import kotlin.math.sin

class TouchOrbitController(
    var azimuthDeg: Float = 0f,
    var elevationDeg: Float = 5f,
    var distance: Float = 1.9f,
    var targetX: Float = 0f,
    var targetY: Float = 1.08f,
    var targetZ: Float = 0f
) {
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isDragging = false

    fun onTouchEvent(event: MotionEvent, scaleDetector: ScaleGestureDetector?): Boolean {
        scaleDetector?.onTouchEvent(event)
        if (scaleDetector != null && scaleDetector.isInProgress) {
            return true
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                isDragging = true
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging && event.pointerCount == 1) {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY

                    azimuthDeg -= dx * 0.4f
                    elevationDeg += dy * 0.3f
                    elevationDeg = elevationDeg.coerceIn(-60f, 60f)

                    lastTouchX = event.x
                    lastTouchY = event.y
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                return true
            }
        }
        return false
    }

    fun onScale(scaleFactor: Float) {
        distance /= scaleFactor
        distance = distance.coerceIn(0.8f, 4.5f)
    }

    // Camera presets
    fun setPresetFront() {
        azimuthDeg = 0f
        elevationDeg = 5f
        distance = 1.9f
        targetX = 0f
        targetY = 1.08f
        targetZ = 0f
    }

    fun setPresetSide45() {
        azimuthDeg = 45f
        elevationDeg = 5f
        distance = 1.9f
        targetX = 0f
        targetY = 1.08f
        targetZ = 0f
    }

    fun setPresetProfile90() {
        azimuthDeg = 90f
        elevationDeg = 5f
        distance = 1.9f
        targetX = 0f
        targetY = 1.08f
        targetZ = 0f
    }

    fun setPresetHandsZoom() {
        azimuthDeg = 15f
        elevationDeg = -10f
        distance = 1.25f
        targetX = 0f
        targetY = 0.85f
        targetZ = 0.1f
    }

    fun computeEyePosition(): FloatArray {
        val azRad = Math.toRadians(azimuthDeg.toDouble()).toFloat()
        val elRad = Math.toRadians(elevationDeg.toDouble()).toFloat()

        val eyeX = targetX + distance * cos(elRad) * sin(azRad)
        val eyeY = targetY + distance * sin(elRad)
        val eyeZ = targetZ + distance * cos(elRad) * cos(azRad)

        return floatArrayOf(eyeX, eyeY, eyeZ)
    }
}
