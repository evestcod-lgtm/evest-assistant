# Vosk / JNA native bindings
-keep class org.vosk.** { *; }
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.Structure { public *; }
-dontwarn com.sun.jna.**

# Keep our own model/data classes used for JSON parsing
-keep class com.evest.assistant.data.** { *; }
-keep class com.evest.assistant.nlu.** { *; }

-dontwarn okhttp3.**
-dontwarn okio.**
