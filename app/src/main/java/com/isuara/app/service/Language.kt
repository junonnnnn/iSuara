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
    MALAY(null, Locale("ms", "MY"), "Bahasa Melayu", "MS"),
    ENGLISH("en", Locale.US, "English", "EN"),
    MANDARIN("zh", Locale.SIMPLIFIED_CHINESE, "简体中文", "中"),
    TAMIL("ta", Locale("ta", "IN"), "தமிழ்", "த");

    /** True when this language is not Malay. */
    val isSecondary: Boolean get() = labelMapKey != null

    companion object {
        fun fromName(name: String?): Language =
            entries.firstOrNull { it.name == name } ?: MALAY
    }
}

/**
 * Complete UI string localization across all 4 supported languages.
 * Guarantees that switching to a language shows 100% that language only.
 */
data class AppStrings(
    val tabSignToVoice: String,
    val tabTextToSign: String,
    val waitingForSigns: String,
    val signsLabel: String,
    val clear: String,
    val translate: String,
    val speak: String,
    val refiningGrammar: String,
    val showReasoning: String,
    val hideReasoning: String,
    val consensusBadge: String,
    val consensusPick: String,
    val candidateThinking: String,
    val candidateNoAnswer: String,
    val stepInterpreters: String,
    val stepInterpretations: String,
    val stepConsensus: String,
    val judgeTitle: String,
    val textToSignPlaceholder: String,
    val bimFinalSequence: String,
    val finalSignBadge: String,
    val collapse: String,
    val expand: String,
    val reasoningBreakdown: String,
    val noMotionData: String,
    val modelReasoning: String,
    val judging: String,
    val weighingInterpretations: String,
)

val Language.strings: AppStrings
    get() = when (this) {
        Language.MALAY -> AppStrings(
            tabSignToVoice = "Isyarat → Suara",
            tabTextToSign = "Teks → Avatar",
            waitingForSigns = "Menunggu isyarat...",
            signsLabel = "Isyarat dikesan",
            clear = "Padam",
            translate = "Terjemah",
            speak = "Sebut",
            refiningGrammar = "Menghalusi tatabahasa...",
            showReasoning = "Tunjuk hujah",
            hideReasoning = "Sembunyi hujah",
            consensusBadge = "Konsensus Berbilang Model",
            consensusPick = "PILIHAN KONSENSUS",
            candidateThinking = "sedang berfikir…",
            candidateNoAnswer = "tiada jawapan",
            stepInterpreters = "Jurubahasa",
            stepInterpretations = "Hujah Terjemahan",
            stepConsensus = "Keputusan Konsensus",
            judgeTitle = "Hakim Konsensus",
            textToSignPlaceholder = "Taip ayat dalam Bahasa Melayu...",
            bimFinalSequence = "Jujukan Isyarat Akhir",
            finalSignBadge = "ISYARAT AKHIR",
            collapse = "Tutup",
            expand = "Buka",
            reasoningBreakdown = "Laluan Penalaran 3-Model (Pecahan):",
            noMotionData = "Tiada gerakan untuk perkataan ini",
            modelReasoning = "Penalaran Model",
            judging = "Penghakiman",
            weighingInterpretations = "Menimbang tafsiran…",
        )
        Language.ENGLISH -> AppStrings(
            tabSignToVoice = "Sign → Voice",
            tabTextToSign = "Text → Avatar",
            waitingForSigns = "Waiting for signs...",
            signsLabel = "Detected signs",
            clear = "Clear",
            translate = "Translate",
            speak = "Speak",
            refiningGrammar = "Refining grammar...",
            showReasoning = "Show reasoning",
            hideReasoning = "Hide reasoning",
            consensusBadge = "Multi-Model Consensus",
            consensusPick = "CONSENSUS PICK",
            candidateThinking = "thinking…",
            candidateNoAnswer = "no answer",
            stepInterpreters = "Interpreters",
            stepInterpretations = "Interpretations",
            stepConsensus = "Consensus Verdict",
            judgeTitle = "Consensus Judge",
            textToSignPlaceholder = "Type a sentence in English...",
            bimFinalSequence = "Final Sign Sequence",
            finalSignBadge = "FINAL SIGN",
            collapse = "Collapse",
            expand = "Expand",
            reasoningBreakdown = "3-Model Reasoning Breakdown:",
            noMotionData = "No motion data for this word",
            modelReasoning = "Model Reasoning",
            judging = "Judging",
            weighingInterpretations = "Weighing interpretations…",
        )
        Language.MANDARIN -> AppStrings(
            tabSignToVoice = "手语 → 语音",
            tabTextToSign = "文本 → 虚拟人",
            waitingForSigns = "等待手语动作...",
            signsLabel = "已识别手语",
            clear = "清空",
            translate = "翻译",
            speak = "朗读",
            refiningGrammar = "正在梳理语法...",
            showReasoning = "显示推理过程",
            hideReasoning = "隐藏推理过程",
            consensusBadge = "多模型智能共识",
            consensusPick = "共识最佳选择",
            candidateThinking = "思考中…",
            candidateNoAnswer = "未作回答",
            stepInterpreters = "AI 翻译模型",
            stepInterpretations = "模型翻译候选项",
            stepConsensus = "多模型裁决结果",
            judgeTitle = "共识裁决裁判",
            textToSignPlaceholder = "输入句子进行手语转换...",
            bimFinalSequence = "最终手语动作序列",
            finalSignBadge = "最终手语动作",
            collapse = "收起",
            expand = "展开",
            reasoningBreakdown = "三模型推理路径分析:",
            noMotionData = "该词暂无手语动作数据",
            modelReasoning = "模型推理",
            judging = "共识裁决",
            weighingInterpretations = "正在权衡多方解读…",
        )
        Language.TAMIL -> AppStrings(
            tabSignToVoice = "சைகை → குரல்",
            tabTextToSign = "உரை → அவதார்",
            waitingForSigns = "சைகைகளுக்காக காத்திருக்கிறது...",
            signsLabel = "கண்டறியப்பட்ட சைகைகள்",
            clear = "அழி",
            translate = "மொழிபெயர்",
            speak = "பேசு",
            refiningGrammar = "இலக்கணத்தை சீரமைக்கிறது...",
            showReasoning = "காரணத்தைக் காட்டு",
            hideReasoning = "காரணத்தை மறை",
            consensusBadge = "பல மாதிரி ஒருமித்த கருத்து",
            consensusPick = "ஒருமித்த தேர்வு",
            candidateThinking = "சிந்திக்கிறது…",
            candidateNoAnswer = "பதில் இல்லை",
            stepInterpreters = "மொழிபெயர்ப்பாளர்கள்",
            stepInterpretations = "மொழிபெயர்ப்பு வாதங்கள்",
            stepConsensus = "ஒருமித்த தீர்ப்பு",
            judgeTitle = "ஒருமித்த நீதிபதி",
            textToSignPlaceholder = "சைகை செய்ய தமிழில் தட்டச்சு செய்க...",
            bimFinalSequence = "இறுதி சைகை வரிசை",
            finalSignBadge = "இறுதி சைகை",
            collapse = "மூடு",
            expand = "விரி",
            reasoningBreakdown = "3-மாதிரி பகுப்பாய்வு:",
            noMotionData = "இந்த சொல்லுக்கு இயக்க தரவு இல்லை",
            modelReasoning = "மாதிரி பகுப்பாய்வு",
            judging = "ஒருமித்த தீர்ப்பு",
            weighingInterpretations = "கருத்துக்கள் பரிசீலிக்கப்படுகின்றன…",
        )
    }
