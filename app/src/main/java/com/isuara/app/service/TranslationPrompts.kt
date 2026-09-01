package com.isuara.app.service

/**
 * Prompts shared by every debating model.
 *
 * Kept in one place so all three agents are asked for exactly the same thing.
 * That is what makes the comparison meaningful: any difference between
 * candidates is attributable to the model, not to a differing prompt.
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

}
