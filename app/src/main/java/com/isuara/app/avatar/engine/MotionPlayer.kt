package com.isuara.app.avatar.engine

import com.isuara.app.avatar.model.BimFrame
import com.isuara.app.avatar.model.BimMotion
import com.isuara.app.avatar.model.Vec3
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

class MotionPlayer {

    var currentMotion: BimMotion? = null
        private set

    var isPlaying: Boolean = false
    var playbackSpeed: Float = 1.0f
    var currentTimeSec: Float = 0.0f
        private set
    var isLooping: Boolean = true

    private var currentPoseState: AvatarPoseState = AvatarPoseState()

    fun setMotion(motion: BimMotion, autoPlay: Boolean = false) {
        currentMotion = motion
        currentTimeSec = 0.0f
        isPlaying = autoPlay
        currentPoseState = evaluatePose(0.0f)
    }

    fun seekTo(timeSec: Float) {
        val duration = currentMotion?.duration ?: 0f
        currentTimeSec = if (duration > 0f) timeSec.coerceIn(0f, duration) else 0f
        currentPoseState = evaluatePose(currentTimeSec)
    }

    fun play() {
        isPlaying = true
    }

    fun pause() {
        isPlaying = false
    }

    fun togglePlayPause() {
        isPlaying = !isPlaying
    }

    fun advanceTime(deltaSec: Float): AvatarPoseState {
        val motion = currentMotion ?: return currentPoseState
        val duration = motion.duration
        if (duration <= 0f) return currentPoseState

        if (isPlaying) {
            currentTimeSec += deltaSec * playbackSpeed
            if (currentTimeSec >= duration) {
                if (isLooping) {
                    currentTimeSec %= duration
                } else {
                    currentTimeSec = duration
                    isPlaying = false
                }
            }
        }

        currentPoseState = evaluatePose(currentTimeSec)
        return currentPoseState
    }

    fun getCurrentPose(): AvatarPoseState = currentPoseState

    /**
     * Evaluates the 3D kinematic avatar pose at [timeSec] with continuous 60 FPS sub-frame LERP.
     */
    fun evaluatePose(timeSec: Float): AvatarPoseState {
        val motion = currentMotion ?: return AvatarPoseState()
        val frames = motion.frames
        if (frames.isEmpty()) return AvatarPoseState()

        val fps = if (motion.fps > 0f) motion.fps else 50.0f
        val exactFrame = timeSec * fps
        val f0Index = min(max(floor(exactFrame).toInt(), 0), frames.size - 1)
        val f1Index = min(f0Index + 1, frames.size - 1)
        val alpha = exactFrame - f0Index.toFloat()

        val f0 = frames[f0Index]
        val f1 = frames[f1Index]

        // 1. Interpolate Pose
        val pNose0 = f0.pose.nose
        val pNose1 = f1.pose.nose ?: pNose0
        val pNose = if (pNose0 != null && pNose1 != null) pNose0.lerp(pNose1, alpha) else pNose0

        val pLSh = Vec3(0.19f, 1.15f, 0.0f)
        val pRSh = Vec3(-0.19f, 1.15f, 0.0f)
        val midSh = Vec3(0f, 1.15f, 0.0f)

        // Head & Neck (Anatomically centered, attached at base of skull)
        var headX = 0f
        if (pNose != null) {
            headX = (pNose.x - midSh.x) * 0.35f
        }
        val headPos = Vec3(headX, 1.34f, 0.0f)
        val neckStart = Vec3(0f, 1.15f, -0.015f)
        val neckEnd = Vec3(headX, 1.22f, -0.015f)
        val torsoPos = Vec3(0f, 0.93f, 0f)

        // 2. Interpolate Hands
        val rhPoints = interpolateHand(f0, f1, alpha, isLeft = false)
        val lhPoints = interpolateHand(f0, f1, alpha, isLeft = true)

        // 3. Solve Right Arm
        val pRWr = rhPoints?.getOrNull(0) ?: Vec3(-0.23f, 0.72f, 0.12f)
        val pREl = KinematicSolver.solveNaturalElbow(pRSh, pRWr, isLeft = false)

        var rightPalmPos: Vec3? = null
        var rightPalmLookAt: Vec3? = null
        if (rhPoints != null && rhPoints.size >= 10) {
            val w = rhPoints[0]
            val m = rhPoints[9]
            rightPalmPos = (w + m) * 0.5f
            rightPalmLookAt = m
        }

        // 4. Solve Left Arm
        val pLWr = lhPoints?.getOrNull(0) ?: Vec3(0.23f, 0.72f, 0.12f)
        val pLEl = KinematicSolver.solveNaturalElbow(pLSh, pLWr, isLeft = true)

        var leftPalmPos: Vec3? = null
        var leftPalmLookAt: Vec3? = null
        if (lhPoints != null && lhPoints.size >= 10) {
            val w = lhPoints[0]
            val m = lhPoints[9]
            leftPalmPos = (w + m) * 0.5f
            leftPalmLookAt = m
        }

        return AvatarPoseState(
            headPosition = headPos,
            neckStart = neckStart,
            neckEnd = neckEnd,
            torsoPosition = torsoPos,
            leftShoulder = pLSh,
            leftElbow = pLEl,
            leftWrist = pLWr,
            rightShoulder = pRSh,
            rightElbow = pREl,
            rightWrist = pRWr,
            leftPalmPos = leftPalmPos,
            leftPalmLookAt = leftPalmLookAt,
            rightPalmPos = rightPalmPos,
            rightPalmLookAt = rightPalmLookAt,
            leftHandPoints = lhPoints,
            rightHandPoints = rhPoints
        )
    }

    private fun interpolateHand(f0: BimFrame, f1: BimFrame, alpha: Float, isLeft: Boolean): List<Vec3>? {
        val h0 = if (isLeft) f0.leftHand else f0.rightHand
        val h1 = (if (isLeft) f1.leftHand else f1.rightHand) ?: h0

        val pts0 = h0?.points
        val pts1 = h1?.points ?: pts0

        if (pts0.isNullOrEmpty() && pts1.isNullOrEmpty()) return null
        val p0 = pts0 ?: pts1 ?: return null
        val p1 = pts1 ?: pts0 ?: return null

        if (p0.size != 21 || p1.size != 21) return null

        val rawPoints = ArrayList<Vec3>(21)
        for (i in 0 until 21) {
            rawPoints.add(p0[i].lerp(p1[i], alpha))
        }

        return KinematicSolver.scaleAndClampHandKeypoints(rawPoints)
    }
}
