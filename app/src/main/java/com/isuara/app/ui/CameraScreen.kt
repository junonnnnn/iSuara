package com.isuara.app.ui

import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.isuara.app.R
import com.isuara.app.emotion.EmotionReading
import com.isuara.app.emotion.EmotionTracker
import com.isuara.app.emotion.ui.EmotionChip
import com.isuara.app.ml.SignPredictor
import com.isuara.app.service.Language
import com.isuara.app.service.SpeechRouter
import com.isuara.app.service.Translation
import com.isuara.app.service.TranslationStage
import com.isuara.app.service.CandidateView
import com.isuara.app.service.DebateProgress
import com.isuara.app.service.Translator
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

private const val TAG = "CameraScreen"

val googleSansFlex = FontFamily(Font(R.font.google_sans_flex))

@androidx.annotation.OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    signPredictor: SignPredictor,
    translator: Translator?,
    speech: SpeechRouter,
    emotionTracker: EmotionTracker? = null,
    initialLanguage: Language = Language.MALAY,
    onLanguageChange: (Language) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val predictionState by signPredictor.state.collectAsState()
    // Vague pipeline progress: shows the multi-agent work is real without
    // putting candidate sentences or judge reasoning on screen.
    val debate by (translator?.progress?.collectAsState()
        ?: remember { mutableStateOf(DebateProgress()) })
    val translationStage by (translator?.stage?.collectAsState()
        ?: remember { mutableStateOf(TranslationStage.IDLE) })
    // Live expression, for the chip only. The reading that actually steers a
    // translation is taken once per sentence via readSentenceEmotion().
    val liveEmotion by (emotionTracker?.state?.collectAsState()
        ?: remember { mutableStateOf<EmotionReading?>(null) })

    var showLandmarks by remember { mutableStateOf(false) }
    var translation by remember { mutableStateOf<Translation?>(null) }
    var language by remember { mutableStateOf(initialLanguage) }
    var languageMenuOpen by remember { mutableStateOf(false) }
    var isTranslating by remember { mutableStateOf(false) }
    // Held so the replay button re-speaks with the emotion the sentence was
    // translated under; the window itself is consumed at translation time.
    var spokenEmotion by remember { mutableStateOf<EmotionReading?>(null) }
    var fpsCounter by remember { mutableIntStateOf(0) }
    var displayFps by remember { mutableIntStateOf(0) }
    var lastFpsTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_FRONT) }

    val mlExecutor = remember { Executors.newSingleThreadExecutor() }

    // =========================================================================
    // 1. AUTO-TRANSLATE & TTS FEATURE
    // =========================================================================
    LaunchedEffect(predictionState.currentWord) {
        // Notice the new condition: !predictionState.isWaitingForNewSentence
        // This prevents the TTS from looping twice if the user stays idle!
        if (predictionState.currentWord == SignPredictor.IDLE &&
            predictionState.sentence.isNotEmpty() &&
            !isTranslating &&
            !predictionState.isWaitingForNewSentence) {

            kotlinx.coroutines.delay(3000)

            val words = signPredictor.getSentenceWords()
            if (words.isNotEmpty() && !predictionState.isWaitingForNewSentence && !isTranslating) {
                isTranslating = true
                translation = null

                // Read once and reuse: readSentenceEmotion() consumes the
                // window, so calling it again below would return nothing.
                val sentenceEmotion = emotionTracker?.readSentenceEmotion()
                spokenEmotion = sentenceEmotion

                try {
                    val rawSentence = words.joinToString(" ")
                    // No translator configured falls back to the raw Malay
                    // glosses. A real failure throws into the catch below.
                    val result = translator?.translate(words, sentenceEmotion)
                        ?: Translation.ofRawGlosses(rawSentence)
                    translation = result

                    // Speak only the selected language; the router falls back to
                    // the Malay rendering if that voice is not installed.
                    val spoken = result.forLanguage(language)
                    if (spoken.isNotEmpty()) {
                        speech.speak(spoken, language, result.ms, sentenceEmotion)
                    }
                } catch (e: Exception) {
                    // If the translator throws (e.g., no internet), fall back to
                    // the raw glosses, which are Malay — so speak them as Malay.
                    // The emotion still applies: how it is said does not depend
                    // on the translator having succeeded.
                    val rawSentence = words.joinToString(" ")
                    translation = Translation.ofRawGlosses(rawSentence)
                    speech.speak(rawSentence, Language.MALAY, rawSentence, sentenceEmotion)
                    Log.e(TAG, "Auto-translate error, falling back to raw text", e)
                } finally {
                    isTranslating = false
                    // INSTEAD OF resetAll(), tell the predictor to hold the text on
                    // screen until the exact moment the next sign is made!
                    signPredictor.prepareForNewSentence()
                }
            }
        }
    }

    // =========================================================================
    // 2. UI POLISH (OPTIONAL BUT RECOMMENDED)
    // Clear the previous translation text from the screen when a NEW sentence starts
    // =========================================================================
    LaunchedEffect(predictionState.sentence.size) {
        if (predictionState.sentence.size == 1 && !isTranslating) {
            translation = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1. RESPONSIVE CAMERA PREVIEW
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.1f)
                .background(Color.DarkGray)
        ) {
            val previewView = remember {
                androidx.camera.view.PreviewView(context).apply {
                    scaleType = androidx.camera.view.PreviewView.ScaleType.FILL_CENTER
                    implementationMode = androidx.camera.view.PreviewView.ImplementationMode.PERFORMANCE
                }
            }

            LaunchedEffect(lensFacing) {
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()

                    val previewBuilder = Preview.Builder()
                        .setTargetResolution(android.util.Size(640, 480))

                    val previewExt = androidx.camera.camera2.interop.Camera2Interop.Extender(previewBuilder)
                    previewExt.setCaptureRequestOption(
                        android.hardware.camera2.CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                        android.util.Range(30, 60)
                    )

                    val preview = previewBuilder.build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }

                    val analysisBuilder = ImageAnalysis.Builder()
                        .setTargetResolution(android.util.Size(480, 360))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)

                    val imageAnalysis = analysisBuilder.build()
                    val isFront = lensFacing == CameraSelector.LENS_FACING_FRONT
                    var reusedBitmap: Bitmap? = null
                    val canvas = android.graphics.Canvas()
                    val matrix = android.graphics.Matrix()

                    imageAnalysis.setAnalyzer(mlExecutor) { imageProxy ->
                        try {
                            val rawBitmap = imageProxy.toBitmap()
                            val rotation = imageProxy.imageInfo.rotationDegrees
                            val isPortrait = rotation == 90 || rotation == 270
                            val targetWidth = if (isPortrait) rawBitmap.height else rawBitmap.width
                            val targetHeight = if (isPortrait) rawBitmap.width else rawBitmap.height

                            if (reusedBitmap == null || reusedBitmap!!.width != targetWidth) {
                                reusedBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                            }

                            matrix.reset()
                            matrix.postTranslate(-rawBitmap.width / 2f, -rawBitmap.height / 2f)
                            matrix.postRotate(rotation.toFloat())
                            if (isFront) matrix.postScale(-1f, 1f)
                            matrix.postTranslate(targetWidth / 2f, targetHeight / 2f)

                            canvas.setBitmap(reusedBitmap)
                            canvas.drawColor(android.graphics.Color.BLACK, android.graphics.PorterDuff.Mode.CLEAR)
                            canvas.drawBitmap(rawBitmap, matrix, null)

                            signPredictor.processFrame(reusedBitmap!!, imageProxy.imageInfo.timestamp / 1_000_000, isFront)
                        } finally {
                            imageProxy.close()
                        }

                        fpsCounter++
                        val now = System.currentTimeMillis()
                        if (now - lastFpsTime >= 1000) {
                            displayFps = fpsCounter
                            fpsCounter = 0
                            lastFpsTime = now
                        }
                    }

                    val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageAnalysis)
                    } catch (e: Exception) {
                        Log.e(TAG, "Camera bind failed", e)
                    }
                }, ContextCompat.getMainExecutor(context))
            }

            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

            if (showLandmarks && predictionState.keypoints != null) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val keypoints = predictionState.keypoints!!
                    val radius = 6f
                    val scale = java.lang.Math.max(size.width / predictionState.imageWidth, size.height / predictionState.imageHeight)
                    val scaledWidth = predictionState.imageWidth * scale
                    val scaledHeight = predictionState.imageHeight * scale
                    val offsetX = (size.width - scaledWidth) / 2f
                    val offsetY = (size.height - scaledHeight) / 2f

                    fun mapX(xNorm: Float) = if (lensFacing == CameraSelector.LENS_FACING_FRONT) (xNorm * scaledWidth) + offsetX else ((1f - xNorm) * scaledWidth) + offsetX
                    fun mapY(yNorm: Float) = (yNorm * scaledHeight) + offsetY

                    for (i in 0 until 33) {
                        val x = mapX(keypoints[i * 4]); val y = mapY(keypoints[i * 4 + 1])
                        if (keypoints[i * 4 + 3] > 0.5f) drawCircle(Color.Green, radius, androidx.compose.ui.geometry.Offset(x, y))
                    }
                    for (i in 0 until 21) {
                        val idx = 132 + (i * 3)
                        if (keypoints[idx] > 0f) drawCircle(Color.Magenta, radius, androidx.compose.ui.geometry.Offset(mapX(keypoints[idx]), mapY(keypoints[idx + 1])))
                    }
                    for (i in 0 until 21) {
                        val idx = 195 + (i * 3)
                        if (keypoints[idx] > 0f) drawCircle(Color.Cyan, radius, androidx.compose.ui.geometry.Offset(mapX(keypoints[idx]), mapY(keypoints[idx + 1])))
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = "$displayFps FPS",
                        fontFamily = googleSansFlex,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EmotionChip(liveEmotion)
                    IconButton(
                        onClick = { showLandmarks = !showLandmarks },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = if (showLandmarks) Color(0xFF2196F3) else Color.Black.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Toggle Landmarks", tint = Color.White)
                    }
                    IconButton(
                        onClick = { lensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT) CameraSelector.LENS_FACING_BACK else CameraSelector.LENS_FACING_FRONT },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = "Switch Camera", tint = Color.White)
                    }
                }
            }

            LinearProgressIndicator(
                progress = { predictionState.bufferProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .align(Alignment.BottomCenter),
                color = Color(0xFF4CAF50),
                trackColor = Color.Transparent,
            )
        }

        // 2. CENTERED RECOGNITION & TRANSLATION PANEL
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(modifier = Modifier.height(44.dp), contentAlignment = Alignment.Center) {
                val isWordActive = predictionState.currentWord.isNotEmpty() && predictionState.currentWord != SignPredictor.IDLE
                androidx.compose.animation.AnimatedVisibility(
                    visible = isWordActive,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    val textColor by animateColorAsState(if (predictionState.isConfident) Color(0xFF4CAF50) else Color.White.copy(alpha = 0.85f), label = "color")
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = predictionState.currentWord.uppercase(),
                            fontFamily = googleSansFlex,
                            color = textColor,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp
                        )
                        signPredictor.glossIn(predictionState.currentWord, language)?.let { translated ->
                            Text(
                                text = translated,
                                fontFamily = googleSansFlex,
                                color = textColor.copy(alpha = 0.65f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }
            }

            val animatedConfidence by animateFloatAsState(targetValue = predictionState.confidence, label = "confidence")
            LinearProgressIndicator(
                progress = { animatedConfidence },
                modifier = Modifier.width(100.dp).height(2.dp).clip(RoundedCornerShape(1.dp)),
                color = if (predictionState.isConfident) Color(0xFF4CAF50) else Color(0xFFFF9800),
                trackColor = Color(0xFF21262D)
            )

            Spacer(modifier = Modifier.height(8.dp))

            val displaySentence = predictionState.sentence.joinToString(" ")

            Surface(
                color = Color.White.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = displaySentence.ifEmpty { "Waiting for signs..." },
                        fontFamily = googleSansFlex,
                        color = if (displaySentence.isEmpty()) Color.White.copy(alpha = 0.4f) else Color.White,
                        fontSize = 18.sp,
                        lineHeight = 26.sp,
                        textAlign = TextAlign.Center
                    )

                    // Second row of glosses in the selected language.
                    if (displaySentence.isNotEmpty() && language.isSecondary) {
                        val translatedGlosses = predictionState.sentence
                            .map { signPredictor.glossIn(it, language) ?: it }
                            .joinToString(" ")
                        Text(
                            text = translatedGlosses,
                            fontFamily = googleSansFlex,
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    AnimatedVisibility(visible = isTranslating || translation != null) {
                        Column(
                            modifier = Modifier.padding(top = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            HorizontalDivider(color = Color.White.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 8.dp))
                            val hasDebate = debate.candidates.isNotEmpty()

                            // Reset on each new run so the review dropdown does
                            // not open pre-expanded from the previous sentence.
                            var reasoningOpen by remember { mutableStateOf(false) }
                            LaunchedEffect(isTranslating) {
                                if (isTranslating) reasoningOpen = false
                            }

                            // Before the agents are seeded, and for a
                            // single-model translator that has no debate.
                            if (isTranslating && !hasDebate) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        color = ACCENT,
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = translationStage.label
                                            .ifEmpty { "Refining grammar..." },
                                        fontFamily = googleSansFlex,
                                        color = ACCENT,
                                        fontSize = 14.sp,
                                    )
                                }
                            }

                            if (!isTranslating) {
                                translation?.let { t ->
                                    Text(
                                        text = t.ms,
                                        fontFamily = googleSansFlex,
                                        color = Color(0xFF64B5F6),
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Medium,
                                        lineHeight = 26.sp,
                                        textAlign = TextAlign.Center
                                    )
                                    if (language.isSecondary) {
                                        Text(
                                            text = t.forLanguage(language),
                                            fontFamily = googleSansFlex,
                                            color = Color(0xFF64B5F6).copy(alpha = 0.75f),
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Normal,
                                            lineHeight = 22.sp,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }

                                // The reasoning is secondary once the answer is
                                // in: one collapsed row below the sentence, so
                                // the finished state reads as a translation
                                // rather than a debate log.
                                if (hasDebate) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clickable { reasoningOpen = !reasoningOpen }
                                            .padding(top = 8.dp, bottom = 2.dp),
                                    ) {
                                        Text(
                                            text = if (reasoningOpen) "▾" else "▸",
                                            color = Color.White.copy(alpha = 0.55f),
                                            fontSize = 11.sp,
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (reasoningOpen) {
                                                "Hide reasoning"
                                            } else {
                                                "Show reasoning"
                                            },
                                            fontFamily = googleSansFlex,
                                            color = Color.White.copy(alpha = 0.55f),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                        )
                                    }
                                }
                            }

                            // One call site for both phases: live during the run,
                            // and behind the dropdown afterwards. Rendering it
                            // from two places would give Compose two call sites
                            // and reset the per-step expand state between them.
                            if (hasDebate && (isTranslating || reasoningOpen)) {
                                DebatePanel(debate, translationStage)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            val uniformButtonHeight = 52.dp

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Compact chip for language selection
                Box {
                    FilledIconButton(
                        onClick = { languageMenuOpen = true },
                        modifier = Modifier.size(uniformButtonHeight),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color.White.copy(alpha = 0.12f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = language.shortLabel,
                            fontFamily = googleSansFlex,
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
                                        fontFamily = googleSansFlex,
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

                FilledIconButton(
                    onClick = {
                        signPredictor.resetAll()
                        translation = null
                        isTranslating = false
                        spokenEmotion = null
                        speech.stop()
                    },
                    modifier = Modifier.size(uniformButtonHeight),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White.copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Clear", tint = Color.White)
                }

                Button(
                    onClick = {
                        val words = signPredictor.getSentenceWords()
                        if (words.isNotEmpty() && !isTranslating) {
                            isTranslating = true
                            translation = null
                            val sentenceEmotion = emotionTracker?.readSentenceEmotion()
                            spokenEmotion = sentenceEmotion
                            scope.launch {
                                try {
                                    val rawSentence = words.joinToString(" ")
                                    translation = translator?.translate(words, sentenceEmotion)
                                        ?: Translation.ofRawGlosses(rawSentence)
                                } catch (e: Exception) {
                                    translation = Translation.ofRawGlosses(words.joinToString(" "))
                                    Log.e(TAG, "Manual translate error", e)
                                } finally {
                                    isTranslating = false
                                    // The manual path needs the same reset as
                                    // the automatic one: glosses that survive a
                                    // translation re-trigger it on the next idle.
                                    signPredictor.prepareForNewSentence()
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(uniformButtonHeight),
                    enabled = predictionState.sentence.isNotEmpty() && !isTranslating,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2196F3),
                        disabledContainerColor = Color(0xFF1E2630),
                        disabledContentColor = Color(0xFF8B949E)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Translate", fontFamily = googleSansFlex, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }

                FloatingActionButton(
                    onClick = {
                        val current = translation
                        if (current != null) {
                            speech.speak(
                                current.forLanguage(language), language, current.ms, spokenEmotion,
                            )
                        } else {
                            val raw = predictionState.sentence.joinToString(" ")
                            if (raw.isNotEmpty()) {
                                speech.speak(raw, Language.MALAY, raw, spokenEmotion)
                            }
                        }
                    },
                    modifier = Modifier.size(uniformButtonHeight),
                    containerColor = Color(0xFF4CAF50),
                    contentColor = Color.White,
                    shape = RoundedCornerShape(16.dp),
                    elevation = FloatingActionButtonDefaults.elevation(0.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Speak")
                }
            }
        }
    }
}

private val ACCENT = Color(0xFF2196F3)
private val WINNER = Color(0xFF4CAF50)
private val MUTED = Color.White.copy(alpha = 0.4f)

/** Which step of the debate is currently open. Only ever one at a time. */
private enum class DebateStep { MODEL_REASONING, JUDGING, NONE }

/**
 * The debate as two steps that take turns: the models answer, then the judge
 * decides. Whichever step is live is expanded; the other is a one-line header.
 *
 * Which step is open is *derived* from [DebateProgress] rather than tracked
 * separately, so the accordion cannot drift out of sync with the pipeline. A
 * manual override sits on top for taps and is cleared whenever the pipeline
 * moves on, otherwise reopening a step during one translation would leave the
 * accordion stuck for the next.
 */
@Composable
private fun DebatePanel(debate: DebateProgress, stage: TranslationStage) {
    val auto = when {
        !debate.isActive -> DebateStep.NONE
        !debate.allResolved -> DebateStep.MODEL_REASONING
        debate.verdict == null -> DebateStep.JUDGING
        else -> DebateStep.NONE
    }

    var override by remember { mutableStateOf<DebateStep?>(null) }
    // Clearing on every change of `auto` is what keeps a tap from outliving the
    // step it was made in.
    LaunchedEffect(auto) { override = null }
    val open = override ?: auto

    val answered = debate.candidates.count { it.sentence != null }
    val winner = debate.verdict?.let { debate.candidates.getOrNull(it.choice) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 220.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        DebateStepRow(
            index = 1,
            title = "Model Reasoning",
            expanded = open == DebateStep.MODEL_REASONING,
            active = auto == DebateStep.MODEL_REASONING,
            summary = when {
                debate.candidates.isEmpty() -> ""
                answered == debate.candidates.size -> "${debate.candidates.size} models"
                else -> "$answered of ${debate.candidates.size}"
            },
            onToggle = {
                override = if (open == DebateStep.MODEL_REASONING) {
                    DebateStep.NONE
                } else {
                    DebateStep.MODEL_REASONING
                }
            },
        ) {
            debate.candidates.forEachIndexed { index, candidate ->
                ModelRow(candidate, isWinner = debate.verdict?.choice == index)
            }
        }

        DebateStepRow(
            index = 2,
            title = "Judging",
            expanded = open == DebateStep.JUDGING,
            active = auto == DebateStep.JUDGING,
            summary = winner?.shortName.orEmpty(),
            onToggle = {
                override = if (open == DebateStep.JUDGING) DebateStep.NONE else DebateStep.JUDGING
            },
        ) {
            if (debate.verdict == null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 20.dp),
                ) {
                    CircularProgressIndicator(
                        color = ACCENT,
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stage.label.ifEmpty { "Weighing interpretations…" },
                        fontFamily = googleSansFlex,
                        color = ACCENT,
                        fontSize = 12.sp,
                    )
                }
            } else {
                Column(modifier = Modifier.padding(start = 20.dp)) {
                    Text(
                        text = "★ ${winner?.shortName.orEmpty()}",
                        fontFamily = googleSansFlex,
                        color = WINNER,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    winner?.sentence?.let {
                        Text(
                            text = it,
                            fontFamily = googleSansFlex,
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 14.sp,
                            lineHeight = 19.sp,
                            modifier = Modifier.padding(top = 1.dp),
                        )
                    }
                    debate.verdict?.reason?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            fontFamily = googleSansFlex,
                            color = Color(0xFFFFB74D).copy(alpha = 0.85f),
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                }
            }
        }
    }
}

/** One numbered step: a tappable header, and a body shown only when expanded. */
@Composable
private fun DebateStepRow(
    index: Int,
    title: String,
    expanded: Boolean,
    active: Boolean,
    summary: String,
    onToggle: () -> Unit,
    body: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = 4.dp),
        ) {
            Text(
                text = if (expanded) "▾" else "▸",
                color = if (active) ACCENT else MUTED,
                fontSize = 11.sp,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "$index. $title",
                fontFamily = googleSansFlex,
                color = if (active) ACCENT else Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            )
            if (!expanded && summary.isNotBlank()) {
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = summary,
                    fontFamily = googleSansFlex,
                    color = MUTED,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(bottom = 4.dp)) { body() }
        }
    }
}

/** One model's row: pending, answered, or failed. */
@Composable
private fun ModelRow(candidate: CandidateView, isWinner: Boolean) {
    Column(modifier = Modifier.padding(bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (candidate.isPending) {
                CircularProgressIndicator(
                    color = ACCENT,
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Text(
                    text = if (candidate.failed) "×" else if (isWinner) "★" else "•",
                    color = when {
                        candidate.failed -> MUTED
                        isWinner -> WINNER
                        else -> ACCENT
                    },
                    fontSize = 13.sp,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = candidate.shortName,
                fontFamily = googleSansFlex,
                color = if (isWinner) WINNER else Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontWeight = if (isWinner) FontWeight.Bold else FontWeight.Medium,
            )
        }
        Text(
            text = when {
                candidate.failed -> "no answer"
                candidate.sentence == null -> "thinking…"
                else -> candidate.sentence
            },
            fontFamily = googleSansFlex,
            color = if (candidate.sentence == null || candidate.failed) {
                MUTED
            } else {
                Color.White.copy(alpha = 0.9f)
            },
            fontSize = 14.sp,
            lineHeight = 19.sp,
            modifier = Modifier.padding(start = 20.dp, top = 1.dp),
        )
    }
}
