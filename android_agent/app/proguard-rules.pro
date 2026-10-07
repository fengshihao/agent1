# Weizhi AAR consumer rules merge WeizhiEngine / WeizhiLimits / ReminderReceiver automatically.

# WebView wasm 桥（Gson 字段名 + @JavascriptInterface）
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*

-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

-keep class com.weizhi.agent.web.** { *; }

-keepclassmembers class com.weizhi.agent.web.** {
    <fields>;
}

# OkHttp / Gson（WebView 与网络层）
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class com.google.gson.** { *; }

# security-crypto (Tink) 可选注解，compileOnly 未打进 APK
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.RestrictedApi

# Compose（由编译器生成 keep；保留 line numbers 便于 release 崩溃栈）
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
