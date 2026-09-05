package com.isuara.app.service

/** The judge's decision about a set of candidates. */
data class JudgeVerdict(
    val choice: Int,
    val reason: String,
)

/** One agent's slot in the debate, from pending through to answered or failed. */
data class CandidateView(
    /** The model id, e.g. `deepseek-ai/DeepSeek-V4-Flash-0731`. */
    val model: String,
    /** Full translation in all four languages once answered. */
    val translation: Translation? = null,
    /** Null while the model is still working. */
    val sentence: String? = null,
    val failed: Boolean = false,
) {
    val isPending: Boolean get() = translation == null && sentence == null && !failed

    /** Just the model name, without the vendor prefix, for display. */
    val shortName: String get() = model.substringAfterLast('/')

    fun sentenceFor(language: Language): String? =
        translation?.forLanguage(language) ?: sentence
}

/**
 * Everything the UI needs to show the debate as it happens.
 *
 * Exists because [TranslationStage] is a flat enum and cannot carry the
 * candidates. The point is that agents are revealed **as they arrive** rather
 * than after the slowest finishes — with a measured spread from ~9s to ~126s
 * across the three models, batching the reveal means minutes of dead air.
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
