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
        // Three keys, one per debate agent. Each MUST come from a different
        // Google Cloud project: the free-tier limit is 5 requests/minute per
        // PROJECT, so keys issued from one project share a single bucket and
        // buy nothing. Slot 1 falls back to the original single-key name.
        val geminiKey1 = localProperties.getProperty("GEMINI_API_KEY_1")
            ?: localProperties.getProperty("GEMINI_API_KEY") ?: ""
        val geminiKey2 = localProperties.getProperty("GEMINI_API_KEY_2") ?: ""
        val geminiKey3 = localProperties.getProperty("GEMINI_API_KEY_3") ?: ""

        // 3. Define the build config field so the app can see it
        buildConfigField("String", "GONKA_API_KEY", "\"$gonkaKey\"")
        buildConfigField("String", "GEMINI_API_KEY_1", "\"$geminiKey1\"")
        buildConfigField("String", "GEMINI_API_KEY_2", "\"$geminiKey2\"")
        buildConfigField("String", "GEMINI_API_KEY_3", "\"$geminiKey3\"")

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

    // Don't compress TFLite models or MediaPipe task bundles
    androidResources {
        noCompress += listOf("tflite", "task")
    }

    // google-genai is a server-side SDK: several of its jars ship the same
    // META-INF metadata, which the packager refuses to merge. None of it is
    // needed at runtime on Android.
    packaging {
        resources {
            excludes += setOf(
                "META-INF/INDEX.LIST",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/license.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/notice.txt",
                "META-INF/*.SF",
                "META-INF/*.DSA",
                "META-INF/*.RSA",
            )
        }
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

    // GonkaRouter — reached with the Anthropic SDK (GonkaRouter speaks the
    // Messages API); base URL is overridden in service/GonkaClient.kt
    implementation("com.anthropic:anthropic-java:2.59.0")

    // Gemini — the active provider. Server-side SDK, but OkHttp-based rather
    // than java.net.http, which is what makes it viable on Android.
    implementation("com.google.genai:google-genai:1.68.0") {
        // MediaPipe brings protobuf-javalite, which Android needs; google-genai
        // brings full protobuf-java. They define the same classes, so dexing
        // fails on duplicates. The REST path we use goes over OkHttp + Gson, so
        // dropping the JRE protobuf should cost us nothing.
        exclude(group = "com.google.protobuf", module = "protobuf-java")
    }

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