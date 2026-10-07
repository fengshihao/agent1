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
    // GitHub Actions：run_number 单调递增，避免仅装 CI 包时因 shallow/回滚导致 versionCode 倒退
    System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()?.let { run ->
        return 100_000 + run.coerceAtLeast(1)
    }
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

fun agent1VersionName(): String {
    System.getenv("VERSION_NAME")?.takeIf { it.isNotBlank() }?.let { return it }
    val code = agent1VersionCode()
    val patch = when {
        code >= 100_000 -> code - 100_000
        else -> code - 10_000
    }
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
    // Compose BOM 2025.04+（Foundation 1.8）要求 compileSdk ≥ 35
    compileSdk = 35

    defaultConfig {
        applicationId = "com.agent1.android"
        minSdk = 26
        targetSdk = 34
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

    signingConfigs {
        getByName("debug") {
            // 团队共用 debug 签名，避免不同机器 ~/.android/debug.keystore 不一致导致只能卸载重装
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
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
    sourceSets.named("main") {
        if (weizhiIntegrated) {
            java.srcDir("../../java_agent/weizhi-bridge/src/shared/java")
            java.srcDir("src/weizhi/java")
            assets.srcDir("src/weizhi/assets")
        }
    }

    @Suppress("DEPRECATION")
    applicationVariants.configureEach {
        outputs.configureEach {
            val impl = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            impl.outputFileName = "agent1-android-${buildType.name}.apk"
        }
    }
}

dependencies {
    // markdown-renderer 0.35 调用含 TextAutoSize 的 BasicText（Compose Foundation 1.8+）；2025.02 BOM 仍为 1.7.8 会 NoSuchMethodError
    val composeBom = platform("androidx.compose:compose-bom:2025.04.01")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-process:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.navigation:navigation-compose:2.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    implementation("com.agent1:java-agent-core:0.1.0-SNAPSHOT") {
        // 能力库走系统 SQLiteDatabase；sqlite-jdbc 的桌面 .so 不能打进 APK。
        exclude(group = "org.xerial", module = "sqlite-jdbc")
    }
    if (findProject(":weizhi") != null) {
        implementation(project(":weizhi"))
        implementation(project(":caps"))
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
        implementation(w("artifact.weizhi"))
        implementation(w("artifact.caps"))
    }
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.mikepenz:multiplatform-markdown-renderer-m3:0.35.0")
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
