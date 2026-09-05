package com.isuara.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.isuara.app.emotion.EmotionClassifier
import com.isuara.app.emotion.EmotionTracker
import com.isuara.app.ml.SignPredictor
import com.isuara.app.service.SpeechRouter
import com.isuara.app.service.gonkaDebate
import com.isuara.app.service.LanguagePreference
import com.isuara.app.service.Language
import com.isuara.app.service.Translator
import com.isuara.app.service.TtsService
import com.isuara.app.ui.CameraScreen

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private var signPredictor: SignPredictor? = null
    private var translator: Translator? = null
    private var languagePreference: LanguagePreference? = null
    private var ttsService: TtsService? = null
    private var speechRouter: SpeechRouter? = null
    private var emotionTracker: EmotionTracker? = null

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            initAndShow()
        } else {
            Toast.makeText(this, "Camera permission required", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            initAndShow()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun initAndShow() {
        // ── Initialize ML & services ──
        // Expression recognition is an enrichment, so it is built first and
        // separately: if the ONNX model fails to load, the tracker stays null
        // and the app runs exactly as it did before the feature existed.
        emotionTracker = try {
            EmotionTracker(EmotionClassifier(this))
        } catch (e: Exception) {
            Log.w(TAG, "Emotion model unavailable, continuing without it: ${e.message}")
            null
        }

        try {
            signPredictor = SignPredictor(this, emotionTracker)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init SignPredictor", e)
            Toast.makeText(this, "Model load failed: ${e.message}", Toast.LENGTH_LONG).show()
        }

        // Translator (optional — needs an API key). The key itself is read by
        // GonkaClient; this only decides whether the feature is enabled.
        val apiKey = try {
            BuildConfig.GONKA_API_KEY
        } catch (_: Exception) {
            ""
        }
        translator = if (apiKey.isNotBlank() && apiKey != "\"\"") {
            try {
                gonkaDebate()
            } catch (e: Exception) {
                Log.w(TAG, "Translator init failed: ${e.message}")
                null
            }
        } else {
            Log.w(TAG, "No GonkaRouter API key — translate feature disabled")
            null
        }

        val tts = TtsService(this)
        ttsService = tts
        // The UI speaks only through the router, which prefers the expressive
        // cloud voice and silently falls back to [tts] when it is unavailable.
        speechRouter = SpeechRouter(tts)
        languagePreference = LanguagePreference(this)

        // ── Compose UI ──
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
                ) {
                    val predictor = signPredictor
                    val speech = speechRouter

                    if (predictor != null && speech != null) {
                        val languagePrefs = languagePreference
                        var currentTab by remember { mutableIntStateOf(0) } // 0 = Camera, 1 = 3D Avatar

                        Scaffold(
                            bottomBar = {
                                NavigationBar(
                                    containerColor = Color(0xFF0D1117),
                                    contentColor = Color.White,
                                    tonalElevation = 0.dp
                                ) {
                                    NavigationBarItem(
                                        selected = currentTab == 0,
                                        onClick = { currentTab = 0 },
                                        icon = {
                                            Icon(
                                                Icons.Filled.Videocam,
                                                contentDescription = "Camera"
                                            )
                                        },
                                        label = { Text("Sign → Voice", fontSize = 12.sp, fontWeight = if (currentTab == 0) FontWeight.Bold else FontWeight.Normal) },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = Color.White,
                                            selectedTextColor = Color(0xFF58A6FF),
                                            indicatorColor = Color(0xFF1F6FEB),
                                            unselectedIconColor = Color(0xFF8B949E),
                                            unselectedTextColor = Color(0xFF8B949E)
                                        )
                                    )

                                    NavigationBarItem(
                                        selected = currentTab == 1,
                                        onClick = { currentTab = 1 },
                                        icon = {
                                            Icon(
                                                Icons.Filled.AccessibilityNew,
                                                contentDescription = "Avatar"
                                            )
                                        },
                                        label = { Text("Text → Sign", fontSize = 12.sp, fontWeight = if (currentTab == 1) FontWeight.Bold else FontWeight.Normal) },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = Color.White,
                                            selectedTextColor = Color(0xFF58A6FF),
                                            indicatorColor = Color(0xFF1F6FEB),
                                            unselectedIconColor = Color(0xFF8B949E),
                                            unselectedTextColor = Color(0xFF8B949E)
                                        )
                                    )
                                }
                            }
                        ) { innerPadding ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                            ) {
                                if (currentTab == 0) {
                                    CameraScreen(
                                        signPredictor = predictor,
                                        translator = translator,
                                        speech = speech,
                                        emotionTracker = emotionTracker,
                                        initialLanguage = languagePrefs?.get() ?: Language.MALAY,
                                        onLanguageChange = { languagePrefs?.set(it) }
                                    )
                                } else {
                                    com.isuara.app.avatar.ui.AvatarPlayerScreen(
                                        initialLanguage = languagePrefs?.get() ?: Language.MALAY,
                                        onLanguageChange = { languagePrefs?.set(it) }
                                    )
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Failed to initialize ML models",
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        signPredictor?.close()
        speechRouter?.stop()
        ttsService?.close()
        // Closes the ONNX session and stops the inference thread.
        emotionTracker?.close()
    }
}
