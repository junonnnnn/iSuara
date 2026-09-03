package com.isuara.app.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers [TranslationParsing.extractTranslation] against the shapes models
 * actually return. Pure parsing — no network, no API cost — so this runs as
 * part of the normal `gradlew test`.
 */
class TranslationParsingTest {

    @Test
    fun `parses a clean JSON object`() {
        val t = TranslationParsing.extractTranslation(
            """{"ms": "Polis datang ke rumah kami.", "en": "The police came to our house.", "zh": "警察来到了我们家。", "ta": "காவல்துறை எங்கள் வீட்டிற்கு வந்தது."}"""
        )
        assertEquals("Polis datang ke rumah kami.", t.ms)
        assertEquals("The police came to our house.", t.en)
        assertEquals("警察来到了我们家。", t.zh)
    }

    @Test
    fun `strips markdown code fences`() {
        val t = TranslationParsing.extractTranslation(
            """
            ```json
            {"ms": "Saya lapar.", "en": "I am hungry.", "zh": "我饿了。", "ta": "எனக்கு பசிக்கிறது."}
            ```
            """.trimIndent()
        )
        assertEquals("Saya lapar.", t.ms)
        assertEquals("我饿了。", t.zh)
    }

    /** MiniMax leaked a <think> monologue on 10/10 benchmark runs. */
    @Test
    fun `ignores a leading reasoning monologue`() {
        val t = TranslationParsing.extractTranslation(
            """
            <think>
            The glosses are Polis, Rumah, Kami. In BIM the order is topic-comment,
            so this probably means the police came to our house. Let me pick the
            most direct reading.
            </think>
            {"ms": "Polis ke rumah kami.", "en": "Police to our house.", "zh": "警察来我们家。", "ta": "காவல்துறை எங்கள் வீட்டிற்கு வந்தது."}
            """.trimIndent()
        )
        assertEquals("Polis ke rumah kami.", t.ms)
    }

    @Test
    fun `ignores trailing commentary after the object`() {
        val t = TranslationParsing.extractTranslation(
            """{"ms": "Saya faham.", "en": "I understand.", "zh": "我明白。", "ta": "எனக்கு புரிகிறது."} Hope this helps!"""
        )
        assertEquals("Saya faham.", t.ms)
    }

    @Test
    fun `keeps commas and escaped quotes inside a sentence`() {
        val t = TranslationParsing.extractTranslation(
            """{"ms": "Ya, saya \"faham\" sekarang.", "en": "Yes, I \"understand\" now.", "zh": "是的，我现在“明白”了。", "ta": "ஆம், இப்போது எனக்கு புரிகிறது."}"""
        )
        assertEquals("Ya, saya \"faham\" sekarang.", t.ms)
        assertTrue(t.en.contains(","))
    }

    /**
     * The naive "first { to last }" span broke here: a brace inside the
     * reasoning swallowed the whole thing. MiniMax leaks reasoning on every
     * run, so this is the case that keeps it in the debate.
     */
    @Test
    fun `ignores reasoning that itself contains braces`() {
        val t = TranslationParsing.extractTranslation(
            """
            <think>
            The prompt asks for {"ms": ..., "en": ...} so I must emit an object
            with exactly those keys. Let me restate the shape {like this} first.
            </think>
            {"ms": "Saya lapar.", "en": "I am hungry.", "zh": "我饿了。", "ta": "எனக்கு பசிக்கிறது."}
            """.trimIndent()
        )
        assertEquals("Saya lapar.", t.ms)
    }

    @Test
    fun `keeps braces that appear inside a string value`() {
        val t = TranslationParsing.extractTranslation(
            """{"ms": "Kurungan { dan } dalam ayat.", "en": "Braces { and } in a sentence.", "zh": "句子里的 { 和 }。", "ta": "வாக்கியத்தில் { மற்றும் }."}"""
        )
        assertEquals("Kurungan { dan } dalam ayat.", t.ms)
        assertTrue(t.en.contains("{"))
    }

