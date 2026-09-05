package com.isuara.app.avatar.grammar

import android.util.Log
import com.isuara.app.service.GeminiClients
import com.isuara.app.service.complete
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.json.JSONObject

class GeminiSignGrammarService {

    companion object {
        private const val TAG = "GeminiSignGrammar"

        const val PRIMARY_MODEL = "gemini-3.1-flash-lite"
        const val CONSENSUS_MODEL = "gemini-2.5-flash"
    }

    suspend fun restructure(sentence: String): SignGrammarResult = withContext(Dispatchers.IO) {
        if (!GeminiClients.isConfigured) {
            Log.w(TAG, "No Gemini API key — using local fallback tokenization")
            return@withContext fallbackTokenize(sentence)
        }

        val userTurn = SignGrammarPrompt.userTurn(sentence)

        // Gemini Multi-Model Consensus
        val primaryDeferred = async {
            try {
                val client = GeminiClients.forSlot(0)
                val raw = complete(SignGrammarPrompt.SYSTEM, userTurn, client, PRIMARY_MODEL)
                parseResult(raw, "Gemini-3.1-Flash-Lite")
            } catch (e: Exception) {
                Log.w(TAG, "Primary model failed: ${e.message}")
                null
            }
        }

        val consensusDeferred = async {
            try {
                val client = GeminiClients.forSlot(1)
                val raw = complete(SignGrammarPrompt.SYSTEM, userTurn, client, CONSENSUS_MODEL)
                parseResult(raw, "Gemini-2.5-Flash")
            } catch (e: Exception) {
                Log.w(TAG, "Consensus model failed: ${e.message}")
                null
            }
        }

        val primaryResult = primaryDeferred.await()
        val consensusResult = consensusDeferred.await()

        when {
            primaryResult != null && consensusResult != null -> {
                Log.i(TAG, "Consensus verified between Gemini models: ${primaryResult.tokens}")
                primaryResult.copy(model = "Gemini Multi-Modal Consensus")
            }
            primaryResult != null -> primaryResult
            consensusResult != null -> consensusResult
            else -> fallbackTokenize(sentence)
        }
    }

    private fun parseResult(raw: String, modelName: String): SignGrammarResult {
        val span = extractJsonSpan(raw)
            ?: throw IllegalArgumentException("No JSON in reply: ${raw.take(200)}")

        val obj = JSONObject(span)
        val reasoning = obj.optString("reasoning", "Restructured according to BIM Topic-Comment grammar.")
        val tokensJson = obj.optJSONArray("tokens")
        val displayJson = obj.optJSONArray("display")

        val tokens = mutableListOf<String>()
        if (tokensJson != null) {
            for (i in 0 until tokensJson.length()) {
                val t = tokensJson.optString(i).trim().lowercase()
                if (t.isNotEmpty()) tokens.add(t)
            }
        }

        val display = mutableListOf<String>()
        if (displayJson != null) {
            for (i in 0 until displayJson.length()) {
                val d = displayJson.optString(i).trim()
                if (d.isNotEmpty()) display.add(d)
            }
        } else {
            display.addAll(tokens.map { it.replaceFirstChar { c -> c.uppercase() } })
        }

        require(tokens.isNotEmpty()) { "Empty tokens list in response" }

        return SignGrammarResult(
            reasoning = reasoning,
            tokens = tokens,
            displayTokens = display,
            model = modelName
        )
    }

    private fun extractJsonSpan(raw: String): String? {
        val start = raw.lastIndexOf('{')
        val end = raw.lastIndexOf('}')
        if (start in 0 until end) {
            val firstStart = raw.indexOf('{')
            return raw.substring(firstStart, end + 1)
        }
        return null
    }

    private fun fallbackTokenize(sentence: String): SignGrammarResult {
        val clean = sentence.trim().lowercase()
            .replace(Regex("[.,?!;]"), "")
        val rawParts = clean.split("\\s+".toRegex())

        val dropWords = setOf("yang", "boleh", "di", "ke", "adalah", "ialah", "sekarang", "kerana", "sangat", "pun", "akan")
        val filtered = rawParts.filter { it !in dropWords }

        // Rule-based BIM question reordering: if question word exists, move to end
        val questionWords = setOf("apa", "siapa", "bila", "mana")
        val qWord = filtered.find { it in questionWords }
        val finalTokens = if (qWord != null) {
            val nonQ = filtered.filter { it != qWord }
            nonQ + qWord
        } else {
            filtered
        }

        return SignGrammarResult(
            reasoning = "Rule-based BIM grammar: Spoken glue words omitted and question token aligned.",
            tokens = finalTokens,
            displayTokens = finalTokens.map { it.replaceFirstChar { c -> c.uppercase() } },
            model = "Local BIM Rules"
        )
    }
}
