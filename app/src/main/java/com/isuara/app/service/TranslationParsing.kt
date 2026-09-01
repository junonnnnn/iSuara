package com.isuara.app.service

import org.json.JSONObject

/**
 * Provider-neutral parsing of LLM replies.
 *
 * Lives apart from any one translator because every provider needs the same
 * tolerance: models wrap objects in markdown fences and some prepend a visible
 * reasoning monologue (the latency benchmark saw one leak `<think>` blocks on
 * 10/10 runs). Both are handled by taking the outermost brace-delimited span.
 */
object TranslationParsing {

    /** The outermost `{...}` span, or null when there isn't one. */
    private fun jsonSpan(raw: String): String? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        return if (start >= 0 && end > start) raw.substring(start, end + 1) else null
    }

    private fun parse(raw: String, what: String): JSONObject {
        val span = jsonSpan(raw)
        require(span != null) { "no JSON object in $what: ${raw.take(200)}" }
        return try {
            JSONObject(span)
        } catch (e: Exception) {
            throw IllegalArgumentException("malformed JSON in $what: ${raw.take(200)}", e)
        }
    }

    /**
     * Pulls the four-language translation out of a raw model reply.
     *
     * @throws IllegalArgumentException if no complete object is present or any
     *   of the four languages is missing or blank.
     */
    fun extractTranslation(raw: String): Translation {
        val obj = parse(raw, "reply")
        val ms = obj.optString("ms").trim()
        val en = obj.optString("en").trim()
        val zh = obj.optString("zh").trim()
        val ta = obj.optString("ta").trim()
        require(ms.isNotEmpty() && en.isNotEmpty() && zh.isNotEmpty() && ta.isNotEmpty()) {
            "missing language in reply: ms=${ms.isNotEmpty()} en=${en.isNotEmpty()} " +
                "zh=${zh.isNotEmpty()} ta=${ta.isNotEmpty()}"
        }
        return Translation(ms = ms, en = en, zh = zh, ta = ta)
    }

    /**
     * Pulls the judge's choice out of a raw reply.
     *
     * @param candidateCount how many candidates were offered, used to bound the
     *   index — a judge that names a candidate we never sent must not select one.
     * @throws IllegalArgumentException if no object is present, the choice is
     *   missing or not an integer, or the index is out of range.
     */
    fun extractChoice(raw: String, candidateCount: Int): Pair<Int, String> {
        val obj = parse(raw, "judge reply")
        require(obj.has("choice")) { "judge reply has no choice: ${raw.take(200)}" }
        val choice = obj.optInt("choice", -1)
        require(choice in 0 until candidateCount) {
            "judge chose $choice, outside 0..${candidateCount - 1}"
        }
        return choice to obj.optString("reason").trim()
    }
}
