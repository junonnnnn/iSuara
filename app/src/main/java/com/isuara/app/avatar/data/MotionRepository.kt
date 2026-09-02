package com.isuara.app.avatar.data

import android.content.Context
import android.util.Log
import com.isuara.app.avatar.model.BimFrame
import com.isuara.app.avatar.model.BimMotion
import com.isuara.app.avatar.model.HandJoints
import com.isuara.app.avatar.model.PoseJoints
import com.isuara.app.avatar.parser.MotionParser
import java.io.InputStream

data class VocabularyItem(
    val key: String,
    val title: String,
    val translation: String,
    val category: String,
    val isSentence: Boolean = false,
    val assetFileName: String
)

class MotionRepository(private val context: Context) {

    companion object {
        private const val TAG = "MotionRepository"

        val CATALOG: List<VocabularyItem> = listOf(
            // Sentences
            VocabularyItem("apa_khabar_suhu", "Apa Khabar → Suhu", "Sentence: How are you → Temperature", "Continuous Sentences", isSentence = true, "apa_khabar_suhu_3d.json"),
            VocabularyItem("suhu_apa_khabar", "Suhu → Apa Khabar", "Sentence: Temperature → How are you", "Continuous Sentences", isSentence = true, "suhu_apa_khabar_3d.json"),

            // 10 Audited Pilot Vocabulary
            VocabularyItem("terima_kasih", "Terima Kasih", "Thank you", "Pilot Benchmarks", assetFileName = "terima_kasih.json"),
            VocabularyItem("makan", "Makan", "Eat", "Pilot Benchmarks", assetFileName = "makan.json"),
            VocabularyItem("gembira", "Gembira", "Happy", "Pilot Benchmarks", assetFileName = "gembira.json"),
            VocabularyItem("cinta", "Cinta", "Love", "Pilot Benchmarks", assetFileName = "cinta.json"),
            VocabularyItem("belajar", "Belajar", "Study / Learn", "Pilot Benchmarks", assetFileName = "belajar.json"),
            VocabularyItem("doktor", "Doktor", "Doctor", "Pilot Benchmarks", assetFileName = "doktor.json"),
            VocabularyItem("baju", "Baju", "Clothes", "Pilot Benchmarks", assetFileName = "baju.json"),
            VocabularyItem("air", "Air", "Water", "Pilot Benchmarks", assetFileName = "air.json"),
            VocabularyItem("abang", "Abang", "Brother", "Pilot Benchmarks", assetFileName = "abang.json"),
            VocabularyItem("apa_khabar", "Apa Khabar", "How are you", "Pilot Benchmarks", assetFileName = "apa_khabar_3d.json"),
            VocabularyItem("suhu", "Suhu", "Temperature", "Pilot Benchmarks", assetFileName = "suhu_3d.json"),

            // Common Conversation & Greetings
            VocabularyItem("hai", "Hai", "Hi / Hello", "Daily Conversation", assetFileName = "hai.json"),
            VocabularyItem("hello", "Hello", "Hello", "Daily Conversation", assetFileName = "hello.json"),
            VocabularyItem("saya", "Saya", "I / Me", "Daily Conversation", assetFileName = "saya.json"),
            VocabularyItem("awak", "Awak", "You", "Daily Conversation", assetFileName = "awak.json"),
            VocabularyItem("nama", "Nama", "Name", "Daily Conversation", assetFileName = "nama.json"),
            VocabularyItem("tolong", "Tolong", "Help / Please", "Daily Conversation", assetFileName = "tolong.json"),
            VocabularyItem("sama_sama", "Sama-sama", "You're welcome", "Daily Conversation", assetFileName = "sama_sama.json"),
            VocabularyItem("baik", "Baik", "Good / Fine", "Daily Conversation", assetFileName = "baik.json"),
            VocabularyItem("cantik", "Cantik", "Beautiful", "Daily Conversation", assetFileName = "cantik.json"),
            VocabularyItem("ibu", "Ibu", "Mother", "Family", assetFileName = "ibu.json"),
            VocabularyItem("bapa", "Bapa", "Father", "Family", assetFileName = "bapa.json"),
            VocabularyItem("sekolah", "Sekolah", "School", "Places", assetFileName = "sekolah.json"),
            VocabularyItem("hospital", "Hospital", "Hospital", "Places", assetFileName = "hospital.json"),

            // Numbers & Letters
            VocabularyItem("1", "1 (Satu)", "One", "Numbers", assetFileName = "1.json"),
            VocabularyItem("2", "2 (Dua)", "Two", "Numbers", assetFileName = "2.json"),
            VocabularyItem("3", "3 (Tiga)", "Three", "Numbers", assetFileName = "3.json"),
            VocabularyItem("4", "4 (Empat)", "Four", "Numbers", assetFileName = "4.json"),
            VocabularyItem("5", "5 (Lima)", "Five", "Numbers", assetFileName = "5.json"),
            VocabularyItem("a", "Huruf A", "Letter A", "Alphabet", assetFileName = "a.json"),
            VocabularyItem("b", "Huruf B", "Letter B", "Alphabet", assetFileName = "b.json"),
            VocabularyItem("c", "Huruf C", "Letter C", "Alphabet", assetFileName = "c.json")
        )
    }

