package com.isuara.app.avatar.gl

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class MeshData(
    val vertexBuffer: FloatBuffer,
    val normalBuffer: FloatBuffer,
    val indexBuffer: ShortBuffer,
    val indexCount: Int
)

object AvatarMeshBuilder {

    /**
     * Builds a high-quality UV Sphere with smooth normals.
     */
    fun createUnitSphere(latitudeBands: Int = 24, longitudeBands: Int = 24): MeshData {
        val vertexPositionData = ArrayList<Float>()
        val normalData = ArrayList<Float>()
        val indexData = ArrayList<Short>()

        for (lat in 0..latitudeBands) {
            val theta = lat * PI.toFloat() / latitudeBands
            val sinTheta = sin(theta)
            val cosTheta = cos(theta)

            for (lon in 0..longitudeBands) {
                val phi = lon * 2 * PI.toFloat() / longitudeBands
                val sinPhi = sin(phi)
                val cosPhi = cos(phi)

                val x = cosPhi * sinTheta
                val y = cosTheta
                val z = sinPhi * sinTheta

                vertexPositionData.add(x)
                vertexPositionData.add(y)
                vertexPositionData.add(z)

                normalData.add(x)
                normalData.add(y)
                normalData.add(z)
            }
        }

        for (lat in 0 until latitudeBands) {
            for (lon in 0 until longitudeBands) {
                val first = (lat * (longitudeBands + 1) + lon).toShort()
                val second = (first + longitudeBands + 1).toShort()

                indexData.add(first)
                indexData.add(second)
                indexData.add((first + 1).toShort())

                indexData.add(second)
                indexData.add((second + 1).toShort())
                indexData.add((first + 1).toShort())
            }
        }

        return toMeshData(vertexPositionData, normalData, indexData)
    }

    /**
     * Builds a Sculpted Modern Mannequin Head (clean humanoid contour with jaw taper).
     */
    fun createMannequinHead(latitudeBands: Int = 28, longitudeBands: Int = 28): MeshData {
        val vertexPositionData = ArrayList<Float>()
        val normalData = ArrayList<Float>()
        val indexData = ArrayList<Short>()

        for (lat in 0..latitudeBands) {
            val theta = lat * PI.toFloat() / latitudeBands
            val sinTheta = sin(theta)
            val cosTheta = cos(theta)

            // Y coordinate from +1.0 (crown) to -1.0 (chin)
            val y = cosTheta

            // Organic jawline tapering
            val taperX = if (y < 0f) 0.82f + 0.18f * (1f + y) else 1.0f - 0.04f * (y * y)
            val taperZ = if (y < 0f) 0.78f + 0.22f * (1f + y) else 1.0f

            for (lon in 0..longitudeBands) {
                val phi = lon * 2 * PI.toFloat() / longitudeBands
                val sinPhi = sin(phi)
                val cosPhi = cos(phi)

                val x = cosPhi * sinTheta * taperX
                val z = sinPhi * sinTheta * taperZ

                vertexPositionData.add(x)
                vertexPositionData.add(y)
                vertexPositionData.add(z)

                val len = sqrt(x * x + y * y + z * z)
                if (len > 0.0001f) {
                    normalData.add(x / len)
                    normalData.add(y / len)
                    normalData.add(z / len)
                } else {
                    normalData.add(0f); normalData.add(1f); normalData.add(0f)
                }
            }
        }

        for (lat in 0 until latitudeBands) {
            for (lon in 0 until longitudeBands) {
                val first = (lat * (longitudeBands + 1) + lon).toShort()
                val second = (first + longitudeBands + 1).toShort()

                indexData.add(first)
                indexData.add(second)
                indexData.add((first + 1).toShort())

                indexData.add(second)
                indexData.add((second + 1).toShort())
                indexData.add((first + 1).toShort())
            }
        }

        return toMeshData(vertexPositionData, normalData, indexData)
    }

