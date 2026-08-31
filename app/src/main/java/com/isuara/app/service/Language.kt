package com.isuara.app.service

import java.util.Locale

/**
 * Display/speech language for glosses and translated sentences.
 *
 * Malay is the canonical language: sign glosses, the sentence buffer and the
 * LLM prompt are all Malay internally, and [labelMapKey] is null for it because
 * no lookup is needed. The other entries name their key in the `translations`
 * object of `label_map.json`.
 */
enum class Language(
    val labelMapKey: String?,
    val locale: Locale,
    val menuLabel: String,
    /** Compact label for the bottom-row chip. */
    val shortLabel: String,
) {
    MALAY(null, Locale("ms", "MY"), "Malay only", "MS"),
    ENGLISH("en", Locale.US, "English", "EN"),
    MANDARIN("zh", Locale.SIMPLIFIED_CHINESE, "Mandarin", "中"),
    TAMIL("ta", Locale("ta", "IN"), "Tamil", "த");

    /** True when a second row should be rendered beneath the Malay one. */
    val isSecondary: Boolean get() = labelMapKey != null

    companion object {
        fun fromName(name: String?): Language =
            entries.firstOrNull { it.name == name } ?: MALAY
    }
}
