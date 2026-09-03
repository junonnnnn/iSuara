package com.isuara.app.service

import com.isuara.app.emotion.EmotionReading

/**
 * Prompts shared by every debating model.
 *
 * Kept in one place so every agent is asked for exactly the same thing. That is
 * what makes the comparison meaningful: a difference between candidates comes
 * from the agent, and a differing prompt would make it uninterpretable.
 *
 * The observed facial expression goes in the per-request user turn rather than
 * in [SYSTEM], for the same reason: the system prompt stays byte-identical
 * across all three agents and across all requests, so it remains a constant in
 * the comparison and stays cacheable.
 */
object TranslationPrompts {

    val SYSTEM = """
        You are a professional Bahasa Isyarat Malaysia (BIM) sign language interpreter.

        Rules:
        1. Rearrange and expand the BIM keywords (glosses) into a natural Bahasa Melayu sentence (Subject + Verb + Object).
        2. Infer context and add implied verbs (e.g., "rasa," "mahu"), emotions (e.g., "Gembira", "Sedih", "Kecewa", "Maaf") and grammatical particles.
        3. If a phrase is ambiguous, default to the most direct, standard interpretation.
        4. Produce THE SAME sentence in four languages: Bahasa Melayu, English, Simplified Chinese, and Tamil.
        5. Write Tamil in Tamil script, not romanised.
        6. Each language must be ONE single sentence. Do NOT explain your process.

        The input may include the signer's observed facial expression. In sign
        language the face is grammar, not decoration, so when it is given:
        7. Let it shape HOW the sentence is said — word choice, intensity,
           particles, punctuation. It must NEVER add facts the glosses do not
           support. A frightened face on [Polis, Tolong] means "Tolong! Polis!",
           never "Saya takut polis akan menangkap saya."
        8. Register: use standard Bahasa Melayu by default. When the expression is
           high-arousal (fear, anger, urgency, surprise), switch to the natural
           colloquial Malaysian a person would actually shout — "Cepat!",
           "Tolong sikit!", "Jom lah!" — rather than textbook phrasing. Apply the
           same shift in register to the English, Chinese and Tamil sentences so
           all four stay consistent with each other.
        9. Also return two extra fields: "emotion", the tone you rendered, in one
           English word; and "style", a one-sentence ENGLISH instruction to a
           voice actor describing how to deliver the Malay sentence.

        Return ONLY a single JSON object. No markdown, no code fences, no commentary, no reasoning:
        {"ms": "<Bahasa Melayu>", "en": "<English>", "zh": "<Simplified Chinese>", "ta": "<Tamil>", "emotion": "<one English word>", "style": "<one English sentence>"}

        Examples:
        Input: [Polis, Siapa, Salah]
        Output: {"ms": "Siapa yang polis salahkan tadi?", "en": "Who did the police blame just now?", "zh": "警察刚才怪谁?", "ta": "காவல்துறை யாரை குற்றம் சாட்டியது?", "emotion": "neutral", "style": "Say this calmly and clearly, as a straightforward question."}

        Input: [Tolong, Polis, Cepat]
        Observed facial expression: fear (high arousal)
        Output: {"ms": "Tolong! Panggil polis, cepat!", "en": "Help! Call the police, quick!", "zh": "救命!快叫警察!", "ta": "உதவி! சீக்கிரம் போலீஸை கூப்பிடுங்கள்!", "emotion": "fear", "style": "Say this urgently and fearfully, fast and loud, with a strained voice."}
    """.trimIndent()

    /**
     * Builds the per-request user turn.
     *
     * [emotion] is appended as a plain line rather than folded into the gloss
     * list so a model cannot mistake it for another sign to translate.
     */
    fun userTurn(words: List<String>, emotion: EmotionReading? = null): String = buildString {
        append("Input: $words")
        emotionLine(emotion)?.let { append("\n").append(it) }
        append("\nOutput:")
    }

    /**
     * The observed-expression line, or null when nothing was observed.
     *
     * Says "high arousal" rather than a raw number because that is the
     * distinction rule 8 turns on, and models follow a named category far more
     * reliably than a threshold they have to apply themselves. Low-confidence
     * readings are dropped entirely: a hedged hint is worse than no hint, since
     * the model cannot tell how much to discount it.
     */
    private fun emotionLine(emotion: EmotionReading?): String? {
        if (emotion == null || emotion.confidence < MIN_EMOTION_CONFIDENCE) return null
        val arousal = if (emotion.isHighArousal) "high arousal" else "low arousal"
        return "Observed facial expression: ${emotion.label.descriptorEn} ($arousal)"
    }

    /**
     * Below this the reading is too weak to steer a sentence with.
     *
     * The expression classifier returns a full distribution and will always name
     * a winner; when that winner is barely ahead, acting on it would put
     * confident emotion into a sentence on the strength of noise.
     */
    private const val MIN_EMOTION_CONFIDENCE = 0.35f

    val JUDGE = """
        You are evaluating candidate translations of Bahasa Isyarat Malaysia
        (BIM) sign glosses into natural Bahasa Melayu.

        You will be given the original glosses and several candidate sentences,
        each numbered and attributed to the interpreter that produced it. Choose
        the ONE candidate that best represents what the signer most likely
        meant.

        Judge on: faithfulness to the glosses, natural Malay grammar, and
        plausibility as something a person would actually say. Prefer a
        candidate that adds nothing the glosses do not support.

        If an observed facial expression is given, also judge whether the
        candidate's tone and register match it — a flat, textbook sentence is
        the wrong answer for a frightened or angry signer. But a candidate that
        invents events to justify the emotion is worse than a flat one.

        You must choose one of the given candidates. Do NOT write your own
        sentence.

        In your reason, refer to an interpreter by NAME, never by its number —
        the number is only there so you can point at your choice unambiguously.

        Return ONLY a single JSON object. No markdown, no code fences, no
        commentary, no reasoning:
        {"choice": <number of the best candidate>, "reason": "<one short sentence>"}

        Example: {"choice": 1, "reason": "MiniMax M2.7 keeps the urgency without inventing a reason for it."}
    """.trimIndent()

    /**
     * Builds the judge's user turn from the candidates' Malay sentences.
     *
     * Each line carries both a number and the interpreter's name from [labels].
     * The number stays because the verdict is an index — it is what gets
     * bounds-checked and what selects the winning candidate — while the name is
     * what the judge is told to quote in its reason, because that reason is
     * shown to the user and "candidate 1" means nothing on screen.
     *
     * [labels] is positional against [candidates]. A caller holding a wider set
     * of agents must pass only the labels of the ones that actually answered,
     * or the names land on the wrong sentences.
     */
    fun judgeTurn(
        words: List<String>,
        candidates: List<Translation>,
        labels: List<String> = emptyList(),
        emotion: EmotionReading? = null,
    ): String = buildString {
        append("Glosses: $words")
        emotionLine(emotion)?.let { append("\n").append(it) }
        append("\n\nCandidates:\n")
        append(candidates.mapIndexed { i, c ->
            val name = labels.getOrNull(i)?.takeIf { it.isNotBlank() }
            if (name != null) "$i. $name: ${c.ms}" else "$i. ${c.ms}"
        }.joinToString("\n"))
        append("\n\nOutput:")
    }
}
