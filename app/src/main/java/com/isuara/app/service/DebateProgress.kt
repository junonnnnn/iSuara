package com.isuara.app.service

/** The judge's decision about a set of candidates. */
data class JudgeVerdict(
    val choice: Int,
    val reason: String,
)

/** One agent's slot in the debate, from pending through to answered or failed. */
data class CandidateView(
    /**
     * The agent's display label, from [GeminiTranslator.AGENT_LABELS].
     *
     * Display text, not a model id — never match on it to route a call.
     */
    val model: String,
    /** Null while the model is still working. */
    val sentence: String? = null,
    val failed: Boolean = false,
) {
    val isPending: Boolean get() = sentence == null && !failed

    /**
     * Display form of [model]. Strips a `vendor/` prefix when there is one,
     * which the current labels do not have — kept so an id-style label still
     * renders sensibly if a provider with slash-qualified ids is added back.
     */
    val shortName: String get() = model.substringAfterLast('/')
}

/**
 * Everything the UI needs to show the debate as it happens.
 *
 * Exists because [TranslationStage] is a flat enum and cannot carry the
 * candidates. The point is that agents are revealed **as they arrive** rather
 * than after the slowest finishes, so one slow agent cannot hold the others'
 * answers off the screen.
 */
data class DebateProgress(
    val stage: TranslationStage = TranslationStage.IDLE,
    val candidates: List<CandidateView> = emptyList(),
    val verdict: JudgeVerdict? = null,
) {
    /** True once every agent has either answered or failed. */
    val allResolved: Boolean get() = candidates.isNotEmpty() && candidates.none { it.isPending }

    val isActive: Boolean get() = stage != TranslationStage.IDLE
}
