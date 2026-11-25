# Add project specific ProGuard rules here.
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations

# Kotlinx Serialization
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
    @kotlinx.serialization.SerialName <fields>;
}
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class kotlinx.serialization.** { *; }
-keep class com.algermusic.player.model.** { *; }
-keep,includedescriptorclasses class com.algermusic.player.model.**$$serializer { *; }
-keepclassmembers class com.algermusic.player.model.** {
    *** Companion;
}

# WebView JS Bridge for LX Music custom scripts
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.algermusic.player.data.unblock.LxScriptHostService$** { *; }

# Native / Compose Backdrop & TagLib libraries
-keep class com.kyant.taglib.** { *; }
-keep class com.kyant.backdrop.** { *; }
-keep class com.kyant.shapes.** { *; }

# Media3 ExoPlayer / MediaSession / Jellyfin FFmpeg Decoder
-keep class androidx.media3.** { *; }
-keep class org.jellyfin.media3.** { *; }

# Optional TLS providers referenced by OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
