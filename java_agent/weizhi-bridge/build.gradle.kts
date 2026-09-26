plugins {
    `java-library`
}

group = "com.agent1"
version = "0.1.0-SNAPSHOT"

val weizhiRepo: java.io.File =
    providers.gradleProperty("weizhiRepo")
        .map { file(it) }
        .orElse(
            sequenceOf(file("../../../weizhi"), file("../../weizhi"))
                .firstOrNull { it.isDirectory }
                ?: file("../../../weizhi"),
        )
        .get()

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    api(project(":core"))
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
}

val syncMcpSources = tasks.register<Copy>("syncMcpSources") {
    from(weizhiRepo.resolve("android/agent-tools-mcp/src/main/java")) {
        exclude("**/McpLog.java")
    }
    into(layout.buildDirectory.dir("generated/mcp-sources"))
}

val syncWebViewJvmSources = tasks.register<Copy>("syncWebViewJvmSources") {
    from(weizhiRepo.resolve("android/agent-tools-webview/src/main/java")) {
        include("com/weizhi/agent/web/WebViewTask.java")
        include("com/weizhi/agent/web/BridgeCodec.java")
        include("com/weizhi/agent/web/WebViewQueue.java")
    }
    into(layout.buildDirectory.dir("generated/webview-jvm-sources"))
}

sourceSets {
    named("main") {
        java {
            srcDir(weizhiRepo.resolve("java"))
            srcDir(weizhiRepo.resolve("android/agent-tools/src/main/java"))
            srcDir(layout.buildDirectory.dir("generated/mcp-sources"))
            srcDir(layout.buildDirectory.dir("generated/webview-jvm-sources"))
            srcDir("src/shared/java")
            srcDir("src/main/java")
            exclude("**/AgentToolsBundle.java")
            exclude("**/AssetSkillRepository.java")
        }
    }
}

tasks.named("compileJava") {
    dependsOn(syncMcpSources, syncWebViewJvmSources)
}

tasks.test {
    useJUnitPlatform()
    val nativeDir = weizhiRepo.resolve("build")
    jvmArgs("-Djava.library.path=${nativeDir.absolutePath}")
    environment("AGENT1_WEIZHI_REPO", weizhiRepo.absolutePath)
}
