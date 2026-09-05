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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.isuara.app.avatar.data.MotionRepository
import com.isuara.app.avatar.engine.MotionPlayer
import com.isuara.app.avatar.gl.AvatarGLSurfaceView
import com.isuara.app.avatar.grammar.GeminiSignGrammarService
import kotlinx.coroutines.launch

@Composable
fun AvatarPlayerScreen(
    motionPlayer: MotionPlayer = remember { MotionPlayer() }
) {
    val context = LocalContext.current
    val repository = remember { MotionRepository(context) }
    val grammarService = remember { GeminiSignGrammarService() }
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var glView by remember { mutableStateOf<AvatarGLSurfaceView?>(null) }
    var inputText by remember { mutableStateOf("") }
    var activeSignKey by remember { mutableStateOf("terima_kasih") }

    // Multi-model grammar reasoning state
    var isReasoning by remember { mutableStateOf(false) }
    var reasoningTrace by remember { mutableStateOf<String?>(null) }
    var bimTokens by remember { mutableStateOf<List<String>>(emptyList()) }
    var activeModelLabel by remember { mutableStateOf<String?>(null) }

    // Play helper with AI BIM grammar reasoning for sentences
    fun triggerPlay(query: String, startPlaying: Boolean = true) {
        val effectiveQuery = if (query.isNotBlank()) query else activeSignKey
        if (effectiveQuery.isBlank()) return
        focusManager.clearFocus()
        val clean = effectiveQuery.trim().lowercase().replace(" ", "_")

        // 1. Direct Catalog Match for single glosses
        val directMatch = MotionRepository.CATALOG.find {
            it.key.equals(clean, ignoreCase = true) ||
            it.title.equals(effectiveQuery.trim(), ignoreCase = true)
        }

        if (directMatch != null) {
            activeSignKey = directMatch.key
            bimTokens = emptyList()
            reasoningTrace = null
            activeModelLabel = null
            val motion = repository.loadMotion(directMatch.key)
            if (motion != null) {
                motionPlayer.isLooping = false
                motionPlayer.setMotion(motion, autoPlay = startPlaying)
            }
            return
        }

        // 2. Multi-word Sentence Synthesis via Gemini Multi-Model Reasoning
        coroutineScope.launch {
            isReasoning = true
            try {
                val result = grammarService.restructure(effectiveQuery)
                reasoningTrace = result.reasoning
                bimTokens = result.displayTokens
                activeModelLabel = result.model
                activeSignKey = clean

                // Synthesize continuous motion sequence from restructured BIM tokens
                val synthesized = repository.synthesizeSentence(result.tokens)
                if (synthesized != null) {
                    motionPlayer.isLooping = false
                    motionPlayer.setMotion(synthesized, autoPlay = startPlaying)
                } else {
                    // Partial search fallback
                    val fallback = repository.searchVocabulary(effectiveQuery).firstOrNull()
                    if (fallback != null) {
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

    // Load initial motion on start (paused ready state, no auto-play, no looping)
    LaunchedEffect(Unit) {
        val motion = repository.loadMotion("terima_kasih")
        if (motion != null) {
            motionPlayer.isLooping = false
            motionPlayer.setMotion(motion, autoPlay = false)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090D13))
    ) {
        // ── 1. Interactive 3D Avatar Viewport ──
        AndroidView(
            factory = { ctx ->
                AvatarGLSurfaceView(ctx, motionPlayer).also { glView = it }
            },
            modifier = Modifier.fillMaxSize()
        )

        // ── 2. Bottom Input & BIM Grammar Reasoning Card ──
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color(0xF0161B22), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .border(1.dp, Color(0x3330363D), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Quick Demo Scenarios (Police & Doctor)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(Color(0xFF1E2A3A), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFF388BFD), RoundedCornerShape(12.dp))
                        .clickable(enabled = !isReasoning) {
                            inputText = "Encik, apa yang saya boleh tolong?"
                            triggerPlay("Encik, apa yang saya boleh tolong?", startPlaying = true)
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "🚨 Polis: Boleh tolong?",
                        fontSize = 11.sp,
                        color = Color(0xFF58A6FF),
                        fontWeight = FontWeight.Medium
                    )
                }

                Box(
                    modifier = Modifier
                        .background(Color(0xFF192F23), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFF3FB950), RoundedCornerShape(12.dp))
                        .clickable(enabled = !isReasoning) {
                            inputText = "Anak awak sakit apa sekarang?"
                            triggerPlay("Anak awak sakit apa sekarang?", startPlaying = true)
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "🩺 Doktor: Anak sakit apa?",
                        fontSize = 11.sp,
                        color = Color(0xFF3FB950),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Reasoning Status or Token Chips
            AnimatedVisibility(
                visible = isReasoning || bimTokens.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0D1117), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0x4458A6FF), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isReasoning) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF58A6FF)
                            )
                            Text(
                                text = "Analyzing BIM Grammar via Gemini Multi-Model…",
                                fontSize = 12.sp,
                                color = Color(0xFF8B949E)
                            )
                        }
                    } else if (bimTokens.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "BIM Sign Grammar Syntax",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF58A6FF)
                            )
                            activeModelLabel?.let { model ->
                                Text(
                                    text = model,
                                    fontSize = 10.sp,
                                    color = Color(0xFF3FB950),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // Display tokens with arrow connectors
                        LazyRow(
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

                        reasoningTrace?.let { trace ->
                            Text(
                                text = trace,
                                fontSize = 11.sp,
                                fontStyle = FontStyle.Italic,
                                color = Color(0xFF8B949E),
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
            }

            // Input Row: TextField + Send Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = {
                        Text(
                            text = "Enter spoken sentence (e.g. Encik, apa yang saya boleh tolong?)",
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
                        contentDescription = "Send",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}
