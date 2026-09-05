package com.isuara.app.avatar.grammar

/**
 * Linguistic prompts for transforming spoken language into BIM (Bahasa Isyarat Malaysia)
 * sign grammar for 3D sign avatar synthesis.
 */
object SignGrammarPrompt {

    val SYSTEM = """
        You are an expert in Bahasa Isyarat Malaysia (BIM) sign language grammar.
        Convert a natural spoken sentence into the correct sequence of BIM sign glosses for 3D sign avatar synthesis.

        Linguistic Rules:
        1. Topic-Comment Structure: The topic, patient, or condition comes first, followed by the action or state.
        2. WH-Questions at the End: In BIM question grammar, question words (Apa, Siapa, Bila, Mana) ALWAYS move to the END of the sentence with an inquisitive brow marker.
        3. Omit Spoken Particles & Glue Words: Drop words that have no independent sign in BIM ("yang", "boleh", "di", "ke", "adalah", "ialah", "sekarang", "kerana", "sangat", "pun", "akan").
        4. Normalize to Base Gloss Concepts: Lowercase tokens matching root vocabulary (e.g. "awak", "tolong", "saya", "apa", "anak", "sakit", "suhu", "hospital", "lapar").
        5. Return ONLY a single JSON object (no markdown fences, no commentary):
        {"reasoning": "<short explanation of the BIM grammar rule applied>", "tokens": ["gloss1", "gloss2", ...], "display": ["Gloss1", "Gloss2", ...]}

        Examples:
        Input: "Encik, apa yang saya boleh tolong?"
        Output: {"reasoning": "In BIM question grammar, the addressee [Awak] leads, followed by the action [Tolong] and agent [Saya], while the question word [Apa] moves to the end. Particles 'yang' and 'boleh' are omitted.", "tokens": ["awak", "tolong", "saya", "apa"], "display": ["Awak", "Tolong", "Saya", "Apa"]}

        Input: "Anak awak sakit apa sekarang?"
        Output: {"reasoning": "In BIM medical question syntax, the subject [Anak] and possessor [Awak] lead, followed by condition [Sakit], with the question word [Apa] at the end. Temporal filler 'sekarang' is omitted.", "tokens": ["anak", "awak", "sakit", "apa"], "display": ["Anak", "Awak", "Sakit", "Apa"]}

        Input: "Saya sangat lapar kerana sakit perut"
        Output: {"reasoning": "In BIM, the topic/condition [Sakit Perut] is stated first as the cause, followed by the state [Lapar] and the person [Saya]. The intensifier 'sangat' and connective 'kerana' are omitted.", "tokens": ["sakit_perut", "lapar", "saya"], "display": ["Sakit Perut", "Lapar", "Saya"]}
    """.trimIndent()

    fun userTurn(sentence: String): String = "Input: \"$sentence\"\nOutput:"
}
