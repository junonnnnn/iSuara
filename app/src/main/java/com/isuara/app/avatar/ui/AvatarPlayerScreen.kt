package com.isuara.app.avatar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.isuara.app.avatar.data.MotionRepository
import com.isuara.app.avatar.engine.MotionPlayer
import com.isuara.app.avatar.gl.AvatarGLSurfaceView

@Composable
fun AvatarPlayerScreen(
    motionPlayer: MotionPlayer = remember { MotionPlayer() }
) {
    val context = LocalContext.current
    val repository = remember { MotionRepository(context) }
    val focusManager = LocalFocusManager.current

    var glView by remember { mutableStateOf<AvatarGLSurfaceView?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("") }
    var activeSignKey by remember { mutableStateOf("terima_kasih") }

    // Play helper
    fun triggerPlay(query: String, startPlaying: Boolean = true) {
        if (query.isBlank()) return
        focusManager.clearFocus()
        val clean = query.trim().lowercase().replace(" ", "_")

        // 1. Direct Catalog Match
        val directMatch = MotionRepository.CATALOG.find {
            it.key.equals(clean, ignoreCase = true) ||
            it.title.equals(query.trim(), ignoreCase = true)
        }

        if (directMatch != null) {
            activeSignKey = directMatch.key
            val motion = repository.loadMotion(directMatch.key)
            if (motion != null) {
                motionPlayer.setMotion(motion, autoPlay = startPlaying)
                motionPlayer.isLooping = true
                isPlaying = startPlaying
            }
            return
        }

        // 2. Multi-word Sentence Synthesis
        val parts = query.trim().lowercase().split("\\s+".toRegex())
        val synthesized = repository.synthesizeSentence(parts)
        if (synthesized != null) {
            activeSignKey = clean
            motionPlayer.setMotion(synthesized, autoPlay = startPlaying)
            motionPlayer.isLooping = true
            isPlaying = startPlaying
        } else {
            // Fallback: search partial
            val fallback = repository.searchVocabulary(query).firstOrNull()
            if (fallback != null) {
                activeSignKey = fallback.key
                val motion = repository.loadMotion(fallback.key)
                if (motion != null) {
                    motionPlayer.setMotion(motion, autoPlay = startPlaying)
                    motionPlayer.isLooping = true
                    isPlaying = startPlaying
                }
            }
        }
    }

    // Load initial motion on start (paused ready state, no auto-play)
    LaunchedEffect(Unit) {
        val motion = repository.loadMotion("terima_kasih")
        if (motion != null) {
            motionPlayer.setMotion(motion, autoPlay = false)
            motionPlayer.isLooping = true
            isPlaying = false
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

        // ── 2. Bottom Minimalist Input Bar & Controls ──
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color(0xF0161B22), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .border(1.dp, Color(0x3330363D), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            // Input Row: TextField + Green Send Arrow + Play/Pause Button on the right
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
                            text = "Enter word or sentence",
                            fontSize = 13.sp,
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
                    modifier = Modifier
                        .size(46.dp)
                        .background(Color(0xFF238636), CircleShape)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Send",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Play / Pause Button directly to the right side of Send Arrow
                IconButton(
                    onClick = {
                        motionPlayer.togglePlayPause()
                        isPlaying = motionPlayer.isPlaying
                    },
                    modifier = Modifier
                        .size(46.dp)
                        .background(Color(0xFF1F6FEB), CircleShape)
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
