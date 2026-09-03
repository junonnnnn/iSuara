package com.isuara.app.service

import com.isuara.app.emotion.EmotionLabel
import com.isuara.app.emotion.EmotionReading
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers how the observed expression reaches the models.
 *
 * The interesting behaviour is the confidence gate: the classifier always names
 * a winner, so without a floor a coin-flip reading would be stated to the model
 * as fact and would steer a sentence on the strength of noise.
 */
class TranslationPromptsTest {

    private val glosses = listOf("Tolong", "Polis", "Cepat")

    private fun reading(label: EmotionLabel, confidence: Float) =
        EmotionReading(label = label, confidence = confidence, peakArousal = label.arousal)

    @Test
    fun `a confident reading reaches the model`() {
        val turn = TranslationPrompts.userTurn(glosses, reading(EmotionLabel.FEAR, 0.82f))
        assertTrue(turn, turn.contains("Observed facial expression: fear"))
        assertTrue(turn, turn.contains("high arousal"))
    }

    @Test
    fun `a low-arousal emotion is labelled as such`() {
        val turn = TranslationPrompts.userTurn(glosses, reading(EmotionLabel.SADNESS, 0.75f))
        assertTrue(turn, turn.contains("sadness"))
        assertTrue(turn, turn.contains("low arousal"))
    }

    @Test
    fun `a weak reading is withheld rather than hedged`() {
        val turn = TranslationPrompts.userTurn(glosses, reading(EmotionLabel.ANGER, 0.20f))
        assertFalse(turn, turn.contains("Observed facial expression"))
    }

    @Test
    fun `no reading leaves the turn exactly as it was before the feature`() {
        val turn = TranslationPrompts.userTurn(glosses, null)
        assertFalse(turn.contains("Observed"))
        assertTrue(turn, turn.contains("Input: $glosses"))
        assertTrue(turn, turn.trimEnd().endsWith("Output:"))
    }

    @Test
    fun `the judge is told what the face was doing`() {
        val candidates = listOf(
            Translation("Tolong polis.", "Help police.", "帮助", "உதவி"),
            Translation("Tolong! Cepat panggil polis!", "Help! Quick!", "救命!", "உதவி!"),
        )
        val turn = TranslationPrompts.judgeTurn(
            glosses, candidates, reading(EmotionLabel.FEAR, 0.9f),
        )
        assertTrue(turn, turn.contains("Observed facial expression: fear"))
        assertTrue(turn, turn.contains("0. Tolong polis."))
        assertTrue(turn, turn.contains("1. Tolong! Cepat panggil polis!"))
    }

    @Test
    fun `the system prompt asks for the two enrichment fields`() {
        assertTrue(TranslationPrompts.SYSTEM.contains("\"emotion\""))
        assertTrue(TranslationPrompts.SYSTEM.contains("\"style\""))
    }

    /**
     * The observation must live in the per-request turn, not the system prompt.
     *
     * That is what keeps the system prompt byte-identical across all three
     * agents and every request — the property that makes a difference between
     * candidates attributable to the model rather than to the prompt.
     */
    @Test
    fun `the observation varies the user turn and never the system prompt`() {
        val fearful = TranslationPrompts.userTurn(glosses, reading(EmotionLabel.FEAR, 0.9f))
        val calm = TranslationPrompts.userTurn(glosses, reading(EmotionLabel.NEUTRAL, 0.9f))
        assertTrue("the observation must reach the user turn", fearful != calm)
        assertTrue(fearful.contains("fear"))
        assertTrue(calm.contains("neutral"))
        // Same object every time, by construction — asserted so a future edit
        // cannot quietly make it per-request.
        assertTrue(TranslationPrompts.SYSTEM === TranslationPrompts.SYSTEM)
    }
}
