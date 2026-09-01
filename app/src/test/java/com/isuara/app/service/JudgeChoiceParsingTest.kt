package com.isuara.app.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers [TranslationParsing.extractChoice] against the shapes models actually
 * return, and the bounds that stop a bad index selecting a candidate that does
 * not exist. Pure parsing — no network, no API cost.
 */
class JudgeChoiceParsingTest {

    @Test
    fun `parses a clean choice`() {
        val (choice, reason) = TranslationParsing.extractChoice(
            """{"choice": 1, "reason": "Most natural Malay without adding detail."}""", 3
        )
        assertEquals(1, choice)
        assertTrue(reason.contains("natural"))
    }

    @Test
    fun `strips markdown code fences`() {
        val (choice, _) = TranslationParsing.extractChoice(
            """
            ```json
            {"choice": 2, "reason": "Best reflects urgency."}
            ```
            """.trimIndent(),
            3
        )
        assertEquals(2, choice)
    }

    /** MiniMax leaked a <think> monologue on 10/10 benchmark runs. */
    @Test
    fun `ignores a leading reasoning monologue`() {
        val (choice, _) = TranslationParsing.extractChoice(
            """
            <think>
            Candidate 0 is too literal and drops the implied verb. Candidate 1
            reads naturally. Candidate 2 invents worry that is not in the glosses.
            </think>
            {"choice": 1, "reason": "Faithful and fluent."}
            """.trimIndent(),
            3
        )
        assertEquals(1, choice)
    }

    @Test
    fun `ignores trailing commentary`() {
        val (choice, _) = TranslationParsing.extractChoice(
            """{"choice": 0, "reason": "Adds nothing."} Let me know if you need more.""", 3
        )
        assertEquals(0, choice)
    }

    @Test
    fun `accepts the first and last valid index`() {
        assertEquals(0, TranslationParsing.extractChoice("""{"choice": 0, "reason": "x"}""", 3).first)
        assertEquals(2, TranslationParsing.extractChoice("""{"choice": 2, "reason": "x"}""", 3).first)
    }

    @Test
    fun `rejects an index past the end`() {
        assertThrows(IllegalArgumentException::class.java) {
            TranslationParsing.extractChoice("""{"choice": 3, "reason": "x"}""", 3)
        }
    }

    @Test
    fun `rejects a negative index`() {
        assertThrows(IllegalArgumentException::class.java) {
            TranslationParsing.extractChoice("""{"choice": -1, "reason": "x"}""", 3)
        }
    }

    /** With two surviving agents, index 2 must not be accepted. */
    @Test
    fun `bounds the index to the candidates actually offered`() {
        assertThrows(IllegalArgumentException::class.java) {
            TranslationParsing.extractChoice("""{"choice": 2, "reason": "x"}""", 2)
        }
    }

    @Test
    fun `rejects a missing choice`() {
        assertThrows(IllegalArgumentException::class.java) {
            TranslationParsing.extractChoice("""{"reason": "I like the second one."}""", 3)
        }
    }

    @Test
    fun `rejects a non-integer choice`() {
        assertThrows(IllegalArgumentException::class.java) {
            TranslationParsing.extractChoice("""{"choice": "the second one", "reason": "x"}""", 3)
        }
    }

    @Test
    fun `rejects prose with no JSON`() {
        assertThrows(IllegalArgumentException::class.java) {
            TranslationParsing.extractChoice("I think candidate 1 is best.", 3)
        }
    }

    @Test
    fun `tolerates a missing reason`() {
        val (choice, reason) = TranslationParsing.extractChoice("""{"choice": 1}""", 3)
        assertEquals(1, choice)
        assertEquals("", reason)
    }
}
