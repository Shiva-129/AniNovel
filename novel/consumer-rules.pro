# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in the Android SDK tools proguard/proguard-android.txt

# Keep QuickNovel provider classes (loaded reflectively)
-keep class eu.kanade.novel.providers.** { *; }

# Keep NiceHttp / OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Keep Jackson serialization
-keep class com.fasterxml.jackson.** { *; }
-keepattributes *Annotation*,EnclosingMethod,Signature
-keepnames class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.databind.**

# Keep pdfbox
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn org.apache.pdfbox.**

# Keep ML Kit
-keep class com.google.mlkit.** { *; }
