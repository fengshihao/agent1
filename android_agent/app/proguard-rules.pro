# Weizhi AAR consumer rules merge WeizhiEngine / WeizhiLimits / ReminderReceiver automatically.

# ProductivityHostAssembly 用类名字符串反射调用 create。不保留的话 R8 会改名并裁掉该方法，
# 启动即 NoSuchMethodException，会话建不起来。
-keep class com.agent1.android.productivity.logic.business.platform.WeizhiHostLoader {
    public static com.agent1.javaagent.session.ProductivityAgentHost create(android.content.Context, java.nio.file.Path, com.agent1.javaagent.config.AgentRuntimeConfig);
}

# AnnotatedTools 靠运行时注解发现工具。R8 会把这些只被反射调用的方法裁掉，
# 启动报 no @Tool methods。
-keep @interface com.agent1.javaagent.tool.anno.Tool
-keep @interface com.agent1.javaagent.tool.anno.ToolParam
-keepclassmembers class * {
    @com.agent1.javaagent.tool.anno.Tool <methods>;
}

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
