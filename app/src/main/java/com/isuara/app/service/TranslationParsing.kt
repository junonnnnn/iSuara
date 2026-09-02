package com.isuara.app.service

import org.json.JSONObject

/**
 * Provider-neutral parsing of LLM replies.
 *
 * Lives apart from any one translator because every provider needs the same
 * tolerance: models wrap objects in markdown fences and some prepend a visible
 * reasoning monologue (the latency benchmark saw one leak `<think>` blocks on
 * 10/10 runs). Both are handled by scanning for the last brace-balanced object.
 */
object TranslationParsing {

    /**
     * The last complete, brace-balanced `{...}` object in [raw], or null.
     *
     * Deliberately not "first { to last }": MiniMax leaked a visible <think>
     * monologue on 10 of 10 benchmark runs, and if that prose contains a brace
     * the naive span swallows it and parsing fails — which would silently drop
     * that model out of the debate. A single pass tracking string state, escapes
     * and depth finds the real object regardless of what precedes it.
     *
     * Takes the LAST object so that a model which restates the prompt's example
     * before answering does not have its example picked as the answer.
     */
    private fun jsonSpan(raw: String): String? {
        var depth = 0
        var start = -1
        var last: String? = null
        var inString = false
        var escaped = false

        for (i in raw.indices) {
            val c = raw[i]
            when {
                escaped -> escaped = false
                inString && c == '\\' -> escaped = true
                c == '"' -> inString = !inString
                inString -> Unit
                c == '{' -> {
                    if (depth == 0) start = i
                    depth++
                }
                c == '}' -> {
                    if (depth > 0) {
                        depth--
                        if (depth == 0 && start >= 0) last = raw.substring(start, i + 1)
                    }
                }
            }
        }
        return last
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
     * The `emotion` and `style` keys are deliberately NOT required. They are an
     * enrichment: a model that omits them, or that predates the prompt asking
     * for them, must still yield a usable translation. Requiring them would turn
     * a cosmetic shortfall into a total failure and drop that model out of the
     * debate entirely.
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
        return Translation(
            ms = ms,
            en = en,
            zh = zh,
            ta = ta,
            emotion = obj.optString("emotion").trim().ifEmpty { null },
            style = obj.optString("style").trim().ifEmpty { null },
        )
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
