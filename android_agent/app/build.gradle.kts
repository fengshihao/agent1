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

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("../config/detekt.yml"))
    source.setFrom("src/main/java")
    parallel = true
}

android {
    namespace = "com.agent1.android"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.agent1.android"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
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
    sourceSets.named("main") {
        if (weizhiIntegrated) {
            java.srcDir("../../java_agent/weizhi-bridge/src/shared/java")
            java.srcDir("src/weizhi/java")
        }
    }
}

androidComponents {
    onVariants { variant ->
        val buildType = variant.buildType ?: "debug"
        variant.outputs.forEach { output ->
            output.outputFileName.set("agent1-android-$buildType.apk")
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
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    implementation("com.agent1:java-agent-core:0.1.0-SNAPSHOT")
    if (findProject(":weizhi") != null) {
        implementation(project(":weizhi"))
        implementation(project(":caps"))
        implementation(project(":agent-tools"))
        implementation(project(":agent-tools-webview"))
        implementation(project(":agent-tools-mcp"))
    } else if (weizhiPrebuiltBase != null) {
        val coords = java.util.Properties().apply {
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
        implementation(w("artifact.agent-tools"))
        implementation(w("artifact.agent-tools-webview"))
        implementation(w("artifact.agent-tools-mcp"))
    }
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.mikepenz:multiplatform-markdown-renderer-m3:0.27.0")
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
