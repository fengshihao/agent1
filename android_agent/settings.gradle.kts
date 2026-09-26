pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.aliyun.com/repository/public")
    }
}

val weizhiAndroidSource = sequenceOf(
    file("../../weizhi/android"),
    file("../weizhi/android"),
).firstOrNull { it.isDirectory }

val weizhiPrebuiltBase = file("weizhi-prebuilt").takeIf {
    it.resolve("maven").isDirectory && it.resolve("coordinates.properties").isFile
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven(url = uri("../java_agent/build/local-maven"))
        if (weizhiPrebuiltBase != null) {
            maven(url = uri(weizhiPrebuiltBase.resolve("maven")))
        }
        google()
        mavenCentral()
        maven("https://maven.aliyun.com/repository/public")
    }
}

rootProject.name = "DynamicUiDemo"
include(":app")

if (weizhiAndroidSource != null) {
    val root = weizhiAndroidSource
    // weizhi 子模块 apply from rootProject.file("weizhi-maven-publish.gradle")；联编时 root 为 android_agent
    weizhiAndroidSource.resolve("weizhi-maven-publish.gradle").takeIf { it.isFile }?.copyTo(
        settings.rootDir.resolve("weizhi-maven-publish.gradle"),
        overwrite = true,
    )
    include(":weizhi", ":caps", ":agent-tools", ":agent-tools-webview", ":agent-tools-mcp")
    project(":weizhi").projectDir = root.resolve("weizhi")
    project(":caps").projectDir = root.resolve("caps")
    project(":agent-tools").projectDir = root.resolve("agent-tools")
    project(":agent-tools-webview").projectDir = root.resolve("agent-tools-webview")
    project(":agent-tools-mcp").projectDir = root.resolve("agent-tools-mcp")
}
