package com.isuara.app.avatar

import com.isuara.app.avatar.engine.KinematicSolver
import com.isuara.app.avatar.engine.MotionPlayer
import com.isuara.app.avatar.model.BimFrame
import com.isuara.app.avatar.model.BimMotion
import com.isuara.app.avatar.model.HandJoints
import com.isuara.app.avatar.model.PoseJoints
import com.isuara.app.avatar.model.Vec3
import com.isuara.app.avatar.parser.MotionParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class AvatarKinematicsTest {

    @Test
    fun testVec3Operations() {
        val v1 = Vec3(1f, 2f, 3f)
        val v2 = Vec3(4f, 6f, 8f)

        val sum = v1 + v2
        assertEquals(5f, sum.x, 0.001f)
        assertEquals(8f, sum.y, 0.001f)
        assertEquals(11f, sum.z, 0.001f)

        val diff = v2 - v1
        assertEquals(3f, diff.x, 0.001f)
        assertEquals(4f, diff.y, 0.001f)
        assertEquals(5f, diff.z, 0.001f)

        val dist = v1.distanceTo(v2)
        assertTrue(dist > 0f)

        val lerped = v1.lerp(v2, 0.5f)
        assertEquals(2.5f, lerped.x, 0.001f)
        assertEquals(4.0f, lerped.y, 0.001f)
        assertEquals(5.5f, lerped.z, 0.001f)
    }

    @Test
    fun testKinematicSolverElbowConstraint() {
        val shoulder = Vec3(0.19f, 1.15f, 0f)
        val wrist = Vec3(0.23f, 0.72f, 0.12f)

        val leftElbow = KinematicSolver.solveNaturalElbow(shoulder, wrist, isLeft = true)
        val upperArmDist = shoulder.distanceTo(leftElbow)

        // Upper arm distance must strictly equal KinematicSolver.UPPER_ARM_LENGTH (0.26m)
        assertEquals(KinematicSolver.UPPER_ARM_LENGTH, upperArmDist, 0.001f)
        assertTrue("Elbow should have forward flare (Z > 0)", leftElbow.z > shoulder.z)
    }

    @Test
    fun testMotionParserJson() {
        val sampleJson = """
            {
              "word": "Test Sign",
              "fps": 50.0,
              "num_frames": 2,
              "duration": 0.04,
              "frames": [
                {
                  "frame": 0,
                  "time": 0.0,
                  "pose": {
                    "nose": [-0.01, 1.55, 1.06],
                    "leftShoulder": [0.18, 1.14, 0.41],
                    "rightShoulder": [-0.18, 1.15, 0.42]
                  },
                  "leftHand": {
                    "active": true,
                    "points": [
                      [0.23, 0.72, 0.12], [0.25, 0.69, 0.13], [0.26, 0.67, 0.14], [0.27, 0.65, 0.14], [0.28, 0.63, 0.15],
                      [0.25, 0.68, 0.13], [0.25, 0.65, 0.13], [0.25, 0.62, 0.13], [0.26, 0.60, 0.14],
                      [0.23, 0.68, 0.12], [0.23, 0.65, 0.13], [0.24, 0.61, 0.13], [0.24, 0.59, 0.13],
                      [0.22, 0.68, 0.12], [0.22, 0.65, 0.13], [0.21, 0.62, 0.13], [0.21, 0.60, 0.13],
                      [0.20, 0.69, 0.12], [0.20, 0.66, 0.12], [0.20, 0.64, 0.13], [0.20, 0.61, 0.13]
                    ]
                  }
                },
                {
                  "frame": 1,
                  "time": 0.02,
                  "pose": {
                    "nose": [-0.02, 1.56, 1.07],
                    "leftShoulder": [0.19, 1.15, 0.42],
                    "rightShoulder": [-0.19, 1.16, 0.43]
                  },
                  "leftHand": {
                    "active": true,
                    "points": [
                      [0.24, 0.73, 0.13], [0.26, 0.70, 0.14], [0.27, 0.68, 0.15], [0.28, 0.66, 0.15], [0.29, 0.64, 0.16],
                      [0.26, 0.69, 0.14], [0.26, 0.66, 0.14], [0.26, 0.63, 0.14], [0.27, 0.61, 0.15],
                      [0.24, 0.69, 0.13], [0.24, 0.66, 0.14], [0.25, 0.62, 0.14], [0.25, 0.60, 0.14],
                      [0.23, 0.69, 0.13], [0.23, 0.66, 0.14], [0.22, 0.63, 0.14], [0.22, 0.61, 0.14],
                      [0.21, 0.70, 0.13], [0.21, 0.67, 0.13], [0.21, 0.65, 0.14], [0.21, 0.62, 0.14]
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        val motion = MotionParser.parseJson(sampleJson)
        assertEquals("Test Sign", motion.word)
        assertEquals(50.0f, motion.fps, 0.01f)
        assertEquals(2, motion.numFrames)
        assertEquals(0.04f, motion.duration, 0.001f)

        assertEquals(2, motion.frames.size)
        val f0 = motion.frames[0]
        assertNotNull(f0.pose.nose)
        assertEquals(-0.01f, f0.pose.nose!!.x, 0.001f)
        assertNotNull(f0.leftHand)
        assertEquals(21, f0.leftHand!!.points.size)
    }

    @Test
    fun testMotionPlayerSubframeLerp() {
        val f0Points = (0 until 21).map { Vec3(0f, 1f, 0f) }
        val f1Points = (0 until 21).map { Vec3(1f, 1f, 0f) }

        val f0 = BimFrame(0, 0.0f, PoseJoints(nose = Vec3(0f, 1.5f, 0f)), leftHand = HandJoints(points = f0Points))
        val f1 = BimFrame(1, 0.02f, PoseJoints(nose = Vec3(1f, 1.5f, 0f)), leftHand = HandJoints(points = f1Points))

        val motion = BimMotion(
            word = "Lerp Test",
            fps = 50f,
            numFrames = 2,
            duration = 0.02f,
            frames = listOf(f0, f1)
        )

        val player = MotionPlayer()
        player.setMotion(motion)

        // Evaluate at midpoint t = 0.01s (alpha = 0.5)
        val midPose = player.evaluatePose(0.01f)
        assertNotNull(midPose.leftHandPoints)
        assertEquals(21, midPose.leftHandPoints!!.size)
    }

    @Test
    fun testMotionPlayerPlaysOnceWithoutLooping() {
        val f0 = BimFrame(0, 0.0f, PoseJoints(nose = Vec3(0f, 1.5f, 0f)))
        val f1 = BimFrame(1, 0.5f, PoseJoints(nose = Vec3(1f, 1.5f, 0f)))

        val motion = BimMotion(
            word = "Once Test",
            fps = 50f,
            numFrames = 2,
            duration = 0.5f,
            frames = listOf(f0, f1)
        )

        val player = MotionPlayer()
        player.setMotion(motion, autoPlay = true)

        // isLooping must be false by default
        org.junit.Assert.assertFalse("Looping must default to false", player.isLooping)
        assertTrue(player.isPlaying)

        // Advance 0.2s -> still playing
        player.advanceTime(0.2f)
        assertTrue(player.isPlaying)
        assertEquals(0.2f, player.currentTimeSec, 0.001f)

        // Advance past duration (0.4s more -> total 0.6s >= 0.5s)
        player.advanceTime(0.4f)
        org.junit.Assert.assertFalse("Player must stop playing after completion", player.isPlaying)
        assertEquals("Current time must clamp at duration", 0.5f, player.currentTimeSec, 0.001f)
    }
}

