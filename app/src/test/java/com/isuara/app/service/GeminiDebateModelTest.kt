package com.isuara.app.service

import org.junit.Assert.assertEquals
import org.junit.Test

class GeminiDebateModelTest {

    @Test
    fun `default model is 3_1-flash-lite and all 3 requests use 3_1-flash-lite`() {
        assertEquals("gemini-3.1-flash-lite", GeminiTranslator.MODEL)
        assertEquals(
            listOf("gemini-3.1-flash-lite", "gemini-3.1-flash-lite", "gemini-3.1-flash-lite"),
            GeminiTranslator.AGENT_MODELS,
        )
        assertEquals(
            listOf("DeepSeek V4-Flash", "MiniMax M2.7", "Kimi K2.6"),
            GeminiTranslator.AGENT_LABELS,
        )
    }

    @Test
    fun `geminiDebate creates agents all using 3_1-flash-lite`() {
        val debate = geminiDebate(listOf("key-1", "key-2", "key-3"))
        assertEquals(3, debate.agents.size)

        val agentModels = debate.agents.map { (it as GeminiTranslator).modelId }
        assertEquals(
            listOf("gemini-3.1-flash-lite", "gemini-3.1-flash-lite", "gemini-3.1-flash-lite"),
            agentModels,
        )

        assertEquals(
            listOf("DeepSeek V4-Flash", "MiniMax M2.7", "Kimi K2.6"),
            debate.agentLabels,
        )
    }

    @Test
    fun `geminiDebate handles single key configuration`() {
        val debate = geminiDebate(listOf("key-1"))
        assertEquals(1, debate.agents.size)
        assertEquals("gemini-3.1-flash-lite", (debate.agents[0] as GeminiTranslator).modelId)
        assertEquals(listOf("DeepSeek V4-Flash"), debate.agentLabels)
    }
}
