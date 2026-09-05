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
            VocabularyItem("c", "Huruf C", "Letter C", "Alphabet", assetFileName = "c.json"),

            // ── Curated Core Vocabulary for Police & Doctor Interactions (from SignAvatar-Muba) ──
            // Emergency & Police
            VocabularyItem("polis", "Polis", "Police", "Emergency", assetFileName = "polis.json"),
            VocabularyItem("curi", "Curi", "Steal / Theft", "Emergency", assetFileName = "curi.json"),
            VocabularyItem("hilang", "Hilang", "Lost / Missing", "Emergency", assetFileName = "hilang.json"),
            VocabularyItem("tangkap", "Tangkap", "Catch / Arrest", "Emergency", assetFileName = "tangkap.json"),
            VocabularyItem("jahat", "Jahat", "Bad / Evil", "Emergency", assetFileName = "jahat.json"),
            VocabularyItem("bahaya", "Bahaya", "Danger", "Emergency", assetFileName = "bahaya.json"),
            VocabularyItem("selamat", "Selamat", "Safe", "Emergency", assetFileName = "selamat.json"),
            VocabularyItem("duit", "Duit", "Money", "Emergency", assetFileName = "duit.json"),
            VocabularyItem("kereta", "Kereta", "Car", "Emergency", assetFileName = "kereta.json"),
            VocabularyItem("berita", "Berita", "News / Report", "Emergency", assetFileName = "berita.json"),
            VocabularyItem("kunci", "Kunci", "Key", "Emergency", assetFileName = "kunci.json"),
            VocabularyItem("tengok", "Tengok", "Look / Watch", "Emergency", assetFileName = "tengok.json"),
            VocabularyItem("lihat", "Lihat", "See", "Emergency", assetFileName = "lihat.json"),
            VocabularyItem("alamat", "Alamat", "Address", "Emergency", assetFileName = "alamat.json"),
            VocabularyItem("telefon", "Telefon", "Telephone", "Emergency", assetFileName = "telefon.json"),
            VocabularyItem("jangan", "Jangan", "Don't", "Emergency", assetFileName = "jangan.json"),
            VocabularyItem("takut", "Takut", "Scared / Fear", "Emergency", assetFileName = "takut.json"),
            VocabularyItem("berlari", "Berlari", "Run", "Emergency", assetFileName = "berlari.json"),

            // Healthcare & Doctor
            VocabularyItem("doktor", "Doktor", "Doctor", "Healthcare", assetFileName = "doktor.json"),
            VocabularyItem("hospital", "Hospital", "Hospital", "Healthcare", assetFileName = "hospital.json"),
            VocabularyItem("klinik", "Klinik", "Clinic", "Healthcare", assetFileName = "klinik.json"),
            VocabularyItem("anak", "Anak", "Child", "Family", assetFileName = "anak.json"),
            VocabularyItem("sakit", "Sakit", "Sick / Pain", "Healthcare", assetFileName = "sakit.json"),
            VocabularyItem("sakit_perut", "Sakit Perut", "Stomach Ache", "Healthcare", assetFileName = "sakit_perut.json"),
            VocabularyItem("sakit_kepala", "Sakit Kepala", "Headache", "Healthcare", assetFileName = "sakit_kepala.json"),
            VocabularyItem("demam", "Demam", "Fever", "Healthcare", assetFileName = "demam.json"),
            VocabularyItem("batuk", "Batuk", "Cough", "Healthcare", assetFileName = "batuk.json"),
            VocabularyItem("sihat", "Sihat", "Healthy", "Healthcare", assetFileName = "sihat.json"),
            VocabularyItem("ubat", "Ubat", "Medicine", "Healthcare", assetFileName = "ubat.json"),
            VocabularyItem("darah", "Darah", "Blood", "Healthcare", assetFileName = "darah.json"),
            VocabularyItem("suhu", "Suhu", "Temperature", "Healthcare", assetFileName = "suhu.json"),
            VocabularyItem("panas", "Panas", "Hot", "Healthcare", assetFileName = "panas.json"),
            VocabularyItem("sejuk", "Sejuk", "Cold", "Healthcare", assetFileName = "sejuk.json"),
            VocabularyItem("rehat", "Rehat", "Rest", "Healthcare", assetFileName = "rehat.json"),
            VocabularyItem("tidur", "Tidur", "Sleep", "Healthcare", assetFileName = "tidur.json"),
            VocabularyItem("badan", "Badan", "Body", "Healthcare", assetFileName = "badan.json"),
            VocabularyItem("dada", "Dada", "Chest", "Healthcare", assetFileName = "dada.json"),
            VocabularyItem("kaki", "Kaki", "Leg / Foot", "Healthcare", assetFileName = "kaki.json"),
            VocabularyItem("tangan", "Tangan", "Hand", "Healthcare", assetFileName = "tangan.json"),
            VocabularyItem("mata", "Mata", "Eye", "Healthcare", assetFileName = "mata.json"),
            VocabularyItem("telinga", "Telinga", "Ear", "Healthcare", assetFileName = "telinga.json"),
            VocabularyItem("mulut", "Mulut", "Mouth", "Healthcare", assetFileName = "mulut.json"),
            VocabularyItem("bayi", "Bayi", "Baby", "Family", assetFileName = "bayi.json"),

            // Questions, Time & Core Conversation
            VocabularyItem("apa", "Apa", "What", "Questions", assetFileName = "apa.json"),
            VocabularyItem("bila", "Bila", "When", "Questions", assetFileName = "bila.json"),
            VocabularyItem("mana", "Mana", "Where", "Questions", assetFileName = "mana.json"),
            VocabularyItem("siapa", "Siapa", "Who", "Questions", assetFileName = "siapa.json"),
            VocabularyItem("kenapa", "Kenapa", "Why", "Questions", assetFileName = "kenapa.json"),
            VocabularyItem("bagaimana", "Bagaimana", "How", "Questions", assetFileName = "bagaimana.json"),
            VocabularyItem("pagi", "Pagi", "Morning", "Time", assetFileName = "pagi.json"),
            VocabularyItem("petang", "Petang", "Afternoon", "Time", assetFileName = "petang.json"),
            VocabularyItem("malam", "Malam", "Night", "Time", assetFileName = "malam.json"),
            VocabularyItem("hari_ini", "Hari Ini", "Today", "Time", assetFileName = "hari_ini.json"),
            VocabularyItem("esok", "Esok", "Tomorrow", "Time", assetFileName = "esok.json"),
            VocabularyItem("sekarang", "Sekarang", "Now", "Time", assetFileName = "sekarang.json"),
            VocabularyItem("encik", "Encik", "Sir / Mister", "Greetings", assetFileName = "encik.json"),
            VocabularyItem("boleh", "Boleh", "Can / Able", "Daily Conversation", assetFileName = "boleh.json"),
            VocabularyItem("lapar", "Lapar", "Hungry", "Daily Conversation", assetFileName = "lapar.json"),
            VocabularyItem("dahaga", "Dahaga", "Thirsty", "Daily Conversation", assetFileName = "dahaga.json"),
            VocabularyItem("tahu", "Tahu", "Know", "Daily Conversation", assetFileName = "tahu.json"),
            VocabularyItem("tidak", "Tidak / Tak", "No / Not", "Daily Conversation", assetFileName = "tidak.json"),
            VocabularyItem("ada", "Ada", "Have / Exist", "Daily Conversation", assetFileName = "ada.json"),
            VocabularyItem("dan", "Dan", "And", "Daily Conversation", assetFileName = "dan.json"),
            VocabularyItem("dia", "Dia", "He / She", "Daily Conversation", assetFileName = "dia.json"),
            VocabularyItem("kami", "Kami", "We / Us", "Daily Conversation", assetFileName = "kami.json"),
            VocabularyItem("mereka", "Mereka", "They / Them", "Daily Conversation", assetFileName = "mereka.json"),
            VocabularyItem("rumah", "Rumah", "Home / House", "Places", assetFileName = "rumah.json"),
            VocabularyItem("benda", "Benda", "Things / Items", "Daily Conversation", assetFileName = "benda.json"),
            VocabularyItem("saya", "Saya", "I / Me", "Daily Conversation", assetFileName = "saya.json"),
            VocabularyItem("awak", "Awak", "You", "Daily Conversation", assetFileName = "awak.json"),
            VocabularyItem("datang", "Datang", "Come / Arrive", "Daily Conversation", assetFileName = "datang.json"),
            VocabularyItem("yang", "Yang", "Which / That", "Daily Conversation", assetFileName = "yang.json"),

            // Synthesized Dual-Version Continuous Sentences
            VocabularyItem("sentence_1_bim_encik_saya_boleh_tolong_apa", "Encik, Saya Boleh Tolong Apa?", "Sir, what can I help you with?", "Sentences", assetFileName = "sentence_1_bim_encik_saya_boleh_tolong_apa.json"),
            VocabularyItem("sentence_2_bim_apa_khabar_hari_ini_awak_datang_hospital_kenapa", "Apa-Khabar, Hari-Ini Awak Datang Hospital Kenapa?", "Hello, why did you come to the hospital today?", "Sentences", assetFileName = "sentence_2_bim_apa_khabar_hari_ini_awak_datang_hospital_kenapa.json"),
            VocabularyItem("sentence_1_ktbm_encik_apa_yang_saya_boleh_tolong", "KTBM: Encik Apa Yang Saya Boleh Tolong", "Sir, what can I help you with? (KTBM)", "Sentences", assetFileName = "sentence_1_ktbm_encik_apa_yang_saya_boleh_tolong.json"),
            VocabularyItem("sentence_2_ktbm_apa_khabar_kenapa_datang_hospital_hari_ini", "KTBM: Apa Khabar Kenapa Datang Hospital Hari Ini", "Hello, why did you come to the hospital today? (KTBM)", "Sentences", assetFileName = "sentence_2_ktbm_apa_khabar_kenapa_datang_hospital_hari_ini.json")
        )

        // Vocabulary aliases for continuous grammatical sentences
        val ALIASES: Map<String, String> = mapOf(
            "tak" to "tidak",
            "tak_tahu" to "tahu",
            "apa_apa" to "apa",
            "dr" to "doktor",
            "panas" to "demam",
            "budak" to "anak",
            "saudara" to "keluarga",
            "bantu" to "tolong",
            "bantuan" to "tolong",
            "lari" to "berlari",
            "apa-khabar" to "apa_khabar",
            "hari-ini" to "hari_ini",
            "sakit-perut" to "sakit_perut",
            "sakit-kepala" to "sakit_kepala"
        )

        val ZH_GLOSS_MAP: Map<String, String> = mapOf(
            "apa_khabar" to "你好",
            "hello" to "你好",
            "hai" to "嗨",
            "encik" to "先生",
            "saya" to "我",
            "boleh" to "可以",
            "tolong" to "帮助",
            "apa" to "什么",
            "hari_ini" to "今天",
            "hari-ini" to "今天",
            "awak" to "你",
            "datang" to "来",
            "hospital" to "医院",
            "kenapa" to "为什么",
            "yang" to "的",
            "doktor" to "医生",
            "klinik" to "诊所",
            "polis" to "警察",
            "terima_kasih" to "谢谢",
            "makan" to "吃",
            "minum" to "喝",
            "tidur" to "睡觉",
            "sakit" to "生病",
            "demam" to "发烧",
            "batuk" to "咳嗽",
            "ubat" to "药物",
            "panas" to "热",
            "sejuk" to "冷",
            "suhu" to "体温",
            "rumah" to "家",
            "sekolah" to "学校",
            "duit" to "钱",
            "kereta" to "汽车",
            "anak" to "孩子",
            "ibu" to "妈妈",
            "bapa" to "爸爸",
            "ayah" to "爸爸",
            "abang" to "哥哥",
            "kakak" to "姐姐",
            "kawan" to "朋友",
            "nama" to "名字",
            "curi" to "偷窃",
            "hilang" to "丢失",
            "tangkap" to "抓捕",
            "bahaya" to "危险",
            "selamat" to "安全",
            "sakit_perut" to "胃痛",
            "sakit_kepala" to "头痛",
            "siapa" to "谁",
            "mana" to "哪里",
            "bila" to "何时",
            "bagaimana" to "如何",
            "gembira" to "开心",
            "cinta" to "爱",
            "belajar" to "学习",
            "baju" to "衣服",
            "air" to "水"
        )

        val TA_GLOSS_MAP: Map<String, String> = mapOf(
            "apa_khabar" to "வணக்கம்",
            "hello" to "வணக்கம்",
            "hai" to "வணக்கம்",
            "encik" to "ஐயா",
            "saya" to "நான்",
            "boleh" to "முடியும்",
            "tolong" to "உதவு",
            "apa" to "என்ன",
            "hari_ini" to "இன்று",
            "hari-ini" to "இன்று",
            "awak" to "நீங்கள்",
            "datang" to "வருதல்",
            "hospital" to "மருத்துவமனை",
            "kenapa" to "ஏன்",
            "yang" to "என்று",
            "doktor" to "மருத்துவர்",
            "klinik" to "கிளினிக்",
            "polis" to "காவல்துறை",
            "terima_kasih" to "நன்றி",
            "makan" to "சாப்பிடு",
            "minum" to "குடி",
            "tidur" to "தூங்கு",
            "sakit" to "நோய்",
            "demam" to "காய்ச்சல்",
            "batuk" to "இருமல்",
            "ubat" to "மருந்து",
            "panas" to "சூடான",
            "sejuk" to "குளிர்",
            "suhu" to "வெப்பநிலை",
            "rumah" to "வீடு",
            "sekolah" to "பள்ளி",
            "duit" to "பணம்",
            "kereta" to "கார்",
            "anak" to "குழந்தை",
            "ibu" to "அம்மா",
            "bapa" to "அப்பா",
            "ayah" to "அப்பா",
            "abang" to "அண்ணன்",
            "kakak" to "அக்கா",
            "kawan" to "நண்பர்",
            "nama" to "பெயர்",
            "curi" to "திருடு",
            "hilang" to "தொலைந்தது",
            "tangkap" to "பிடி",
            "bahaya" to "ஆபத்து",
            "selamat" to "பாதுகாப்பான",
            "sakit_perut" to "வயிற்று வலி",
            "sakit_kepala" to "தலைவலி",
            "siapa" to "யார்",
            "mana" to "எங்கே",
            "bila" to "எப்போது",
            "bagaimana" to "எப்படி",
            "gembira" to "சந்தோஷம்",
            "cinta" to "அன்பு",
            "belajar" to "படி",
            "baju" to "ஆடை",
            "air" to "தண்ணீர்"
        )

        val REVERSE_GLOSS_MAP: Map<String, String> by lazy {
            val map = mutableMapOf<String, String>()
            CATALOG.forEach { item ->
                map[item.key.lowercase()] = item.key
                map[item.title.lowercase()] = item.key
                item.translation.split("/").forEach { tr ->
                    map[tr.trim().lowercase()] = item.key
                }
            }
            ZH_GLOSS_MAP.forEach { (k, v) -> map[v.trim().lowercase()] = k }
            TA_GLOSS_MAP.forEach { (k, v) -> map[v.trim().lowercase()] = k }
            map
        }

        fun findExactMatch(query: String, language: com.isuara.app.service.Language = com.isuara.app.service.Language.MALAY): VocabularyItem? {
            val clean = query.trim().lowercase().replace(Regex("[.,?!;]"), "").replace(Regex("[-\\s]+"), "_")
            val keyFromReverse = REVERSE_GLOSS_MAP[query.trim().lowercase()]
                ?: REVERSE_GLOSS_MAP[clean]
                ?: REVERSE_GLOSS_MAP[clean.replace("_", " ")]
            if (keyFromReverse != null) {
                val item = CATALOG.find { it.key.equals(keyFromReverse, ignoreCase = true) }
                if (item != null) return item
            }
            return CATALOG.find { item ->
                item.key.equals(clean, ignoreCase = true) ||
                item.title.equals(query.trim(), ignoreCase = true) ||
                item.translation.split("/").any { it.trim().equals(query.trim(), ignoreCase = true) } ||
                getLocalizedGloss(item.key, language).equals(query.trim(), ignoreCase = true)
            }
        }

        fun getLocalizedGloss(key: String, language: com.isuara.app.service.Language): String {
            val cleanKey = key.trim().lowercase().replace(Regex("[.,?!;]"), "").replace(Regex("[-\\s]+"), "_")
            val resolvedKey = REVERSE_GLOSS_MAP[cleanKey] ?: cleanKey
            val item = CATALOG.find { it.key.equals(resolvedKey, ignoreCase = true) }
            return when (language) {
                com.isuara.app.service.Language.MANDARIN -> ZH_GLOSS_MAP[resolvedKey] ?: item?.title ?: resolvedKey
                com.isuara.app.service.Language.TAMIL -> TA_GLOSS_MAP[resolvedKey] ?: item?.title ?: resolvedKey
                com.isuara.app.service.Language.ENGLISH -> item?.translation?.split("/")?.firstOrNull()?.trim() ?: item?.title ?: resolvedKey.replace("_", " ").replaceFirstChar { it.uppercase() }
                com.isuara.app.service.Language.MALAY -> item?.title ?: resolvedKey.replace("_", " ").replaceFirstChar { it.uppercase() }
            }
        }
    }

    private val cache = HashMap<String, BimMotion>()

    /**
     * Loads a motion track by vocabulary key, resolving aliases if necessary.
     */
    fun loadMotion(key: String): BimMotion? {
        val cleanKey = key.trim().lowercase().replace(Regex("[.,?!;]"), "").replace(Regex("[-\\s]+"), "_")
        val resolvedKey = ALIASES[cleanKey] ?: REVERSE_GLOSS_MAP[cleanKey] ?: cleanKey
        val cached = cache[resolvedKey]
        if (cached != null) return cached

        val item = CATALOG.find { it.key.equals(resolvedKey, ignoreCase = true) }
        val fileName = item?.assetFileName ?: if (resolvedKey.endsWith(".json")) resolvedKey else "$resolvedKey.json"

        return try {
            val assetPath = "motions/$fileName"
            val stream: InputStream = context.assets.open(assetPath)
            val motion = MotionParser.parseStream(stream)
            cache[resolvedKey] = motion
            motion
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load motion for key '$key' -> '$resolvedKey' ($fileName)", e)
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
     * Synthesizes a continuous sentence by joining multiple vocabulary words with a fast 8-frame co-articulation bridge.
     */
    fun synthesizeSentence(words: List<String>): BimMotion? {
        val normalizedWords = words.map {
            it.trim().lowercase().replace(Regex("[.,?!;]"), "").replace(Regex("[-\\s]+"), "_")
        }

        // Direct precompiled sentence check
        val joinedKey = normalizedWords.joinToString("_")
        val precompiled = when {
            joinedKey == "encik_saya_boleh_tolong_apa" || (joinedKey.contains("encik") && joinedKey.contains("tolong") && joinedKey.contains("apa")) ->
                "sentence_1_bim_encik_saya_boleh_tolong_apa"
            joinedKey == "apa_khabar_hari_ini_awak_datang_hospital_kenapa" || (joinedKey.contains("hospital") && (joinedKey.contains("kenapa") || joinedKey.contains("datang"))) ->
                "sentence_2_bim_apa_khabar_hari_ini_awak_datang_hospital_kenapa"
            joinedKey == "encik_apa_yang_saya_boleh_tolong" ->
                "sentence_1_ktbm_encik_apa_yang_saya_boleh_tolong"
            joinedKey == "apa_khabar_kenapa_datang_hospital_hari_ini" ->
                "sentence_2_ktbm_apa_khabar_kenapa_datang_hospital_hari_ini"
            else -> null
        }
        if (precompiled != null) {
            val motion = loadMotion(precompiled)
            if (motion != null) return motion
        }

        val motions = normalizedWords.mapNotNull { loadMotion(it) }
        if (motions.isEmpty()) return null
        if (motions.size == 1) return motions[0]

        val combinedFrames = ArrayList<BimFrame>()
        val fps = 55f
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

            // If there is a next word, insert an 8-frame smooth co-articulation bridge
            if (i < motions.size - 1) {
                val lastFrame = m.frames.lastOrNull()
                val nextMotion = motions[i + 1]
                val firstFrameNext = nextMotion.frames.firstOrNull()

                if (lastFrame != null && firstFrameNext != null) {
                    val bridgeCount = 8
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
