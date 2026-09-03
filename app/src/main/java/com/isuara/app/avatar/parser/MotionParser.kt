package com.isuara.app.avatar.parser

import com.isuara.app.avatar.model.BimFrame
import com.isuara.app.avatar.model.BimMotion
import com.isuara.app.avatar.model.HandJoints
import com.isuara.app.avatar.model.PoseJoints
import com.isuara.app.avatar.model.Vec3
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream

object MotionParser {

    /**
     * Parses a BIM Motion JSON string into a [BimMotion] object.
     */
    fun parseJson(jsonString: String): BimMotion {
        val root = JSONObject(jsonString)
        val word = root.optString("word", "Unknown")
        val fps = root.optDouble("fps", 50.0).toFloat()
        val numFrames = root.optInt("num_frames", 0)
        val duration = root.optDouble("duration", 0.0).toFloat()

        val framesArray = root.optJSONArray("frames") ?: JSONArray()
        val framesList = ArrayList<BimFrame>(framesArray.length())

        for (i in 0 until framesArray.length()) {
            val frameObj = framesArray.optJSONObject(i) ?: continue
            val frameIndex = frameObj.optInt("frame", i)
            val time = frameObj.optDouble("time", i / fps.toDouble()).toFloat()

            val poseJoints = parsePose(frameObj.optJSONObject("pose"))
            val leftHand = parseHand(frameObj.optJSONObject("leftHand"))
            val rightHand = parseHand(frameObj.optJSONObject("rightHand"))

            framesList.add(
                BimFrame(
                    frame = frameIndex,
                    time = time,
                    pose = poseJoints,
                    leftHand = leftHand,
                    rightHand = rightHand
                )
            )
        }

        return BimMotion(
            word = word,
            fps = fps,
            numFrames = if (numFrames > 0) numFrames else framesList.size,
            duration = if (duration > 0f) duration else (framesList.size / fps),
            frames = framesList
        )
    }

    /**
     * Parses a BIM Motion from an [InputStream].
     */
    fun parseStream(stream: InputStream): BimMotion {
        val jsonString = stream.bufferedReader().use { it.readText() }
        return parseJson(jsonString)
    }

    private fun parsePose(poseObj: JSONObject?): PoseJoints {
        if (poseObj == null) return PoseJoints()

        return PoseJoints(
            nose = parseVec3(poseObj.optJSONArray("nose")),
            leftShoulder = parseVec3(poseObj.optJSONArray("leftShoulder")),
            rightShoulder = parseVec3(poseObj.optJSONArray("rightShoulder")),
            leftElbow = parseVec3(poseObj.optJSONArray("leftElbow")),
            rightElbow = parseVec3(poseObj.optJSONArray("rightElbow")),
            leftWrist = parseVec3(poseObj.optJSONArray("leftWrist")),
            rightWrist = parseVec3(poseObj.optJSONArray("rightWrist"))
        )
    }

    private fun parseHand(handObj: JSONObject?): HandJoints? {
        if (handObj == null) return null
        val active = handObj.optBoolean("active", true)
        val pointsArray = handObj.optJSONArray("points") ?: return null

        val points = ArrayList<Vec3>(pointsArray.length())
        for (i in 0 until pointsArray.length()) {
            val ptArray = pointsArray.optJSONArray(i)
            val pt = parseVec3(ptArray) ?: Vec3.ZERO
            points.add(pt)
        }

        return HandJoints(active = active, points = points)
    }

    private fun parseVec3(arr: JSONArray?): Vec3? {
        if (arr == null || arr.length() < 3) return null
        return Vec3(
            arr.optDouble(0, 0.0).toFloat(),
            arr.optDouble(1, 0.0).toFloat(),
            arr.optDouble(2, 0.0).toFloat()
        )
    }
}
