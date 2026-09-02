import java.util.Properties
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.isuara.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.isuara.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        // 1. Load the local.properties file manually using the imported Properties class
        val localProperties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localPropertiesFile.inputStream().use { localProperties.load(it) }
        }

        // 2. Extract the key from the loaded properties object
        val gonkaKey = localProperties.getProperty("GONKA_API_KEY") ?: ""

        // 3. Define the build config field so the app can see it
        buildConfigField("String", "GONKA_API_KEY", "\"$gonkaKey\"")

        // Gemini keys for expressive TTS, comma-joined. Several are supported
        // because the TTS endpoint rate-limits hard on this tier — measured 429s
        // on 3 of 5 consecutive calls — and rotating keys is what keeps a live
        // demo speaking. Numbered keys are optional; the unsuffixed one is not.
        val geminiKeys = listOfNotNull(
            localProperties.getProperty("GEMINI_API_KEY"),
            localProperties.getProperty("GEMINI_API_KEY_2"),
            localProperties.getProperty("GEMINI_API_KEY_3"),
        ).filter { it.isNotBlank() }
        buildConfigField("String", "GEMINI_API_KEYS", "\"${geminiKeys.joinToString(",")}\"")

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Don't compress TFLite models, MediaPipe task bundles or the ONNX model
    androidResources {
        noCompress += listOf("tflite", "task", "onnx")
    }
}

// JDK toolchain — tells Gradle to use JDK 17 for compilation
kotlin {
    jvmToolchain(17)
}

dependencies {
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    // Compose
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.material:material-icons-extended")

    // CameraX
    val cameraVersion = "1.4.1"
    implementation("androidx.camera:camera-core:$cameraVersion")
    implementation("androidx.camera:camera-camera2:$cameraVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraVersion")
    implementation("androidx.camera:camera-view:$cameraVersion")

    // MediaPipe Tasks — Pose + Hand Landmarker
    implementation("com.google.mediapipe:tasks-vision:0.10.21")

    // TFLite — interpreter + delegates
    implementation("org.tensorflow:tensorflow-lite:2.16.1")
    implementation("org.tensorflow:tensorflow-lite-api:2.16.1")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
    implementation("org.tensorflow:tensorflow-lite-gpu:2.16.1")
    implementation("org.tensorflow:tensorflow-lite-select-tf-ops:2.16.1")
    implementation("org.tensorflow:tensorflow-lite-gpu-api:2.16.1")

    // ONNX Runtime — facial expression recognition (EmotiEffLib EfficientNet-B0).
    // A second inference runtime beside TFLite is deliberate: EmotiEffLib ships
    // ONNX weights, and converting them would add a fragile offline step whose
    // numerical drift we would have to police ourselves.
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.20.0")

    // GonkaRouter — reached with the Anthropic SDK (GonkaRouter speaks the
    // Messages API); base URL is overridden in service/GonkaClient.kt
    implementation("com.anthropic:anthropic-java:2.59.0")

    // Kotlin coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // JSON parsing
    implementation("org.json:json:20240303")

    // Core
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")

    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Unit tests — model latency benchmark. runBlocking comes from the existing
    // coroutines dependency, which testImplementation inherits.
    testImplementation("junit:junit:4.13.2")
}