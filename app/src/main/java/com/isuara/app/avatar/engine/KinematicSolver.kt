package com.isuara.app.avatar.engine

import com.isuara.app.avatar.model.Vec3

object KinematicSolver {

    // Humanoid limb lengths (in meters)
    const val UPPER_ARM_LENGTH = 0.26f
    const val FOREARM_LENGTH = 0.24f

    // Anatomical constraints matching SignAvatar-Muba
    const val FINGER_SCALE = 0.72f
    const val Y_MAX_HEAD = 1.58f
    const val Y_MIN_WAIST = 0.62f

    // Standard hand bone connections for 21 landmarks:
    // [0,1..4] Thumb, [0,5..8] Index, [0,9..12] Middle, [0,13..16] Ring, [0,17..20] Pinky
    val HAND_BONES = arrayOf(
        intArrayOf(0, 1), intArrayOf(1, 2), intArrayOf(2, 3), intArrayOf(3, 4),       // Thumb
        intArrayOf(0, 5), intArrayOf(5, 6), intArrayOf(6, 7), intArrayOf(7, 8),       // Index
        intArrayOf(0, 9), intArrayOf(9, 10), intArrayOf(10, 11), intArrayOf(11, 12),  // Middle
        intArrayOf(0, 13), intArrayOf(13, 14), intArrayOf(14, 15), intArrayOf(15, 16),// Ring
        intArrayOf(0, 17), intArrayOf(17, 18), intArrayOf(18, 19), intArrayOf(19, 20) // Pinky
    )

    /**
     * Solves natural elbow position using inverse kinematics with forward and outward arc flare.
     * @param shoulder 3D position of shoulder
     * @param wrist 3D position of wrist
     * @param isLeft true for left arm (+X), false for right arm (-X)
     */
    fun solveNaturalElbow(shoulder: Vec3, wrist: Vec3, isLeft: Boolean): Vec3 {
        // Midpoint between Shoulder and Wrist
        val mid = (shoulder + wrist) * 0.5f

        // Natural outward, downward, and forward flare
        val sign = if (isLeft) 1f else -1f
        val flare = Vec3(sign * 0.12f, -0.05f, 0.10f)
        val targetElbow = mid + flare

        // Constrain Upper Arm to exact length L1
        val dirUpper = (targetElbow - shoulder).normalize()
        return shoulder + (dirUpper * UPPER_ARM_LENGTH)
    }

    /**
     * Scales finger spread towards wrist (preserving Point 0 wrist anchor) and clamps within natural signing boundaries.
     */
    fun scaleAndClampHandKeypoints(rawPoints: List<Vec3>): List<Vec3> {
        if (rawPoints.size != 21) return rawPoints

        val wrist = rawPoints[0]
        val scaled = ArrayList<Vec3>(21)
        scaled.add(wrist)

        for (i in 1 until 21) {
            val pt = rawPoints[i]
            val scaledPt = wrist + ((pt - wrist) * FINGER_SCALE)
            val clampedY = scaledPt.y.coerceIn(Y_MIN_WAIST, Y_MAX_HEAD)
            scaled.add(Vec3(scaledPt.x, clampedY, scaledPt.z))
        }

        return scaled
    }
}
