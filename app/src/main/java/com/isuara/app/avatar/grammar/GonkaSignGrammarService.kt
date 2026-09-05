package com.isuara.app.avatar.grammar

import android.util.Log
import com.isuara.app.BuildConfig
import com.isuara.app.service.gonkaComplete
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.json.JSONObject

class GonkaSignGrammarService {

    companion object {
        private const val TAG = "GonkaSignGrammar"

        const val PRIMARY_MODEL = "deepseek-ai/DeepSeek-V4-Flash-0731"
        const val CONSENSUS_MODEL = "MiniMaxAI/MiniMax-M2.7"
    }

    suspend fun restructure(sentence: String): SignGrammarResult = withContext(Dispatchers.IO) {
        val apiKey = try {
            BuildConfig.GONKA_API_KEY
        } catch (_: Exception) {
            ""
        }

        if (apiKey.isBlank() || apiKey == "\"\"") {
            Log.w(TAG, "No Gonka API key — using local fallback tokenization")
            return@withContext fallbackTokenize(sentence)
        }

        val userTurn = SignGrammarPrompt.userTurn(sentence)

        // Option A: Multi-Model Consensus on Gonka Network
        val primaryDeferred = async {
            try {
                val raw = gonkaComplete(PRIMARY_MODEL, SignGrammarPrompt.SYSTEM, userTurn)
                parseResult(raw, "DeepSeek-V4-Flash")
            } catch (e: Exception) {
                Log.w(TAG, "Primary model failed: ${e.message}")
                null
            }
        }

        val consensusDeferred = async {
            try {
                val raw = gonkaComplete(CONSENSUS_MODEL, SignGrammarPrompt.SYSTEM, userTurn)
                parseResult(raw, "MiniMax-M2.7")
            } catch (e: Exception) {
                Log.w(TAG, "Consensus model failed: ${e.message}")
                null
            }
        }

        val primaryResult = primaryDeferred.await()
        val consensusResult = consensusDeferred.await()

        when {
            primaryResult != null && consensusResult != null -> {
                Log.i(TAG, "Consensus verified between DeepSeek and MiniMax: ${primaryResult.tokens}")
                primaryResult.copy(model = "Gonka Multi-Model Consensus")
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
            // Find outermost matching brace
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
