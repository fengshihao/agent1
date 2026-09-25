pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.aliyun.com/repository/public")
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven(url = uri("../java_agent/build/local-maven"))
        google()
        mavenCentral()
        maven("https://maven.aliyun.com/repository/public")
    }
}

rootProject.name = "DynamicUiDemo"
include(":app")

val weizhiAndroidRoot = file("../../weizhi/android")
if (weizhiAndroidRoot.isDirectory) {
    include(":weizhi", ":caps", ":agent-tools", ":agent-tools-webview", ":agent-tools-mcp")
    project(":weizhi").projectDir = weizhiAndroidRoot.resolve("weizhi")
    project(":caps").projectDir = weizhiAndroidRoot.resolve("caps")
    project(":agent-tools").projectDir = weizhiAndroidRoot.resolve("agent-tools")
    project(":agent-tools-webview").projectDir = weizhiAndroidRoot.resolve("agent-tools-webview")
    project(":agent-tools-mcp").projectDir = weizhiAndroidRoot.resolve("agent-tools-mcp")
}
