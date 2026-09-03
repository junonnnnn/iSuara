package com.isuara.app.service

import com.isuara.app.BuildConfig

/**
 * The Gemini API keys.
 *
 * There is no client to share — [GeminiTranslator] and [GeminiTtsService] each
 * open their own HttpURLConnection — so this holds only the keys.
 *
 * How many there are is structural, not a nicety: [geminiDebate] runs one agent
 * per key, because the debate fans out concurrently and three simultaneous
 * requests on a single key is the one arrangement guaranteed to hit the
 * per-minute quota.
 */
object GeminiKeys {

    /**
     * Comma-joined at build time from `local.properties`; see app/build.gradle.kts.
     *
     * Blank entries are dropped so a half-filled properties file cannot hand an
     * agent an empty key, which would fail every call it makes.
     */
    val all: List<String> by lazy {
        BuildConfig.GEMINI_API_KEYS.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    val isConfigured: Boolean get() = all.isNotEmpty()
}
