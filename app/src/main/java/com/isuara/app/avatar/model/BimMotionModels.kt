package com.isuara.app.avatar.model

import kotlin.math.sqrt

/**
 * Immutable 3D vector representing Cartesian coordinates (X: left/right, Y: up/down, Z: forward/back in meters).
 */
data class Vec3(
    val x: Float,
    val y: Float,
    val z: Float
) {
    operator fun plus(other: Vec3): Vec3 = Vec3(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vec3): Vec3 = Vec3(x - other.x, y - other.y, z - other.z)
    operator fun times(scalar: Float): Vec3 = Vec3(x * scalar, y * scalar, z * scalar)
    operator fun div(scalar: Float): Vec3 = if (scalar != 0f) Vec3(x / scalar, y / scalar, z / scalar) else Vec3(0f, 0f, 0f)

    fun length(): Float = sqrt(x * x + y * y + z * z)

    fun normalize(): Vec3 {
        val len = length()
        return if (len > 0.00001f) this / len else Vec3(0f, 1f, 0f)
    }

    fun distanceTo(other: Vec3): Float = (this - other).length()

    fun lerp(target: Vec3, alpha: Float): Vec3 {
        val clampedAlpha = alpha.coerceIn(0f, 1f)
        return Vec3(
            x + (target.x - x) * clampedAlpha,
            y + (target.y - y) * clampedAlpha,
            z + (target.z - z) * clampedAlpha
        )
    }

    companion object {
        val ZERO = Vec3(0f, 0f, 0f)
        val UP = Vec3(0f, 1f, 0f)

        fun fromArray(arr: FloatArray?): Vec3? {
            if (arr == null || arr.size < 3) return null
            return Vec3(arr[0], arr[1], arr[2])
        }

        fun fromList(list: List<Float>?): Vec3? {
            if (list == null || list.size < 3) return null
            return Vec3(list[0], list[1], list[2])
        }
    }
}

/**
 * 21-point Hand joint landmarks for a single hand side.
 * Point 0: Wrist, 1-4: Thumb, 5-8: Index, 9-12: Middle, 13-16: Ring, 17-20: Pinky.
 */
data class HandJoints(
    val active: Boolean = true,
    val points: List<Vec3> = emptyList()
) {
    val wrist: Vec3? get() = points.getOrNull(0)
    val middleMcp: Vec3? get() = points.getOrNull(9)
}

/**
 * Body pose joints for a single frame.
 */
data class PoseJoints(
    val nose: Vec3? = null,
    val leftShoulder: Vec3? = null,
    val rightShoulder: Vec3? = null,
    val leftElbow: Vec3? = null,
    val rightElbow: Vec3? = null,
    val leftWrist: Vec3? = null,
    val rightWrist: Vec3? = null
)

/**
 * A single timestamped motion capture frame.
 */
data class BimFrame(
    val frame: Int,
    val time: Float,
    val pose: PoseJoints = PoseJoints(),
    val leftHand: HandJoints? = null,
    val rightHand: HandJoints? = null
)

/**
 * Complete 3D kinematic motion clip for a BIM vocabulary word or continuous sentence.
 */
data class BimMotion(
    val word: String,
    val fps: Float = 50f,
    val numFrames: Int = 0,
    val duration: Float = 0f,
    val frames: List<BimFrame> = emptyList()
)
