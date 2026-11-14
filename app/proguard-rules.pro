# Keep kotlinx.serialization generated serializers.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class app.potatotv.**$$serializer { *; }
-keepclassmembers class app.potatotv.** {
    *** Companion;
}
-keepclasseswithmembers class app.potatotv.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Media3 / ExoPlayer
-dontwarn androidx.media3.**
-keep class androidx.media3.** { *; }

# Keep the JS bridge methods reachable from WebView.
-keepclassmembers class app.potatotv.ui.browser.WebBridge {
    public *;
}
