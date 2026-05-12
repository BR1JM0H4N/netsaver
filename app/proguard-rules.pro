# =========================================
# NetSaver ProGuard Rules
# =========================================

# Keep source file + line numbers
# Better crash logs/debugging
-keepattributes SourceFile,LineNumberTable

# Keep annotations
-keepattributes *Annotation*

# =========================================
# WebView
# =========================================

# Keep JavaScript interfaces
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Prevent WebView classes from being stripped
-keep class android.webkit.** { *; }

# =========================================
# OkHttp / Okio
# =========================================

-dontwarn okhttp3.**
-dontwarn okio.**

# Keep okhttp internals
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# =========================================
# Media3 / ExoPlayer
# =========================================

-keep class androidx.media3.** { *; }

-dontwarn androidx.media3.**

# =========================================
# RecyclerView
# =========================================

-keep class androidx.recyclerview.** { *; }

# =========================================
# AppCompat / Material
# =========================================

-keep class androidx.appcompat.** { *; }

-keep class com.google.android.material.** { *; }

# =========================================
# Activities
# =========================================

-keep public class * extends androidx.appcompat.app.AppCompatActivity

# =========================================
# Prevent enum stripping
# =========================================

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# =========================================
# Keep native methods
# =========================================

-keepclasseswithmembernames class * {
    native <methods>;
}

# =========================================
# Preserve constructors
# =========================================

-keepclassmembers class * {
    public <init>(...);
}

# =========================================
# Suppress common warnings
# =========================================

-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**