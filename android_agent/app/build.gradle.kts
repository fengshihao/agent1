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

val releaseSigningPropertiesFile = rootProject.file("release-signing.properties")

fun gitCommitCount(repoRoot: java.io.File): Int =
    try {
        val proc = ProcessBuilder("git", "rev-list", "--count", "HEAD")
            .directory(repoRoot)
            .redirectErrorStream(true)
            .start()
        val text = proc.inputStream.bufferedReader().readText().trim()
        proc.waitFor()
        text.toIntOrNull()?.coerceAtLeast(1) ?: 1
    } catch (_: Exception) {
        1
    }

/**
 * 单调递增的 versionCode，避免反复打 debug 包时因 versionCode 仍为 1 而无法覆盖安装。
 * 可覆盖：环境变量 VERSION_CODE / VERSION_NAME；CI 需 checkout fetch-depth: 0 以保证 git 计数正确。
 */
fun agent1VersionCode(): Int {
    System.getenv("VERSION_CODE")?.toIntOrNull()?.let { return it }
    val repoRoot = rootProject.layout.projectDirectory.dir("..").asFile
    // GitHub Actions：取 max(run_number, 提交数)，避免从「100000+提交数」切到「100000+run」后倒退
    System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()?.let { run ->
        val ordinal = maxOf(run.coerceAtLeast(1), gitCommitCount(repoRoot))
        return 100_000 + ordinal
    }
    return 10_000 + gitCommitCount(repoRoot)
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
    // Compose BOM 2025.08+（Foundation 1.9）要求 compileSdk ≥ 35
    compileSdk = 35

    defaultConfig {
        applicationId = "com.agent1.android"
        minSdk = 26
        targetSdk = 34
        // 只出 arm64。依赖里的 androidx graphics-path 会带 armeabi-v7a / x86 / x86_64，这里丢掉。
        ndk {
            abiFilters += "arm64-v8a"
        }
        versionCode = agent1VersionCode()
        versionName = agent1VersionName()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val localProps = Properties()
        rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use {
            localProps.load(it)
        }

        fun envOrProp(name: String, default: String = ""): String {
            val fromLocal = localProps.getProperty(name)?.trim().orEmpty()
            if (fromLocal.isNotEmpty()) {
                return fromLocal.replace("\"", "\\\"")
            }
            return providers.gradleProperty(name).orElse(System.getenv(name) ?: default).get()
                .replace("\"", "\\\"")
        }

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
        val cdnHost = envOrProp(
            "AGENT1_CDN_HOST",
            envOrProp("AGENT1_CDN_BASE_URL", envOrProp("agent1.cdn.host")),
        )
        buildConfigField("String", "AGENT1_CDN_HOST", "\"$cdnHost\"")
    }

    signingConfigs {
        getByName("debug") {
            // 团队共用 debug 签名，避免不同机器 ~/.android/debug.keystore 不一致导致只能卸载重装
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (releaseSigningPropertiesFile.isFile) {
            create("release") {
                val props = Properties().apply {
                    releaseSigningPropertiesFile.inputStream().use { load(it) }
                }
                val storePath = props.getProperty("storeFile")?.trim().orEmpty()
                require(storePath.isNotEmpty()) {
                    "release-signing.properties 缺少 storeFile"
                }
                storeFile = rootProject.file(storePath)
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // 当前本机与 CI 都用仓库内 debug.keystore。
            // 以后若放了 release-signing.properties，才改用独立 release 签名。
            signingConfig = if (releaseSigningPropertiesFile.isFile) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
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
            // 能上云的 Skill / Package 不进 APK；registry 动态安装。
            excludes += "/agent-home/weizhi-packages/**"
            excludes += "/agent-home/skills-bundled/**"
            excludes += "/agent-home/skills-catalog/**"
            excludes += "/agent-home/catalog/scripts/**"
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

// 源码联编时，Java 与 libweizhijni.so 必须同一次 weizhi 树产物。
// copyNativeLib 只拷贝已有 so；这里在拷贝前补齐 nativeSetPackageRoot，避免 release run_js 链接失败。
if (weizhiAndroidRoot != null && findProject(":weizhi") != null) {
    val ensureWeizhiJni = tasks.register<Exec>("ensureWeizhiAndroidJni") {
        group = "weizhi"
        description = "Rebuild libweizhijni.so when JNI sources are newer than the packaged library"
        commandLine("bash", rootProject.file("../scripts/ensure-weizhi-android-jni.sh").absolutePath)
    }
    project(":weizhi").afterEvaluate {
        val hook = tasks.findByName("copyNativeLib") ?: tasks.named("preBuild").get()
        hook.dependsOn(ensureWeizhiJni)
    }
}

dependencies {
    // markdown-renderer 0.35 需要 Foundation 1.8+ 的 BasicText；1.9 起 LazyColumn 才能用 LazyLayoutCacheWindow 预组合视口外条目。
    val composeBom = platform("androidx.compose:compose-bom:2025.08.01")

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
        if (findProject(":weizhi-video") != null) {
            // 视频剪辑（issue #98）：插件 SO + weizhi:video JS 包随 AAR 原子交付，syncBundled 自动安装。
            implementation(project(":weizhi-video"))
        }
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
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
    // JVM 单测里 android.jar 的 org.json 是 stub（方法直接抛异常），
    // JSON 解析类测试（formatToolResult 等）需要真实实现。
    testImplementation("org.json:json:20240303")

    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.activity:activity-compose:1.9.2")
}