    /** A model that echoes the prompt's example must not have it picked. */
    @Test
    fun `takes the last object when several are present`() {
        val t = TranslationParsing.extractTranslation(
            """
            Example was {"ms": "Contoh.", "en": "Example.", "zh": "例子。", "ta": "எடுத்துக்காட்டு."}
            My answer:
            {"ms": "Saya faham.", "en": "I understand.", "zh": "我明白。", "ta": "எனக்கு புரிகிறது."}
            """.trimIndent()
        )
        assertEquals("Saya faham.", t.ms)
    }

    @Test
    fun `rejects a missing language`() {
        assertThrows(IllegalArgumentException::class.java) {
            TranslationParsing.extractTranslation("""{"ms": "Saya lapar.", "en": "I am hungry."}""")
        }
    }

    @Test
    fun `rejects a reply missing Tamil`() {
        assertThrows(IllegalArgumentException::class.java) {
            TranslationParsing.extractTranslation(
                """{"ms": "Saya lapar.", "en": "I am hungry.", "zh": "我饿了。"}"""
            )
        }
    }

    @Test
    fun `rejects a blank language`() {
        assertThrows(IllegalArgumentException::class.java) {
            TranslationParsing.extractTranslation("""{"ms": "Saya lapar.", "en": "", "zh": "我饿了。", "ta": "எனக்கு பசி."}""")
        }
    }

    @Test
    fun `rejects prose with no JSON at all`() {
        assertThrows(IllegalArgumentException::class.java) {
            TranslationParsing.extractTranslation("Polis means police, rumah means house.")
        }
    }

    @Test
    fun `rejects malformed JSON`() {
        assertThrows(IllegalArgumentException::class.java) {
            TranslationParsing.extractTranslation("""{"ms": "Saya lapar." "en": broken}""")
        }
    }

    @Test
    fun `forLanguage selects the right field`() {
        val t = Translation(ms = "M", en = "E", zh = "Z", ta = "T")
        assertEquals("M", t.forLanguage(Language.MALAY))
        assertEquals("E", t.forLanguage(Language.ENGLISH))
        assertEquals("Z", t.forLanguage(Language.MANDARIN))
        assertEquals("T", t.forLanguage(Language.TAMIL))
    }

    // ---- emotion enrichment -------------------------------------------------

    @Test
    fun `reads the emotion and style fields when present`() {
        val t = TranslationParsing.extractTranslation(
            """{"ms": "Tolong! Cepat!", "en": "Help! Quick!", "zh": "\u6551\u547d!", "ta": "\u0b89\u0ba4\u0bb5\u0bbf!",
                "emotion": "fear", "style": "Say this urgently and fearfully."}"""
        )
        assertEquals("fear", t.emotion)
        assertEquals("Say this urgently and fearfully.", t.style)
    }

    /**
     * The enrichment must never be load-bearing. A model that ignores the new
     * instruction still produced a perfectly good translation, and failing the
     * whole request would drop that model out of the debate for a cosmetic
     * shortfall.
     */
    @Test
    fun `a reply without emotion or style still parses`() {
        val t = TranslationParsing.extractTranslation(
            """{"ms": "Saya lapar.", "en": "I am hungry.", "zh": "\u6211\u997f\u4e86\u3002", "ta": "\u0baa\u0b9a\u0bbf."}"""
        )
        assertEquals("Saya lapar.", t.ms)
        assertNull(t.emotion)
        assertNull(t.style)
    }

    /** Blank is absence, not an empty directive to hand to the voice engine. */
    @Test
    fun `blank emotion and style are normalised to null`() {
        val t = TranslationParsing.extractTranslation(
            """{"ms": "A", "en": "B", "zh": "C", "ta": "D", "emotion": "  ", "style": ""}"""
        )
        assertNull(t.emotion)
        assertNull(t.style)
    }

    @Test
    fun `missing a language still fails even when emotion is present`() {
        assertThrows(IllegalArgumentException::class.java) {
            TranslationParsing.extractTranslation(
                """{"ms": "A", "en": "B", "zh": "C", "emotion": "anger", "style": "Angrily."}"""
            )
        }
    }
}
