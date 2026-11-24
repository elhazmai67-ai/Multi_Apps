# Room / Kotlin defaults are covered by consumer rules.
-keep class com.linkvault.app.widget.** { *; }
-keepattributes *Annotation*

# Downloader (yt-dlp wrapper) is driven through reflection/JSON; keep it intact.
-keep class com.yausername.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.**
-dontwarn org.apache.commons.compress.**
-dontwarn org.tukaani.xz.**

# ---- Downloader hardening (release builds only) ----
# yt-dlp's wrapper unpacks Python/ffmpeg, spawns processes and parses JSON through reflection and
# generated serializers; shrinking or renaming any of it crashes the app the moment a download starts.
-keep class com.yausername.** { *; }
-keep interface com.yausername.** { *; }
-keepclassmembers class com.yausername.** { *; }
-keep class org.apache.commons.** { *; }
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class **$$serializer { *; }
-keepattributes Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations,AnnotationDefault,Exceptions,*Annotation*
-keep class kotlin.Metadata { *; }
-keep class com.linkvault.app.download.** { *; }
-keep class com.linkvault.app.bubble.** { *; }
-dontwarn org.slf4j.**
-dontwarn javax.annotation.**
-dontwarn org.codehaus.**
-dontwarn kotlinx.serialization.**
-keep class com.linkvault.app.tools.** { *; }
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**
-dontwarn com.gemalto.jp2.**
-dontwarn org.bouncycastle.**
-keep class net.lingala.zip4j.** { *; }
