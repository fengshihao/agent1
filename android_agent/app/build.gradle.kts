import java.security.MessageDigest
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("io.gitlab.arturbosch.detekt")
}

val weizhiAndroidRoot = sequenceOf(
    rootProject.file("../../weizhi/android"),
    rootProject.file("../weizhi/android"),
).firstOrNull { it.isDirectory }

val weizhiPrebuiltBase = rootProject.file("weizhi-prebuilt").takeIf {
    it.resolve("maven").isDirectory && it.resolve("coordinates.properties").isFile
}

val weizhiIntegrated = weizhiAndroidRoot != null || weizhiPrebuiltBase != null

/**
 * 单调递增的 versionCode，避免反复打 debug 包时因 versionCode 仍为 1 而无法覆盖安装。
 * 可覆盖：环境变量 VERSION_CODE / VERSION_NAME；CI 需 checkout fetch-depth: 0 以保证 git 计数正确。
 */
fun agent1VersionCode(): Int {
    System.getenv("VERSION_CODE")?.toIntOrNull()?.let { return it }
    val repoRoot = rootProject.layout.projectDirectory.dir("..").asFile
    return try {
        val proc = ProcessBuilder("git", "rev-list", "--count", "HEAD")
            .directory(repoRoot)
            .redirectErrorStream(true)
            .start()
        val text = proc.inputStream.bufferedReader().readText().trim()
        proc.waitFor()
        val commitCount = text.toIntOrNull()?.coerceAtLeast(1) ?: 1
        10_000 + commitCount
    } catch (_: Exception) {
        10_001
    }
}

fun sha256File(file: java.io.File): String {
    val md = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buf = ByteArray(8192)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            md.update(buf, 0, n)
        }
    }
    return md.digest().joinToString("") { "%02x".format(it) }
}

fun gitHead(dir: java.io.File): String {
    return try {
        val proc = ProcessBuilder("git", "rev-parse", "HEAD")
            .directory(dir)
            .redirectErrorStream(true)
            .start()
        val text = proc.inputStream.bufferedReader().readText().trim()
        proc.waitFor()
        text.ifBlank { "unknown" }
    } catch (_: Exception) {
        "unknown"
    }
}

/** 打包时记下 weizhi Java 与 libweizhijni.so 是否来自同一次构建。 */
fun weizhiPackageStampText(androidRoot: java.io.File?): String {
    if (androidRoot == null) {
        return "weizhi.source=absent\nnote=此 APK 未联编 weizhi 源码（诊断包或未 sync）。\n"
    }
    val repo = androidRoot.parentFile
    val javaFile = repo.resolve("java/com/weizhi/WeizhiEngine.java")
    val jniSo = androidRoot.resolve("weizhi/src/main/jniLibs/arm64-v8a/libweizhijni.so")
    val builtSo = repo.resolve("build-android/arm64-v8a/stripped/libweizhijni.so").takeIf { it.isFile }
        ?: repo.resolve("build-android/arm64-v8a/libweizhijni.so")
    val jniHash = if (jniSo.isFile) sha256File(jniSo) else "missing"
    val builtHash = if (builtSo.isFile) sha256File(builtSo) else "missing"
    val match = when {
        jniHash == "missing" || builtHash == "missing" -> "unknown"
        jniHash == builtHash -> "yes"
        else -> "NO"
    }
    return buildString {
        appendLine("weizhi.git=${gitHead(repo)}")
        appendLine("WeizhiEngine.java.sha256=${if (javaFile.isFile) sha256File(javaFile) else "missing"}")
        appendLine("jniLibs.so.sha256=$jniHash")
        appendLine("jniLibs.so.bytes=${if (jniSo.isFile) jniSo.length() else 0}")
        appendLine("build-android.so.sha256=$builtHash")
        appendLine("java_and_so_same_build=$match")
        appendLine("d4_jni_commit=361478c runJs filename；其父提交 bcbb1f4 为旧 JNI")
        appendLine("note=java_and_so_same_build=NO 表示 jniLibs 里的 SO 不是本次 build-android 的产物，AAR 与 SO 可能不配套。")
    }
}

fun agent1VersionName(): String {
    System.getenv("VERSION_NAME")?.takeIf { it.isNotBlank() }?.let { return it }
    val patch = agent1VersionCode() - 10_000
    return "0.1.$patch"
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("../config/detekt.yml"))
    source.setFrom("src/main/java")
    parallel = true
}

