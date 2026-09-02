package com.isuara.app.avatar.engine

import com.isuara.app.avatar.model.Vec3

/**
 * The fully solved 3D geometric state of the avatar ready for rendering in a frame.
 */
data class AvatarPoseState(
    val headPosition: Vec3 = Vec3(0f, 1.34f, 0f),
    val neckStart: Vec3 = Vec3(0f, 1.15f, 0f),
    val neckEnd: Vec3 = Vec3(0f, 1.29f, 0f),
    val torsoPosition: Vec3 = Vec3(0f, 0.93f, 0f),

    val leftShoulder: Vec3 = Vec3(0.19f, 1.15f, 0f),
    val leftElbow: Vec3 = Vec3(0.24f, 0.90f, 0f),
    val leftWrist: Vec3 = Vec3(0.23f, 0.72f, 0.12f),

    val rightShoulder: Vec3 = Vec3(-0.19f, 1.15f, 0f),
    val rightElbow: Vec3 = Vec3(-0.24f, 0.90f, 0f),
    val rightWrist: Vec3 = Vec3(-0.23f, 0.72f, 0.12f),

    val leftPalmPos: Vec3? = null,
    val leftPalmLookAt: Vec3? = null,
    val rightPalmPos: Vec3? = null,
    val rightPalmLookAt: Vec3? = null,

    val leftHandPoints: List<Vec3>? = null,
    val rightHandPoints: List<Vec3>? = null
)
