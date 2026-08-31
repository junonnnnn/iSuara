package com.isuara.app.service

/**
 * One sentence rendered in every supported language.
 *
 * The translator returns all four in a single call, so switching the display
 * language afterwards is instant and costs nothing.
 */
data class Translation(
    val ms: String,
    val en: String,
    val zh: String,
    val ta: String,
) {
    fun forLanguage(language: Language): String = when (language) {
        Language.MALAY -> ms
        Language.ENGLISH -> en
        Language.MANDARIN -> zh
        Language.TAMIL -> ta
    }


    companion object {
        /** Fallback used when no translator is configured or the call fails. */
        fun ofRawGlosses(text: String) =
            Translation(ms = text, en = text, zh = text, ta = text)
    }
}

/**
 * Turns an ordered list of detected BIM glosses into a natural sentence.
 *
 * Implementations throw on failure rather than returning an error string, so a
 * caller can tell a real translation from a failure without inspecting the text.
 *
 * The planned multi-model voting layer will implement this over several
 * delegates. Since delegates can now disagree per language, the simplest rule
 * is to vote on [Translation.ms] and take the winning delegate's whole result,
 * keeping the three languages mutually consistent.
 */
interface Translator {
    suspend fun translate(words: List<String>): Translation
}
