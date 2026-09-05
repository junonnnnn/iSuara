package com.isuara.app.avatar.grammar

/**
 * Result of AI reasoning transforming natural spoken text into BIM sign grammar.
 */
data class GrammarCandidate(
    val model: String,
    val tokens: List<String>,
    val displayTokens: List<String>,
    val reasoning: String,
    val requestId: String? = null,
    val isWinner: Boolean = false
)

data class GrammarVerdict(
    val judgeModel: String,
    val reason: String,
    val choice: Int = 0,
    val requestId: String? = null
)

data class SignGrammarResult(
    val reasoning: String,
    val tokens: List<String>,
    val displayTokens: List<String>,
    val model: String = "Gonka Multi-Model",
    val candidates: List<GrammarCandidate> = emptyList(),
    val verdict: GrammarVerdict? = null
)