    /**
     * Builds a Smooth Rounded Capsule with hemispherical rounded ends (Top & Bottom).
     * This creates seamless, organic human limbs without harsh cylinder edges.
     */
    fun createRoundedCapsule(
        radiusTop: Float = 1.0f,
        radiusBottom: Float = 1.0f,
        cylinderHeight: Float = 1.0f,
        capBands: Int = 8,
        segments: Int = 20
    ): MeshData {
        val vertexPositionData = ArrayList<Float>()
        val normalData = ArrayList<Float>()
        val indexData = ArrayList<Short>()

        val halfH = cylinderHeight * 0.5f

        // 1. Top Hemisphere Cap (lat from 0 to PI/2)
        for (lat in 0..capBands) {
            val theta = (lat * (PI.toFloat() * 0.5f)) / capBands
            val sinTheta = sin(theta)
            val cosTheta = cos(theta)

            val yOffset = halfH + cosTheta * radiusTop
            val r = sinTheta * radiusTop

            for (lon in 0..segments) {
                val phi = lon * 2 * PI.toFloat() / segments
                val cosPhi = cos(phi)
                val sinPhi = sin(phi)

                val x = cosPhi * r
                val z = sinPhi * r

                vertexPositionData.add(x)
                vertexPositionData.add(yOffset)
                vertexPositionData.add(z)

                normalData.add(cosPhi * sinTheta)
                normalData.add(cosTheta)
                normalData.add(sinPhi * sinTheta)
            }
        }

        // 2. Bottom Hemisphere Cap (lat from PI/2 to PI)
        for (lat in 0..capBands) {
            val theta = (PI.toFloat() * 0.5f) + (lat * (PI.toFloat() * 0.5f)) / capBands
            val sinTheta = sin(theta)
            val cosTheta = cos(theta)

            val yOffset = -halfH + cosTheta * radiusBottom
            val r = sinTheta * radiusBottom

            for (lon in 0..segments) {
                val phi = lon * 2 * PI.toFloat() / segments
                val cosPhi = cos(phi)
                val sinPhi = sin(phi)

                val x = cosPhi * r
                val z = sinPhi * r

                vertexPositionData.add(x)
                vertexPositionData.add(yOffset)
                vertexPositionData.add(z)

                normalData.add(cosPhi * sinTheta)
                normalData.add(cosTheta)
                normalData.add(sinPhi * sinTheta)
            }
        }

        // Connect rings with triangle strips
        val totalRings = (capBands + 1) * 2
        for (ring in 0 until totalRings - 1) {
            for (lon in 0 until segments) {
                val first = (ring * (segments + 1) + lon).toShort()
                val second = (first + segments + 1).toShort()

                indexData.add(first)
                indexData.add(second)
                indexData.add((first + 1).toShort())

                indexData.add(second)
                indexData.add((second + 1).toShort())
                indexData.add((first + 1).toShort())
            }
        }

        return toMeshData(vertexPositionData, normalData, indexData)
    }

    /**
     * Builds a Contoured Athletic Humanoid Torso Mesh (Chest width -> Waist taper -> Hip).
     */
    fun createHumanoidTorso(slices: Int = 16, segments: Int = 24): MeshData {
        val vertexPositionData = ArrayList<Float>()
        val normalData = ArrayList<Float>()
        val indexData = ArrayList<Short>()

        for (i in 0..slices) {
            val t = i.toFloat() / slices // 0.0 (top shoulders) to 1.0 (waist/hips)
            val y = 0.5f - t // +0.5 to -0.5

            // Chest is wider at top (t=0.2), tapering down towards waist (t=0.8)
            val widthTaper = when {
                t < 0.25f -> 1.0f + 0.15f * (t / 0.25f) // Shoulders broadening
                t < 0.85f -> 1.15f - 0.35f * ((t - 0.25f) / 0.60f) // Waist tapering
                else -> 0.80f + 0.08f * ((t - 0.85f) / 0.15f) // Subtle hip flare
            }
            val depthTaper = 0.70f + 0.15f * (1.0f - t)

            for (j in 0..segments) {
                val phi = j * 2 * PI.toFloat() / segments
                val cosPhi = cos(phi)
                val sinPhi = sin(phi)

                val x = cosPhi * widthTaper
                val z = sinPhi * depthTaper

                vertexPositionData.add(x)
                vertexPositionData.add(y)
                vertexPositionData.add(z)

                val len = sqrt(x * x + z * z)
                normalData.add(if (len > 0.001f) x / len else 0f)
                normalData.add(0f)
                normalData.add(if (len > 0.001f) z / len else 1f)
            }
        }

        for (i in 0 until slices) {
            for (j in 0 until segments) {
                val first = (i * (segments + 1) + j).toShort()
                val second = (first + segments + 1).toShort()

                indexData.add(first)
                indexData.add(second)
                indexData.add((first + 1).toShort())

                indexData.add(second)
                indexData.add((second + 1).toShort())
                indexData.add((first + 1).toShort())
            }
        }

        return toMeshData(vertexPositionData, normalData, indexData)
    }

