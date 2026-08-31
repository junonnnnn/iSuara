package com.isuara.app.service

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.isuara.app.BuildConfig

/**
 * The single Anthropic-protocol client, pointed at GonkaRouter.
 *
 * Deliberately one instance for the whole app: each client owns an OkHttp
 * connection pool and a thread pool. The planned multi-model fan-out should
 * create several [GonkaTranslator]s over this one client, not several clients.
 */
object GonkaClient {

    /** The SDK appends the `/v1/messages` path itself. */
    private const val BASE_URL = "https://api.gonkarouter.io"

    val client: AnthropicClient by lazy {
        AnthropicOkHttpClient.builder()
            .apiKey(BuildConfig.GONKA_API_KEY)
            .baseUrl(BASE_URL)
            .build()
    }
}
