package com.isuara.app.service

import org.junit.Assert.assertEquals
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
}
