package com.isuara.app.service

import android.util.Log
import com.google.genai.Client
import com.isuara.app.BuildConfig

/**
 * The pool of Gemini clients — one per configured API key.
 *
 * A key is bound at client construction, so giving each debate agent its own key
 * means giving each its own [Client]. That is the point: the free-tier limit is
 * 5 requests/minute *per Google Cloud project*, and the debate costs 4 calls per
 * translation, so a single key allows barely one translation a minute. Three
 * keys from three separate projects give three independent buckets.
 *
 * Keys come from local.properties via BuildConfig; see app/build.gradle.kts.
 * Keys issued from the *same* project share one bucket and buy nothing.
 *
 * Each client owns an OkHttp connection pool and a thread pool, which is the
 * real cost here — so the pool is built once, lazily, and shared. Never
 * construct a client per request. Build it off the main thread: the dependency
 * graph is heavy enough that loading it during startup trips Android's
 * process-attach watchdog.
 */
object GeminiClients {

    private const val TAG = "GeminiClients"

    private val keys: List<String> by lazy {
        listOf(
            BuildConfig.GEMINI_API_KEY_1,
            BuildConfig.GEMINI_API_KEY_2,
            BuildConfig.GEMINI_API_KEY_3,
        ).map { it.trim() }.filter { it.isNotEmpty() }
    }

    val all: List<Client> by lazy {
        when (keys.size) {
            0 -> Log.w(TAG, "No Gemini API key configured — translation disabled")
            3 -> Log.i(TAG, "3 Gemini keys configured (~15 req/min if separate projects)")
            // Still works, just with proportionally less quota, so say so loudly
            // rather than letting it surface later as mysterious 429s.
            else -> Log.w(
                TAG,
                "Only ${keys.size} of 3 Gemini keys configured — agents will share " +
                    "keys and quota. Add GEMINI_API_KEY_2/_3 to local.properties."
            )
        }
        keys.map { Client.builder().apiKey(it).build() }
    }

    val isConfigured: Boolean get() = keys.isNotEmpty()

    /**
     * How many keys are configured. Reads BuildConfig only — deliberately does
     * NOT touch [all], so callers can do slot arithmetic on the main thread
     * without triggering client construction.
     */
    val slotCount: Int get() = keys.size

    /**
     * The client for agent/judge slot [index], wrapping when fewer keys are set.
     *
     * Forces the pool to build, so call this from a background thread only.
     */
    fun forSlot(index: Int): Client {
        require(all.isNotEmpty()) { "no Gemini API key configured" }
        return all[index % all.size]
    }

    /** Which key slot [index] resolves to — cheap, for logging and assignment. */
    fun slotOf(index: Int): Int = if (slotCount == 0) -1 else index % slotCount
}
