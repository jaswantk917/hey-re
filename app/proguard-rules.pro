-keep class com.sun.jna.* { *; }
-keepclassmembers class * extends com.sun.jna.* { public *; }

# Vosk calls into JNA by reflection from native code
-keep class org.vosk.** { *; }
# protobuf-lite reads its generated fields by reflection
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }
# LiteRT (TensorFlow Lite) looks up its Java classes from JNI
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**
# JNA's desktop-only AWT helpers never run on Android
-dontwarn java.awt.**
# dicio-numbers picks each language's parser and formatter by reflection
-keep class org.dicio.numbers.lang.** { <init>(...); }
# ML Kit GenAI (pixel flavour) wires its components and telemetry up by reflection
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_genai_** { *; }
-keep class com.google.android.datatransport.** { *; }

# Shell.run reaches Shizuku.newProcess by reflection
-keep class rikka.shizuku.** { *; }
