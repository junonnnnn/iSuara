# ProGuard rules for iSuara

# TFLite
-keep class org.tensorflow.** { *; }
-dontwarn org.tensorflow.**

# MediaPipe
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**

# Keep model-related data classes
-keep class com.isuara.app.ml.** { *; }
