package com.isuara.app.service

/**
 * Prompts shared by every provider.
 *
 * Kept in one place so the Gemini and GonkaRouter translators cannot drift
 * apart: switching provider must not silently change what the model is asked
 * for, or comparing their output would be meaningless.
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

        Return ONLY a single JSON object. No markdown, no code fences, no commentary, no reasoning:
        {"ms": "<Bahasa Melayu>", "en": "<English>", "zh": "<Simplified Chinese>", "ta": "<Tamil>"}

        Examples:
        Input: [Polis, Siapa, Salah]
        Output: {"ms": "Siapa yang polis salahkan tadi?", "en": "Who did the police blame just now?", "zh": "警察刚才怪谁?", "ta": "காவல்துறை யாரை குற்றம் சாட்டியது?"}
    """.trimIndent()

    /** Builds the per-request user turn. */
    fun userTurn(words: List<String>): String = "Input: $words\nOutput:"

    /**
     * The three interpretive stances for the multi-agent path.
     *
     * They must differ substantively, not just cosmetically. Beyond producing
     * genuinely different readings of ambiguous glosses, distinct prompts also
     * guarantee distinct requests — a provider that caches by request content
     * would otherwise return one answer three times.
     */
    val PERSONAS = listOf(
        "INTERPRETIVE STANCE: Be literal and conservative. Stay as close to the " +
            "given glosses as grammar allows. Add only the particles and function " +
            "words Malay requires. Do not invent context, emotions or details the " +
            "glosses do not state.",
        "INTERPRETIVE STANCE: Prioritise natural, fluent, conversational Malay. " +
            "Expand the glosses into how a native speaker would actually say this " +
            "out loud, adding implied verbs and connectives so the sentence flows.",
        "INTERPRETIVE STANCE: Infer the most plausible real-world situation behind " +
            "these glosses. Consider who is speaking to whom and why, and reflect " +
            "the emotional register — urgency, worry, politeness — in your phrasing.",
    )

    val JUDGE = """
        You are evaluating candidate translations of Bahasa Isyarat Malaysia
        (BIM) sign glosses into natural Bahasa Melayu.

        You will be given the original glosses and several numbered candidate
        sentences. Choose the ONE candidate that best represents what the
        signer most likely meant.

        Judge on: faithfulness to the glosses, natural Malay grammar, and
        plausibility as something a person would actually say. Prefer a
        candidate that adds nothing the glosses do not support.

        You must choose one of the given candidates. Do NOT write your own
        sentence.

        Return ONLY a single JSON object. No markdown, no code fences, no
        commentary, no reasoning:
        {"choice": <index of the best candidate>, "reason": "<one short sentence>"}
    """.trimIndent()

    /** Builds the judge's user turn from the candidates' Malay sentences. */
    fun judgeTurn(words: List<String>, candidates: List<Translation>): String {
        val numbered = candidates.mapIndexed { i, c -> "$i. ${c.ms}" }.joinToString("\n")
        return "Glosses: $words\n\nCandidates:\n$numbered\n\nOutput:"
    }

    /** Appends an interpretive stance to the shared system prompt. */
    fun withPersona(persona: String?): String =
        if (persona.isNullOrBlank()) SYSTEM else listOf(SYSTEM, persona).joinToString("\n\n")
}
