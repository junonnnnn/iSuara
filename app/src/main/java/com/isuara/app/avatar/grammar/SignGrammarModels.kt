package com.isuara.app.avatar.grammar

/**
 * Result of AI reasoning transforming natural spoken text into BIM sign grammar.
 */
data class SignGrammarResult(
    val reasoning: String,
    val tokens: List<String>,
    val displayTokens: List<String>,
    val model: String = "Gemini Multi-Modal Reasoning"
)
