# Keep JavascriptInterface methods used by WebView media sniffer, touch context, and blob downloads
-keepattributes *Annotation*,JavascriptInterface
-keepclassmembers class com.example.browser.VoltMediaJsBridge {
    @android.webkit.JavascriptInterface <methods>;
}
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
