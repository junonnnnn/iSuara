package com.isuara.app.avatar.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.isuara.app.avatar.data.MotionRepository
import com.isuara.app.avatar.engine.MotionPlayer
import com.isuara.app.avatar.gl.AvatarGLSurfaceView
import com.isuara.app.avatar.grammar.GonkaSignGrammarService
import com.isuara.app.avatar.grammar.SignGrammarResult
import com.isuara.app.service.Language
import com.isuara.app.service.strings
import kotlinx.coroutines.launch

@Composable
fun AvatarPlayerScreen(
    motionPlayer: MotionPlayer = remember { MotionPlayer() },
    initialLanguage: Language = Language.MALAY,
    onLanguageChange: (Language) -> Unit = {}
) {
    val context = LocalContext.current
    val repository = remember { MotionRepository(context) }
    val grammarService = remember { GonkaSignGrammarService() }
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var language by remember { mutableStateOf(initialLanguage) }
    var languageMenuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(initialLanguage) {
        language = initialLanguage
    }

    var glView by remember { mutableStateOf<AvatarGLSurfaceView?>(null) }
    var inputText by remember { mutableStateOf("") }
    var activeSignKey by remember { mutableStateOf("") }
    var activeJsonFile by remember { mutableStateOf<String?>(null) }

    // Multi-model grammar reasoning state
    var isReasoning by remember { mutableStateOf(false) }
    var reasoningTrace by remember { mutableStateOf<String?>(null) }
    var bimTokens by remember { mutableStateOf<List<String>>(emptyList()) }
    var activeModelLabel by remember { mutableStateOf<String?>(null) }
    var grammarResult by remember { mutableStateOf<SignGrammarResult?>(null) }
    var showReasoningPath by remember { mutableStateOf(false) }

    // Play helper with AI BIM grammar reasoning for sentences
    fun triggerPlay(query: String, startPlaying: Boolean = true) {
        val effectiveQuery = if (query.isNotBlank()) query else activeSignKey
        if (effectiveQuery.isBlank()) return
        focusManager.clearFocus()
        val clean = effectiveQuery.trim().lowercase().replace(" ", "_")

        // 1. Direct Catalog Match for single glosses across all languages
        val directMatch = MotionRepository.findExactMatch(effectiveQuery, language)

        if (directMatch != null) {
            activeSignKey = directMatch.key
            activeJsonFile = directMatch.assetFileName
            val localizedGloss = MotionRepository.getLocalizedGloss(directMatch.key, language)
            bimTokens = listOf(localizedGloss)
            reasoningTrace = null
            activeModelLabel = null
            grammarResult = null
            showReasoningPath = false
            val motion = repository.loadMotion(directMatch.key)
            if (motion != null) {
                motionPlayer.isLooping = false
                motionPlayer.setMotion(motion, autoPlay = startPlaying)
            }
            return
        }

        // 2. Multi-word Sentence Synthesis via Gonka Multi-Model Reasoning
        coroutineScope.launch {
            isReasoning = true
            showReasoningPath = false
            try {
                val result = grammarService.restructure(effectiveQuery, language)
                grammarResult = result
                reasoningTrace = result.reasoning
                bimTokens = result.displayTokens
                activeModelLabel = result.model
                activeSignKey = clean

                // Determine active json file for the sentence
                val joined = result.tokens.joinToString("_").lowercase()
                activeJsonFile = when {
                    joined.contains("encik") && joined.contains("tolong") && joined.contains("apa") ->
                        "sentence_1_bim_encik_saya_boleh_tolong_apa.json"
                    joined.contains("hospital") && (joined.contains("kenapa") || joined.contains("datang")) ->
                        "sentence_2_bim_apa_khabar_hari_ini_awak_datang_hospital_kenapa.json"
                    else -> null
                }

                // Synthesize continuous motion sequence from restructured BIM tokens
                val synthesized = repository.synthesizeSentence(result.tokens)
                if (synthesized != null) {
                    motionPlayer.isLooping = false
                    motionPlayer.setMotion(synthesized, autoPlay = startPlaying)
                } else {
                    // Partial search fallback
                    val fallback = repository.searchVocabulary(effectiveQuery).firstOrNull()
                    if (fallback != null) {
                        activeJsonFile = fallback.assetFileName
                        val motion = repository.loadMotion(fallback.key)
                        if (motion != null) {
                            motionPlayer.isLooping = false
                            motionPlayer.setMotion(motion, autoPlay = startPlaying)
                        }
                    }
                }
            } catch (e: Exception) {
                // Fallback literal split
                val parts = effectiveQuery.trim().lowercase().split("\\s+".toRegex())
                val synthesized = repository.synthesizeSentence(parts)
                if (synthesized != null) {
                    motionPlayer.isLooping = false
                    motionPlayer.setMotion(synthesized, autoPlay = startPlaying)
                }
            } finally {
                isReasoning = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D1117))
    ) {
        // ── 1. 3D Avatar GL Surface ──
        AndroidView(
            factory = { ctx ->
                AvatarGLSurfaceView(ctx, motionPlayer).also { view ->
                    glView = view
                }
            },
            update = { view ->
                glView = view
            },
            modifier = Modifier.fillMaxSize()
        )

        // ── 2. Top Bar: Final Sign Sequence & Clickable 3-Model Reasoning Collapse ──
        AnimatedVisibility(
            visible = isReasoning || bimTokens.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF161B22), RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0x4458A6FF), RoundedCornerShape(16.dp))
                    .clickable(enabled = grammarResult != null && !isReasoning) {
                        showReasoningPath = !showReasoningPath
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isReasoning) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFF58A6FF)
                        )
                        Text(
                            text = language.strings.refiningGrammar,
                            fontSize = 12.sp,
                            color = Color(0xFF8B949E)
                        )
                    }
                } else if (bimTokens.isNotEmpty()) {
                    // Header Row with Sequence Title, Tag, and Collapse Indicator
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = language.strings.bimFinalSequence,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF58A6FF)
                            )
                            Box(
                                modifier = Modifier
                                    .background(Color(0x2E3FB950), RoundedCornerShape(4.dp))
                                    .border(1.dp, Color(0x663FB950), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = language.strings.finalSignBadge,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF3FB950)
                                )
                            }
                        }

                        if (grammarResult != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = if (showReasoningPath) "▲ ${language.strings.collapse}" else "▼ ${language.strings.showReasoning}",
                                    fontSize = 10.sp,
                                    color = Color(0xFF58A6FF),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    // Display final sign tokens with arrow connectors
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        itemsIndexed(bimTokens) { index, token ->
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFF21262D), RoundedCornerShape(6.dp))
                                    .border(1.dp, Color(0xFF388BFD), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = token,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                )
                            }
                            if (index < bimTokens.size - 1) {
                                Text(
                                    text = "→",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF58A6FF)
                                )
                            }
                        }
                    }

                    // Collapsible 3-Model Reasoning details (shown ONLY after sign sequence is pressed)
                    AnimatedVisibility(
                        visible = showReasoningPath && grammarResult != null,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 340.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(top = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            HorizontalDivider(
                                color = Color(0x3330363D),
                                thickness = 1.dp,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )

                            Text(
                                text = language.strings.reasoningBreakdown,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF8B949E)
                            )

                            // 3 Candidate models
                            grammarResult?.candidates?.forEach { cand ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            if (cand.isWinner) Color(0x143FB950) else Color(0xFF161B22),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .border(
                                            1.dp,
                                            if (cand.isWinner) Color(0x663FB950) else Color(0xFF30363D),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.weight(1f, fill = false)
                                        ) {
                                            Text(
                                                text = cand.model,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFF0F6FC),
                                                maxLines = 1
                                            )
                                            if (cand.isWinner) {
                                                Box(
                                                    modifier = Modifier
                                                        .background(Color(0x2E3FB950), RoundedCornerShape(4.dp))
                                                        .border(1.dp, Color(0x663FB950), RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                                ) {
                                                    Text(
                                                        text = language.strings.consensusPick,
                                                        fontSize = 8.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF3FB950)
                                                    )
                                                }
                                            }
                                        }
                                        cand.requestId?.let { reqId ->
                                            Text(
                                                text = reqId,
                                                fontSize = 8.sp,
                                                color = Color(0xFF8B949E),
                                                maxLines = 1
                                            )
                                        }
                                    }

                                    // Candidate tokens
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        itemsIndexed(cand.displayTokens) { idx, t ->
                                            Box(
                                                modifier = Modifier
                                                    .background(Color(0xFF21262D), RoundedCornerShape(4.dp))
                                                    .border(1.dp, Color(0x44388BFD), RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = t,
                                                    fontSize = 10.sp,
                                                    color = Color(0xFFE6EDF3)
                                                )
                                            }
                                            if (idx < cand.displayTokens.size - 1) {
                                                Text(
                                                    text = "→",
                                                    fontSize = 10.sp,
                                                    color = Color(0xFF8B949E)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── 3. Bottom Input Row ──
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .imePadding()
                .navigationBarsPadding()
                .fillMaxWidth()
                .background(Color(0xF0161B22), RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .border(1.dp, Color(0x3330363D), RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Compact chip for language selection (matches CameraScreen)
                Box {
                    FilledIconButton(
                        onClick = { languageMenuOpen = true },
                        modifier = Modifier.size(50.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color.White.copy(alpha = 0.12f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = language.shortLabel,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    DropdownMenu(
                        expanded = languageMenuOpen,
                        onDismissRequest = { languageMenuOpen = false }
                    ) {
                        Language.entries.forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        option.menuLabel,
                                        fontWeight = if (option == language) FontWeight.Bold
                                                     else FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    language = option
                                    onLanguageChange(option)
                                    languageMenuOpen = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = {
                        Text(
                            text = language.strings.textToSignPlaceholder,
                            fontSize = 12.sp,
                            color = Color(0xFF8B949E)
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { triggerPlay(inputText, startPlaying = true) }),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF0D1117),
                        unfocusedContainerColor = Color(0xFF0D1117),
                        focusedBorderColor = Color(0xFF58A6FF),
                        unfocusedBorderColor = Color(0xFF30363D),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(25.dp)
                )

                // Send Arrow Button
                IconButton(
                    onClick = { triggerPlay(inputText, startPlaying = true) },
                    enabled = !isReasoning,
                    modifier = Modifier
                        .size(46.dp)
                        .background(if (isReasoning) Color(0xFF30363D) else Color(0xFF238636), CircleShape)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = language.strings.translate,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}
