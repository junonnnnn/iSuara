package com.isuara.app.service

/**
 * Where the translation pipeline currently is, for display while the user waits.
 *
 * Deliberately vague: the label tells the user the multi-agent machinery is
 * working without putting three competing sentences or the judge's reasoning on
 * screen. Those go to logcat instead.
 */
enum class TranslationStage(val label: String) {
    IDLE(""),
    CONSULTING("Consulting interpreters…"),
    COLLECTED("Interpretations received"),
    JUDGING("Weighing interpretations…"),
    DECIDING("Choosing best translation…"),
}
