package com.isuara.app.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reading the text out of a Gemini 200.
 *
 * A 200 does not guarantee text. The TTS spike recorded the audio version of
 * this — `finishReason: OTHER` with no content part at all — and the text side
 * has more ways to reach it (`MAX_TOKENS`, `SAFETY`, `RECITATION`). Each must
 * fail as a cleanly failed agent, which [DebateTranslator] already tolerates,
 * rather than as an NPE from optimistic navigation.
 */
class GeminiReplyExtractionTest {

    private fun failureFor(reply: String): String =
        runCatching { extractGeminiText(reply) }
            .exceptionOrNull()
            .let { it ?: error("expected a failure, got a value") }
            .message
            .orEmpty()

    @Test
    fun `reads the text of a normal reply`() {
        val reply = """
            {"candidates":[{"content":{"parts":[{"text":"{\"ms\":\"Saya lapar\"}"}]},
             "finishReason":"STOP"}]}
        """.trimIndent()
        assertEquals("""{"ms":"Saya lapar"}""", extractGeminiText(reply))
    }

    /** Thinking tokens eat the output budget, and the reply then carries no text. */
    @Test
    fun `MAX_TOKENS with no content names the finish reason`() {
        val detail = failureFor("""{"candidates":[{"finishReason":"MAX_TOKENS"}]}""")
        assertTrue("should name the finish reason, was: $detail", detail.contains("MAX_TOKENS"))
    }

    @Test
    fun `a blank text part is a failure, not an empty translation`() {
        val detail = failureFor(
            """{"candidates":[{"content":{"parts":[{"text":"  "}]},"finishReason":"SAFETY"}]}"""
        )
        assertTrue("should name the finish reason, was: $detail", detail.contains("SAFETY"))
    }

    @Test
    fun `an empty candidate list fails rather than indexing`() {
        failureFor("""{"candidates":[]}""")
        failureFor("""{}""")
    }

    /** Long replies can arrive split across parts; the answer is their concatenation. */
    @Test
    fun `concatenates multiple text parts`() {
        val reply = """
            {"candidates":[{"content":{"parts":[{"text":"{\"ms\":"},{"text":"\"Hai\"}"}]}}]}
        """.trimIndent()
        assertEquals("""{"ms":"Hai"}""", extractGeminiText(reply))
    }

    /** Thinking is requested off, but a model that ignores that must not corrupt the answer. */
    @Test
    fun `skips thought parts`() {
        val reply = """
            {"candidates":[{"content":{"parts":[
              {"text":"Let me think about the glosses...","thought":true},
              {"text":"{\"ms\":\"Saya pergi\"}"}]}}]}
        """.trimIndent()
        assertEquals("""{"ms":"Saya pergi"}""", extractGeminiText(reply))
    }
}
