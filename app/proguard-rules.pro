# Keep JavascriptInterface methods used by WebView media sniffer, touch context, and blob downloads
-keepattributes *Annotation*,JavascriptInterface
-keepclassmembers class com.example.browser.VoltMediaJsBridge {
    @android.webkit.JavascriptInterface <methods>;
}
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Room Database
-keep class * extends androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.room-compiler**

# WireGuard Tunnel & JNI Go backend
-keep class com.wireguard.** { *; }
-dontwarn com.wireguard.**

# Media3 / ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**