    private val cache = HashMap<String, BimMotion>()

    /**
     * Loads a motion track by vocabulary key.
     */
    fun loadMotion(key: String): BimMotion? {
        val cached = cache[key]
        if (cached != null) return cached

        val item = CATALOG.find { it.key.equals(key, ignoreCase = true) }
        val fileName = item?.assetFileName ?: if (key.endsWith(".json")) key else "$key.json"

        return try {
            val assetPath = "motions/$fileName"
            val stream: InputStream = context.assets.open(assetPath)
            val motion = MotionParser.parseStream(stream)
            cache[key] = motion
            motion
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load motion for key '$key' ($fileName)", e)
            null
        }
    }

    /**
     * Searches vocabulary items matching query in title, translation, or key.
     */
    fun searchVocabulary(query: String): List<VocabularyItem> {
        if (query.isBlank()) return CATALOG
        val clean = query.trim().lowercase()
        return CATALOG.filter {
            it.title.lowercase().contains(clean) ||
            it.translation.lowercase().contains(clean) ||
            it.key.lowercase().contains(clean)
        }
    }

    /**
     * Synthesizes a continuous sentence by joining multiple vocabulary words with a smooth 16-frame co-articulation bridge.
     */
    fun synthesizeSentence(words: List<String>): BimMotion? {
        val motions = words.mapNotNull { loadMotion(it) }
        if (motions.isEmpty()) return null
        if (motions.size == 1) return motions[0]

        val combinedFrames = ArrayList<BimFrame>()
        val fps = 50f
        var currentFrameIndex = 0

        for (i in motions.indices) {
            val m = motions[i]
            // Add frames of current word
            for (f in m.frames) {
                combinedFrames.add(
                    f.copy(
                        frame = currentFrameIndex,
                        time = currentFrameIndex / fps
                    )
                )
                currentFrameIndex++
            }

            // If there is a next word, insert a 16-frame smooth co-articulation bridge
            if (i < motions.size - 1) {
                val lastFrame = m.frames.lastOrNull()
                val nextMotion = motions[i + 1]
                val firstFrameNext = nextMotion.frames.firstOrNull()

                if (lastFrame != null && firstFrameNext != null) {
                    val bridgeCount = 16
                    for (b in 1..bridgeCount) {
                        val alpha = b / (bridgeCount + 1).toFloat()
                        val bridgedFrame = bridgeFrames(lastFrame, firstFrameNext, alpha, currentFrameIndex, fps)
                        combinedFrames.add(bridgedFrame)
                        currentFrameIndex++
                    }
                }
            }
        }

        val totalDuration = currentFrameIndex / fps
        val title = words.joinToString(" ") { it.replace("_", " ").replaceFirstChar(Char::titlecase) }

        return BimMotion(
            word = title,
            fps = fps,
            numFrames = combinedFrames.size,
            duration = totalDuration,
            frames = combinedFrames
        )
    }

    private fun bridgeFrames(f0: BimFrame, f1: BimFrame, alpha: Float, frameIndex: Int, fps: Float): BimFrame {
        val nose = if (f0.pose.nose != null && f1.pose.nose != null) f0.pose.nose.lerp(f1.pose.nose, alpha) else f0.pose.nose

        val pose = PoseJoints(
            nose = nose,
            leftShoulder = f0.pose.leftShoulder,
            rightShoulder = f0.pose.rightShoulder,
            leftElbow = f0.pose.leftElbow,
            rightElbow = f0.pose.rightElbow,
            leftWrist = f0.pose.leftWrist,
            rightWrist = f0.pose.rightWrist
        )

        val lh = bridgeHand(f0.leftHand, f1.leftHand, alpha)
        val rh = bridgeHand(f0.rightHand, f1.rightHand, alpha)

        return BimFrame(
            frame = frameIndex,
            time = frameIndex / fps,
            pose = pose,
            leftHand = lh,
            rightHand = rh
        )
    }

    private fun bridgeHand(h0: HandJoints?, h1: HandJoints?, alpha: Float): HandJoints? {
        if (h0 == null || h1 == null) return h0 ?: h1
        if (h0.points.size != 21 || h1.points.size != 21) return h0

        val bridgedPoints = (0 until 21).map { i ->
            h0.points[i].lerp(h1.points[i], alpha)
        }
        return HandJoints(active = h0.active || h1.active, points = bridgedPoints)
    }
}
