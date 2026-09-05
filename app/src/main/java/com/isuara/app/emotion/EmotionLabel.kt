package com.isuara.app.emotion

/**
 * The eight AffectNet expression classes predicted by the EmotiEffLib model.
 *
 * Declaration order IS the model's output index order — taken verbatim from
 * `idx_to_emotion_class` in EmotiEffLib's `facial_analysis.py`. Reordering these
 * entries silently mislabels every prediction, so treat the order as part of the
 * model contract rather than as a stylistic choice.
 *
 * Each entry carries what the two downstream stages need. [descriptorMs] and
 * [descriptorEn] go into the LLM prompt; [styleDirective] goes to Gemini-TTS;
 * [pitch] and [rateScale] drive the offline Android TTS fallback. Keeping them
 * on the enum means adding a ninth class later is one edit, not a hunt through
 * five `when` blocks.
 */
enum class EmotionLabel(
    /**
     * Circumplex arousal, 0 (calm) to 1 (activated). Gates register and prosody
     * so callers ask "how activated is this?" rather than switching on eight
     * discrete names — the distinction that actually matters downstream is
     * arousal, not identity.
     */
    val arousal: Float,
    /** Circumplex valence, -1 (negative) to +1 (positive). */
    val valence: Float,
    /** Malay descriptor handed to the translator prompt. */
    val descriptorMs: String,
    /** English descriptor handed to the translator prompt. */
    val descriptorEn: String,
    /** Visual emoji representation of the emotion. */
    val emoji: String = "",
    /**
     * Absolute pitch for the offline `TextToSpeech` fallback; 1.0 is the
     * engine's normal pitch.
     */
    val pitch: Float,
    /**
     * Multiplier on TtsService's base speech rate rather than an absolute rate,
     * so the app's deliberate "slightly slow for clarity" baseline survives the
     * emotion adjustment.
     */
    val rateScale: Float,
    /**
     * The Gemini-TTS style prompt. Deliberately English even though the spoken
     * text is Malay — Google's own guidance is that style prompts work best in
     * English regardless of the target language.
     */
    val styleDirective: String,
) {
    ANGER(
        arousal = 0.85f, valence = -0.70f,
        descriptorMs = "marah", descriptorEn = "anger",
        emoji = "😠",
        pitch = 0.92f, rateScale = 1.12f,
        styleDirective = "Say this angrily and firmly, with clipped, forceful delivery.",
    ),
    CONTEMPT(
        arousal = 0.45f, valence = -0.40f,
        descriptorMs = "meluat", descriptorEn = "contempt",
        emoji = "😒",
        pitch = 0.95f, rateScale = 0.95f,
        styleDirective = "Say this with cold, dismissive disdain.",
    ),
    DISGUST(
        // Below the high-arousal threshold on purpose: disgust is an intense
        // feeling but not an urgent one, and it should not trigger the
        // shouted-colloquial register that fear and anger do.
        arousal = 0.55f, valence = -0.60f,
        descriptorMs = "jijik", descriptorEn = "disgust",
        emoji = "🤢",
        pitch = 0.90f, rateScale = 0.95f,
        styleDirective = "Say this with revulsion, as if recoiling from something.",
    ),
    FEAR(
        arousal = 0.90f, valence = -0.80f,
        descriptorMs = "takut", descriptorEn = "fear",
        emoji = "😨",
        pitch = 1.18f, rateScale = 1.25f,
        styleDirective = "Say this urgently and fearfully, fast, with a strained, breathless voice.",
    ),
    HAPPINESS(
        arousal = 0.70f, valence = 0.80f,
        descriptorMs = "gembira", descriptorEn = "happiness",
        emoji = "😊",
        pitch = 1.12f, rateScale = 1.05f,
        styleDirective = "Say this warmly and brightly, with an audible smile.",
    ),
    NEUTRAL(
        arousal = 0.25f, valence = 0.0f,
        descriptorMs = "neutral", descriptorEn = "neutral",
        emoji = "😐",
        pitch = 1.0f, rateScale = 1.0f,
        styleDirective = "Say this in a calm, clear, matter-of-fact voice.",
    ),
    SADNESS(
        arousal = 0.30f, valence = -0.60f,
        descriptorMs = "sedih", descriptorEn = "sadness",
        emoji = "😢",
        pitch = 0.88f, rateScale = 0.82f,
        styleDirective = "Say this softly and heavily, slowly, with a downcast tone.",
    ),
    SURPRISE(
        arousal = 0.80f, valence = 0.20f,
        descriptorMs = "terkejut", descriptorEn = "surprise",
        emoji = "😲",
        pitch = 1.20f, rateScale = 1.10f,
        styleDirective = "Say this with sudden surprise, sharp and raised in pitch.",
    );

    /**
     * The single predicate gating both the colloquial Malay register and the
     * prosody boost.
     *
     * Kept here rather than at the two call sites so "what counts as urgent"
     * has exactly one definition — the two must not be able to drift apart.
     */
    val isHighArousal: Boolean get() = arousal >= HIGH_AROUSAL_THRESHOLD

    fun localizedName(language: com.isuara.app.service.Language): String = when (language) {
        com.isuara.app.service.Language.MALAY -> descriptorMs
        com.isuara.app.service.Language.ENGLISH -> descriptorEn
        com.isuara.app.service.Language.MANDARIN -> when (this) {
            ANGER -> "愤怒"
            CONTEMPT -> "轻蔑"
            DISGUST -> "厌恶"
            FEAR -> "害怕"
            HAPPINESS -> "开心"
            NEUTRAL -> "平静"
            SADNESS -> "悲伤"
            SURPRISE -> "惊讶"
        }
        com.isuara.app.service.Language.TAMIL -> when (this) {
            ANGER -> "கோபம்"
            CONTEMPT -> "அலட்சியம்"
            DISGUST -> "அருவருப்பு"
            FEAR -> "பயம்"
            HAPPINESS -> "மகிழ்ச்சி"
            NEUTRAL -> "அமைதி"
            SADNESS -> "சோகம்"
            SURPRISE -> "ஆச்சரியம்"
        }
    }

    companion object {
        const val HIGH_AROUSAL_THRESHOLD = 0.60f

        /** Number of classes the model emits; asserted against the ONNX output. */
        const val COUNT = 8

        /**
         * The label for a model output index, or null if [index] is out of
         * range — a model whose output width does not match [COUNT] must fail
         * loudly rather than silently wrapping onto a wrong label.
         */
        fun fromIndex(index: Int): EmotionLabel? = entries.getOrNull(index)
    }
}
