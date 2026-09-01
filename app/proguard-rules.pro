# ProGuard rules for iSuara

# TFLite
-keep class org.tensorflow.** { *; }
-dontwarn org.tensorflow.**

# MediaPipe
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**

# Gemini
-keep class com.google.ai.** { *; }
-dontwarn com.google.ai.**

# Keep model-related data classes
-keep class com.isuara.app.ml.** { *; }

# ---------------------------------------------------------------------------
# google-genai (Gemini) is a server-side SDK. Its dependency graph references
# build-time and JRE-only classes that never exist on Android. R8 aborts on the
# dangling references unless told they are expected.
# ---------------------------------------------------------------------------

# AutoValue's shaded JavaPoet is an annotation processor, not runtime code.
-dontwarn javax.lang.model.**
-dontwarn javax.annotation.**
-dontwarn autovalue.shaded.**
-dontwarn com.google.auto.value.**

# Server-side transports and telemetry we do not use; the REST path is OkHttp.
-dontwarn org.apache.http.**
-dontwarn android.net.http.**
-dontwarn io.grpc.**
-dontwarn io.opencensus.**
-dontwarn com.google.api.**
-dontwarn com.google.auth.**
-dontwarn com.google.errorprone.**
-dontwarn com.google.j2objc.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
