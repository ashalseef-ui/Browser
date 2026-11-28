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

# WorkManager background download queue
-keep class com.example.media.MediaDownloadWorker { *; }
-keep class * extends androidx.work.CoroutineWorker { *; }
-keep class * extends androidx.work.Worker { *; }

# Transitive snakeyaml optional JavaBeans classes unavailable on Android
-dontwarn java.beans.BeanInfo
-dontwarn java.beans.FeatureDescriptor
-dontwarn java.beans.IntrospectionException
-dontwarn java.beans.Introspector
-dontwarn java.beans.PropertyDescriptor

