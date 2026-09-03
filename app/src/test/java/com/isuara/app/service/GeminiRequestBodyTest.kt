package com.isuara.app.service

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shape of the Gemini request body.
 *
 * Worth testing without a network or a device because the placement of the
 * system prompt is a silent invariant: putting it in the user turn still
 * returns a perfectly good translation, so nothing fails loudly — it just
 * destroys the property that makes candidates comparable, and the prefix stops
 * being a stable cache key.
 */
class GeminiRequestBodyTest {

    private fun body(
        system: String = "SYS",
        user: String = "USR",
        temperature: Double = 1.0,
    ) = JSONObject(buildGeminiBody(system, user, temperature))

    @Test
    fun `system prompt goes in systemInstruction, not the user turn`() {
        val json = body(system = TranslationPrompts.SYSTEM, user = "Input: [Polis]")

        val systemText = json.getJSONObject("systemInstruction")
            .getJSONArray("parts").getJSONObject(0).getString("text")
        assertEquals(TranslationPrompts.SYSTEM, systemText)

        val userText = json.getJSONArray("contents").getJSONObject(0)
            .getJSONArray("parts").getJSONObject(0).getString("text")
        assertEquals("Input: [Polis]", userText)
        assertFalse("system prompt must not be duplicated into the user turn",
            userText.contains("professional Bahasa Isyarat Malaysia"))
    }

    @Test
    fun `user turn is tagged with the user role`() {
        assertEquals("user", body().getJSONArray("contents").getJSONObject(0).getString("role"))
    }

    /**
     * Zero is the judge's temperature and the one most at risk: a config that
     * drops falsy values would silently promote the judge to sampling.
     */
    @Test
    fun `temperature round-trips, including zero`() {
        val config = { t: Double -> body(temperature = t).getJSONObject("generationConfig") }

        assertEquals(1.0, config(1.0).getDouble("temperature"), 0.0)
        assertTrue("temperature must be present at 0.0", config(0.0).has("temperature"))
        assertEquals(0.0, config(0.0).getDouble("temperature"), 0.0)
    }

    @Test
    fun `output budget and JSON constraint are set`() {
        val config = body().getJSONObject("generationConfig")
        assertTrue(config.getInt("maxOutputTokens") >= 1024)
        assertEquals("application/json", config.getString("responseMimeType"))
    }
}
