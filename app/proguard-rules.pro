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

# androidx.security-crypto pulls in Google Tink, which references
# com.google.errorprone.annotations.* (CanIgnoreReturnValue, CheckReturnValue,
# Immutable, RestrictedApi, etc.) as compile-time-only annotations. The
# errorprone annotations artifact itself isn't a runtime dependency, so R8
# can't find these classes during shrinking. They're safe to ignore since
# annotations have no effect at runtime.
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.crypto.tink.**
-keep class com.google.crypto.tink.** { *; }