android {
    namespace = "com.agent1.android"
    compileSdk = 35

    flavorDimensions += "distribution"
    productFlavors {
        create("app") {
            dimension = "distribution"
            isDefault = true
            val probe = providers.gradleProperty("weizhiProbeLabel").orElse("").get()
                .filter { it.isLetterOrDigit() }
                .take(16)
            if (probe.isNotEmpty()) {
                applicationIdSuffix = ".wz$probe"
                versionNameSuffix = "-wz-$probe"
            }
        }
        create("diagnostic") {
            dimension = "distribution"
            applicationIdSuffix = ".diagnostic"
            versionNameSuffix = "-diag"
            buildConfigField("boolean", "WEIZHI_INTEGRATED", "false")
        }
    }

    defaultConfig {
        applicationId = "com.agent1.android"
        minSdk = 26
        targetSdk = 35
        versionCode = agent1VersionCode()
        versionName = agent1VersionName()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        fun envOrProp(name: String, default: String = ""): String =
            providers.gradleProperty(name).orElse(System.getenv(name) ?: default).get().replace("\"", "\\\"")

        val qwenApiKey = envOrProp("QWEN_API_KEY", envOrProp("DASHSCOPE_API_KEY"))
        val qwenBaseUrl = envOrProp(
            "QWEN_BASE_URL",
            envOrProp("DASHSCOPE_BASE_URL", "https://dashscope.aliyuncs.com/compatible-mode/v1"),
        )
        val qwenModel = envOrProp("QWEN_MODEL", "qwen3.7-flash")
        val dashscopeApiKey = envOrProp("DASHSCOPE_API_KEY", qwenApiKey)
        val dashscopeBaseUrl = envOrProp("DASHSCOPE_BASE_URL", qwenBaseUrl)

        buildConfigField("String", "QWEN_API_KEY", "\"$qwenApiKey\"")
        buildConfigField("String", "QWEN_BASE_URL", "\"$qwenBaseUrl\"")
        buildConfigField("String", "QWEN_MODEL", "\"$qwenModel\"")
        buildConfigField("String", "DEFAULT_MODEL", "\"$qwenModel\"")
        buildConfigField("String", "DASHSCOPE_API_KEY", "\"$dashscopeApiKey\"")
        buildConfigField("String", "DASHSCOPE_BASE_URL", "\"$dashscopeBaseUrl\"")
        buildConfigField("boolean", "WEIZHI_INTEGRATED", weizhiIntegrated.toString())
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    sourceSets.named("app") {
        if (weizhiIntegrated) {
            java.srcDir("../../java_agent/weizhi-bridge/src/shared/java")
            java.srcDir("src/weizhi/java")
        }
    }
    sourceSets.named("main") {
        assets.srcDir("build/generated/weizhiPackageStamp")
    }

    @Suppress("DEPRECATION")
    applicationVariants.configureEach {
        outputs.configureEach {
            val impl = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            impl.outputFileName = "agent1-android-${flavorName}-${buildType.name}.apk"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-process:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.navigation:navigation-compose:2.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation(composeBom)
    implementation("androidx.graphics:graphics-path:1.0.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    implementation("com.agent1:java-agent-core:0.1.0-SNAPSHOT")
    if (findProject(":weizhi") != null) {
        add("appImplementation", project(":weizhi"))
        add("appImplementation", project(":caps"))
        add("appImplementation", project(":agent-tools"))
        add("appImplementation", project(":agent-tools-webview"))
        add("appImplementation", project(":agent-tools-mcp"))
    } else if (weizhiPrebuiltBase != null) {
        val coords = Properties().apply {
            weizhiPrebuiltBase.resolve("coordinates.properties").inputStream().use { load(it) }
        }
        fun w(key: String): String {
            val g = coords.getProperty("group")?.trim().orEmpty()
            val v = coords.getProperty("version")?.trim().orEmpty()
            val a = coords.getProperty(key)?.trim().orEmpty()
            return "$g:$a:$v"
        }
        add("appImplementation", w("artifact.weizhi"))
        add("appImplementation", w("artifact.caps"))
        add("appImplementation", w("artifact.agent-tools"))
        add("appImplementation", w("artifact.agent-tools-webview"))
        add("appImplementation", w("artifact.agent-tools-mcp"))
    }
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.mikepenz:multiplatform-markdown-renderer-m3:0.27.0")
    implementation("io.coil-kt:coil-compose:2.6.0")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")

    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.activity:activity-compose:1.9.2")
}

val weizhiStampDir = layout.buildDirectory.dir("generated/weizhiPackageStamp")
tasks.register("writeWeizhiPackageStamp") {
    outputs.dir(weizhiStampDir)
    doLast {
        val dir = weizhiStampDir.get().asFile
        dir.mkdirs()
        dir.resolve("weizhi-package-stamp.txt").writeText(weizhiPackageStampText(weizhiAndroidRoot))
    }
}
tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }.configureEach {
    dependsOn("writeWeizhiPackageStamp")
}
