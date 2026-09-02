package com.isuara.app.avatar.gl

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.SystemClock
import com.isuara.app.avatar.engine.AvatarPoseState
import com.isuara.app.avatar.engine.KinematicSolver
import com.isuara.app.avatar.engine.MotionPlayer
import com.isuara.app.avatar.model.Vec3
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.sqrt

class AvatarGLRenderer(
    val motionPlayer: MotionPlayer,
    val orbitController: TouchOrbitController = TouchOrbitController()
) : GLSurfaceView.Renderer {

    companion object {
        // Faithful color palette from avatar.png reference
        val COLOR_SKIN = floatArrayOf(0.665f, 0.525f, 0.415f, 1.0f)       // Natural warm mannequin tone #AA866A
        val COLOR_EYE = floatArrayOf(0.080f, 0.080f, 0.080f, 1.0f)        // Solid black dot eyes #141414
        val COLOR_BODY = floatArrayOf(0.045f, 0.305f, 0.640f, 1.0f)       // Royal Blue body #0B4EA3
        val COLOR_GRID = floatArrayOf(0.120f, 0.140f, 0.180f, 0.5f)       // Subtle studio floor grid
    }

    private val vPMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val mvMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    private var programId = 0
    private var uMVPMatrixHandle = 0
    private var uMVMatrixHandle = 0
    private var uColorHandle = 0
    private var uLightPosHandle = 0
    private var aPositionHandle = 0
    private var aNormalHandle = 0

    // Mesh primitives
    private lateinit var sphereMesh: MeshData
    private lateinit var cylinderMesh: MeshData
    private lateinit var cubeMesh: MeshData
    private var gridVertexBuffer: FloatBuffer? = null
    private var gridVertexCount = 0

    private var lastFrameTimeNanos = 0L

    private val vertexShaderCode = """
        uniform mat4 u_MVPMatrix;
        uniform mat4 u_MVMatrix;
        attribute vec4 a_Position;
        attribute vec3 a_Normal;
        varying vec3 v_Position;
        varying vec3 v_Normal;

        void main() {
            v_Position = vec3(u_MVMatrix * a_Position);
            v_Normal = normalize(mat3(u_MVMatrix) * a_Normal);
            gl_Position = u_MVPMatrix * a_Position;
        }
    """.trimIndent()

    private val fragmentShaderCode = """
        precision mediump float;
        uniform vec4 u_Color;
        uniform vec3 u_LightPos;
        varying vec3 v_Position;
        varying vec3 v_Normal;

        void main() {
            vec3 normal = normalize(v_Normal);
            vec3 lightVector = normalize(u_LightPos - v_Position);
            vec3 viewDir = normalize(-v_Position);

            // Key light diffuse
            float NdotL = max(dot(normal, lightVector), 0.0);
            
            // Soft fill light
            vec3 fillDir = normalize(vec3(-1.0, -0.4, 0.8));
            float fill = max(dot(normal, fillDir), 0.0) * 0.18;

            float ambient = 0.46;
            float lighting = ambient + NdotL * 0.54 + fill;

            // Soft edge rim
            float rim = 1.0 - max(dot(viewDir, normal), 0.0);
            rim = smoothstep(0.70, 1.0, rim) * 0.16;
            vec3 rimColor = vec3(0.70, 0.82, 1.0) * rim;

            vec3 finalColor = u_Color.rgb * lighting + rimColor;
            gl_FragColor = vec4(finalColor, u_Color.a);
        }
    """.trimIndent()

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.035f, 0.051f, 0.075f, 1.0f) // Studio Dark #090D13
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glCullFace(GLES20.GL_BACK)

        // Compile shaders
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)
        programId = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)
        }

        uMVPMatrixHandle = GLES20.glGetUniformLocation(programId, "u_MVPMatrix")
        uMVMatrixHandle = GLES20.glGetUniformLocation(programId, "u_MVMatrix")
        uColorHandle = GLES20.glGetUniformLocation(programId, "u_Color")
        uLightPosHandle = GLES20.glGetUniformLocation(programId, "u_LightPos")
        aPositionHandle = GLES20.glGetAttribLocation(programId, "a_Position")
        aNormalHandle = GLES20.glGetAttribLocation(programId, "a_Normal")

        // Build geometric primitives
        sphereMesh = AvatarMeshBuilder.createUnitSphere(24, 24)
        cylinderMesh = AvatarMeshBuilder.createUnitCylinder(24)
        cubeMesh = AvatarMeshBuilder.createUnitCube()
        buildGroundGrid()

        lastFrameTimeNanos = SystemClock.elapsedRealtimeNanos()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val ratio = width.toFloat() / if (height > 0) height.toFloat() else 1f
        Matrix.perspectiveM(projectionMatrix, 0, 45f, ratio, 0.1f, 100f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        val nowNanos = SystemClock.elapsedRealtimeNanos()
        val deltaSec = if (lastFrameTimeNanos > 0L) ((nowNanos - lastFrameTimeNanos) / 1_000_000_000.0f).coerceIn(0.001f, 0.1f) else 0.016f
        lastFrameTimeNanos = nowNanos

        val pose = motionPlayer.advanceTime(deltaSec)

        val eye = orbitController.computeEyePosition()
        Matrix.setLookAtM(
            viewMatrix, 0,
            eye[0], eye[1], eye[2],
            orbitController.targetX, orbitController.targetY, orbitController.targetZ,
            0f, 1f, 0f
        )
        Matrix.multiplyMM(vPMatrix, 0, projectionMatrix, 0, viewMatrix, 0)

        GLES20.glUseProgram(programId)

        val lightPosInModel = floatArrayOf(2.5f, 4.5f, 3.5f, 1.0f)
        val lightPosInEye = FloatArray(4)
        Matrix.multiplyMV(lightPosInEye, 0, viewMatrix, 0, lightPosInModel, 0)
        GLES20.glUniform3f(uLightPosHandle, lightPosInEye[0], lightPosInEye[1], lightPosInEye[2])

        // 1. Floor grid
        drawGrid()

        // 2. Cylinder Avatar (matching avatar.png reference)
        renderCylinderAvatar(pose)
    }

    private fun renderCylinderAvatar(pose: AvatarPoseState) {
        val hPos = Vec3(pose.headPosition.x, 1.33f, pose.headPosition.z)

        // ── A. Oval Head & Dot Eyes (avatar.png reference) ──
        // 1. Oval Head Sphere (natural width and height)
        drawSphere(hPos, 0.130f, COLOR_SKIN, scaleX = 1.00f, scaleY = 1.15f, scaleZ = 0.98f)

        // 2. Clean Dot Eyes (centered on face)
        val eyeY = hPos.y + 0.010f
        val eyeZ = hPos.z + 0.124f
        val eyeSpacing = 0.038f

        drawSphere(Vec3(hPos.x + eyeSpacing, eyeY, eyeZ), 0.0085f, COLOR_EYE)
        drawSphere(Vec3(hPos.x - eyeSpacing, eyeY, eyeZ), 0.0085f, COLOR_EYE)

        // ── B. Clean Natural Neck Cylinder (short & proportioned) ──
        drawCylinderBetween(Vec3(0f, 1.15f, 0f), Vec3(0f, 1.22f, 0f), 0.044f, COLOR_SKIN)

        // ── C. Cylinder Torso (avatar.png reference) ──
        // Upright royal blue body cylinder
        drawCylinderBetween(Vec3(0f, 0.65f, 0f), Vec3(0f, 1.15f, 0f), 0.165f, COLOR_BODY)

        // Shoulder Joint Spheres
        drawSphere(pose.leftShoulder, 0.046f, COLOR_BODY)
        drawSphere(pose.rightShoulder, 0.046f, COLOR_BODY)

        // ── D. Left Arm (Cylinders & Spheres) ──
        // Upper Arm Cylinder (Blue)
        drawCylinderBetween(pose.leftShoulder, pose.leftElbow, 0.038f, COLOR_BODY)
        // Elbow Sphere (Blue)
        drawSphere(pose.leftElbow, 0.038f, COLOR_BODY)
        // Forearm Cylinder (Skin tone)
        drawCylinderBetween(pose.leftElbow, pose.leftWrist, 0.032f, COLOR_SKIN)
        // Wrist Sphere (Skin tone)
        drawSphere(pose.leftWrist, 0.026f, COLOR_SKIN)

        // ── E. Right Arm (Cylinders & Spheres) ──
        // Upper Arm Cylinder (Blue)
        drawCylinderBetween(pose.rightShoulder, pose.rightElbow, 0.038f, COLOR_BODY)
        // Elbow Sphere (Blue)
        drawSphere(pose.rightElbow, 0.038f, COLOR_BODY)
        // Forearm Cylinder (Skin tone)
        drawCylinderBetween(pose.rightElbow, pose.rightWrist, 0.032f, COLOR_SKIN)
        // Wrist Sphere (Skin tone)
        drawSphere(pose.rightWrist, 0.026f, COLOR_SKIN)

        // ── F. Palm & Articulated Hands ──
        // Right Palm
        if (pose.rightPalmPos != null && pose.rightPalmLookAt != null) {
            drawPalm(pose.rightPalmPos, pose.rightPalmLookAt, isLeft = false)
        }
        // Left Palm
        if (pose.leftPalmPos != null && pose.leftPalmLookAt != null) {
            drawPalm(pose.leftPalmPos, pose.leftPalmLookAt, isLeft = true)
        }

        // Right Hand Finger Segments & Knuckles
        pose.rightHandPoints?.let { pts ->
            if (pts.size >= 21) {
                KinematicSolver.HAND_BONES.forEach { (i, j) ->
                    val p1 = pts[i]
                    val p2 = pts[j]
                    drawSphere(p1, 0.0070f, COLOR_SKIN)
                    drawSphere(p2, 0.0065f, COLOR_SKIN)
                    drawCylinderBetween(p1, p2, 0.0055f, COLOR_SKIN)
                }
            }
        }

        // Left Hand Finger Segments & Knuckles
        pose.leftHandPoints?.let { pts ->
            if (pts.size >= 21) {
                KinematicSolver.HAND_BONES.forEach { (i, j) ->
                    val p1 = pts[i]
                    val p2 = pts[j]
                    drawSphere(p1, 0.0070f, COLOR_SKIN)
                    drawSphere(p2, 0.0065f, COLOR_SKIN)
                    drawCylinderBetween(p1, p2, 0.0055f, COLOR_SKIN)
                }
            }
        }
    }

    private fun drawPalm(pos: Vec3, lookAt: Vec3, isLeft: Boolean) {
        val dir = (lookAt - pos).normalize()
        val dist = pos.distanceTo(lookAt)

        val yaw = Math.toDegrees(atan2(dir.x.toDouble(), dir.z.toDouble())).toFloat()
        val pitch = Math.toDegrees(-acos(dir.y.toDouble().coerceIn(-1.0, 1.0))).toFloat() + 90f

        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, pos.x, pos.y, pos.z)
        Matrix.rotateM(modelMatrix, 0, yaw, 0f, 1f, 0f)
        Matrix.rotateM(modelMatrix, 0, pitch, 1f, 0f, 0f)
        Matrix.scaleM(modelMatrix, 0, 0.044f, 0.014f, if (dist > 0.01f) dist * 1.05f else 0.045f)

        drawMesh(cubeMesh, COLOR_SKIN)
    }

    private fun drawSphere(
        pos: Vec3,
        radius: Float,
        color: FloatArray,
        scaleX: Float = 1.0f,
        scaleY: Float = 1.0f,
        scaleZ: Float = 1.0f
    ) {
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, pos.x, pos.y, pos.z)
        Matrix.scaleM(modelMatrix, 0, radius * scaleX, radius * scaleY, radius * scaleZ)
        drawMesh(sphereMesh, color)
    }

    private fun drawCylinderBetween(p1: Vec3, p2: Vec3, radius: Float, color: FloatArray) {
        val dist = p1.distanceTo(p2)
        if (dist < 0.001f) return

        val midX = (p1.x + p2.x) * 0.5f
        val midY = (p1.y + p2.y) * 0.5f
        val midZ = (p1.z + p2.z) * 0.5f

        val dir = (p2 - p1).normalize()

        val angleDeg = Math.toDegrees(acos(dir.y.toDouble().coerceIn(-1.0, 1.0))).toFloat()
        val axisX = dir.z
        val axisY = 0f
        val axisZ = -dir.x
        val axisLen = sqrt(axisX * axisX + axisZ * axisZ)

        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, midX, midY, midZ)

        if (axisLen > 0.0001f) {
            Matrix.rotateM(modelMatrix, 0, angleDeg, axisX / axisLen, 0f, axisZ / axisLen)
        } else if (dir.y < 0f) {
            Matrix.rotateM(modelMatrix, 0, 180f, 1f, 0f, 0f)
        }

        Matrix.scaleM(modelMatrix, 0, radius, dist, radius)
        drawMesh(cylinderMesh, color)
    }

    private fun drawMesh(mesh: MeshData, color: FloatArray) {
        Matrix.multiplyMM(mvMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, mvMatrix, 0)

        GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(uMVMatrixHandle, 1, false, mvMatrix, 0)
        GLES20.glUniform4fv(uColorHandle, 1, color, 0)

        mesh.vertexBuffer.position(0)
        GLES20.glVertexAttribPointer(aPositionHandle, 3, GLES20.GL_FLOAT, false, 0, mesh.vertexBuffer)
        GLES20.glEnableVertexAttribArray(aPositionHandle)

        mesh.normalBuffer.position(0)
        GLES20.glVertexAttribPointer(aNormalHandle, 3, GLES20.GL_FLOAT, false, 0, mesh.normalBuffer)
        GLES20.glEnableVertexAttribArray(aNormalHandle)

        mesh.indexBuffer.position(0)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, mesh.indexCount, GLES20.GL_UNSIGNED_SHORT, mesh.indexBuffer)
    }

    private fun buildGroundGrid() {
        val size = 2.0f
        val step = 0.25f
        val lines = ArrayList<Float>()

        var z = -size
        while (z <= size + 0.001f) {
            lines.add(-size); lines.add(0f); lines.add(z)
            lines.add(size); lines.add(0f); lines.add(z)
            z += step
        }

        var x = -size
        while (x <= size + 0.001f) {
            lines.add(x); lines.add(0f); lines.add(-size)
            lines.add(x); lines.add(0f); lines.add(size)
            x += step
        }

        gridVertexCount = lines.size / 3
        gridVertexBuffer = ByteBuffer.allocateDirect(lines.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            lines.forEach { put(it) }
            position(0)
        }
    }

    private fun drawGrid() {
        val buffer = gridVertexBuffer ?: return
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.multiplyMM(mvMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, mvMatrix, 0)

        GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(uMVMatrixHandle, 1, false, mvMatrix, 0)
        GLES20.glUniform4fv(uColorHandle, 1, COLOR_GRID, 0)

        buffer.position(0)
        GLES20.glVertexAttribPointer(aPositionHandle, 3, GLES20.GL_FLOAT, false, 0, buffer)
        GLES20.glEnableVertexAttribArray(aPositionHandle)
        GLES20.glDisableVertexAttribArray(aNormalHandle)

        GLES20.glDrawArrays(GLES20.GL_LINES, 0, gridVertexCount)
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
        }
    }
}
