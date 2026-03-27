# ═══════════════════════════════════════════
# GlassFiles ProGuard / R8 Rules
# ═══════════════════════════════════════════

-optimizationpasses 5
-allowaccessmodification
-repackageclasses 'g'
-overloadaggressively
-flattenpackagehierarchy 'g'

# Remove logs
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Kotlin
-dontwarn kotlin.**
-keep class kotlin.Metadata { *; }
-keep class kotlinx.coroutines.** { *; }

# Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**
-keepclassmembers class * { @androidx.compose.runtime.Composable <methods>; }

# Kyant Backdrop
-keep class com.kyant.backdrop.** { *; }
-keep class com.kyant.shapes.** { *; }

# Glance Widgets
-keep class androidx.glance.** { *; }
-keep class com.glassfiles.widget.** { *; }

# ML Kit
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# Apache HTTP (Google API dependency)
-dontwarn javax.naming.**
-dontwarn javax.naming.directory.**
-dontwarn javax.naming.ldap.**
-dontwarn org.ietf.jgss.**
-dontwarn org.apache.http.**
-dontwarn com.sun.net.httpserver.**

# Google Drive + Auth
-keep class com.google.api.** { *; }
-keep class com.google.auth.** { *; }
-dontwarn com.google.api.**
-dontwarn com.google.auth.**
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# CameraX
-keep class androidx.camera.** { *; }

# Coil
-keep class coil.** { *; }

# Shizuku
-keep class rikka.shizuku.** { *; }
-dontwarn rikka.shizuku.**

# Apache Commons
-keep class org.apache.commons.compress.** { *; }
-dontwarn org.apache.commons.compress.**
-keep class org.apache.commons.net.** { *; }
-dontwarn org.apache.commons.net.**

# JNI Native
-keepclasseswithmembernames class * { native <methods>; }
-keep class com.glassfiles.data.terminal.PtyNative { *; }
-keep class com.glassfiles.security.NativeSecurity { *; }

# Security
-keep class com.glassfiles.security.** { *; }

# Android components
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep class androidx.core.content.FileProvider { *; }

# Enums
-keepclassmembers enum * { public static **[] values(); public static ** valueOf(java.lang.String); }

# Parcelable
-keepclassmembers class * implements android.os.Parcelable { public static final ** CREATOR; }

# Remove Kotlin null checks in release
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void checkNotNull(...);
    public static void checkNotNullParameter(...);
    public static void checkParameterIsNotNull(...);
    public static void checkNotNullExpressionValue(...);
}