    /**
     * Builds a Unit Cylinder along Y-axis.
     */
    fun createUnitCylinder(segments: Int = 20): MeshData {
        val vertexPositionData = ArrayList<Float>()
        val normalData = ArrayList<Float>()
        val indexData = ArrayList<Short>()

        val yBottom = -0.5f
        val yTop = 0.5f

        for (i in 0..segments) {
            val theta = i * 2 * PI.toFloat() / segments
            val cosTheta = cos(theta)
            val sinTheta = sin(theta)

            vertexPositionData.add(cosTheta); vertexPositionData.add(yBottom); vertexPositionData.add(sinTheta)
            normalData.add(cosTheta); normalData.add(0f); normalData.add(sinTheta)

            vertexPositionData.add(cosTheta); vertexPositionData.add(yTop); vertexPositionData.add(sinTheta)
            normalData.add(cosTheta); normalData.add(0f); normalData.add(sinTheta)
        }

        for (i in 0 until segments) {
            val b1 = (i * 2).toShort()
            val t1 = (i * 2 + 1).toShort()
            val b2 = ((i + 1) * 2).toShort()
            val t2 = ((i + 1) * 2 + 1).toShort()

            indexData.add(b1); indexData.add(b2); indexData.add(t1)
            indexData.add(b2); indexData.add(t2); indexData.add(t1)
        }

        // Top cap
        val topCenterIndex = (vertexPositionData.size / 3).toShort()
        vertexPositionData.add(0f); vertexPositionData.add(yTop); vertexPositionData.add(0f)
        normalData.add(0f); normalData.add(1f); normalData.add(0f)

        val topRimStart = (vertexPositionData.size / 3).toShort()
        for (i in 0..segments) {
            val theta = i * 2 * PI.toFloat() / segments
            vertexPositionData.add(cos(theta)); vertexPositionData.add(yTop); vertexPositionData.add(sin(theta))
            normalData.add(0f); normalData.add(1f); normalData.add(0f)
        }
        for (i in 0 until segments) {
            indexData.add(topCenterIndex)
            indexData.add((topRimStart + i).toShort())
            indexData.add((topRimStart + i + 1).toShort())
        }

        // Bottom cap
        val bottomCenterIndex = (vertexPositionData.size / 3).toShort()
        vertexPositionData.add(0f); vertexPositionData.add(yBottom); vertexPositionData.add(0f)
        normalData.add(0f); normalData.add(-1f); normalData.add(0f)

        val bottomRimStart = (vertexPositionData.size / 3).toShort()
        for (i in 0..segments) {
            val theta = i * 2 * PI.toFloat() / segments
            vertexPositionData.add(cos(theta)); vertexPositionData.add(yBottom); vertexPositionData.add(sin(theta))
            normalData.add(0f); normalData.add(-1f); normalData.add(0f)
        }
        for (i in 0 until segments) {
            indexData.add(bottomCenterIndex)
            indexData.add((bottomRimStart + i + 1).toShort())
            indexData.add((bottomRimStart + i).toShort())
        }

        return toMeshData(vertexPositionData, normalData, indexData)
    }

    /**
     * Builds a Unit Cube.
     */
    fun createUnitCube(): MeshData {
        val p = 0.5f
        val n = -0.5f

        val vertices = floatArrayOf(
            n, n, p,   p, n, p,   p, p, p,   n, p, p,
            n, n, n,   n, p, n,   p, p, n,   p, n, n,
            n, p, n,   n, p, p,   p, p, p,   p, p, n,
            n, n, n,   p, n, n,   p, n, p,   n, n, p,
            p, n, n,   p, p, n,   p, p, p,   p, n, p,
            n, n, n,   n, n, p,   n, p, p,   n, p, n
        )

        val normals = floatArrayOf(
            0f, 0f, 1f,   0f, 0f, 1f,   0f, 0f, 1f,   0f, 0f, 1f,
            0f, 0f, -1f,  0f, 0f, -1f,  0f, 0f, -1f,  0f, 0f, -1f,
            0f, 1f, 0f,   0f, 1f, 0f,   0f, 1f, 0f,   0f, 1f, 0f,
            0f, -1f, 0f,  0f, -1f, 0f,  0f, -1f, 0f,  0f, -1f, 0f,
            1f, 0f, 0f,   1f, 0f, 0f,   1f, 0f, 0f,   1f, 0f, 0f,
            -1f, 0f, 0f,  -1f, 0f, 0f,  -1f, 0f, 0f,  -1f, 0f, 0f
        )

        val indices = shortArrayOf(
            0, 1, 2,  0, 2, 3,
            4, 5, 6,  4, 6, 7,
            8, 9, 10,  8, 10, 11,
            12, 13, 14,  12, 14, 15,
            16, 17, 18,  16, 18, 19,
            20, 21, 22,  20, 22, 23
        )

        val vBuf = ByteBuffer.allocateDirect(vertices.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        vBuf.put(vertices).position(0)

        val nBuf = ByteBuffer.allocateDirect(normals.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        nBuf.put(normals).position(0)

        val iBuf = ByteBuffer.allocateDirect(indices.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer()
        iBuf.put(indices).position(0)

        return MeshData(vBuf, nBuf, iBuf, indices.size)
    }

    private fun toMeshData(
        vertexList: List<Float>,
        normalList: List<Float>,
        indexList: List<Short>
    ): MeshData {
        val vBuf = ByteBuffer.allocateDirect(vertexList.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        vertexList.forEach { vBuf.put(it) }
        vBuf.position(0)

        val nBuf = ByteBuffer.allocateDirect(normalList.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        normalList.forEach { nBuf.put(it) }
        nBuf.position(0)

        val iBuf = ByteBuffer.allocateDirect(indexList.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer()
        indexList.forEach { iBuf.put(it) }
        iBuf.position(0)

        return MeshData(vBuf, nBuf, iBuf, indexList.size)
    }
}
