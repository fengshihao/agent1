pluginManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/public")
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven(url = uri("../java_agent/build/local-maven"))
        maven("https://maven.aliyun.com/repository/public")
        google()
        mavenCentral()
    }
}

rootProject.name = "DynamicUiDemo"
include(":app")

val weizhiAndroidRoot = file("../../weizhi/android")
if (weizhiAndroidRoot.isDirectory) {
    include(":weizhi", ":caps")
    project(":weizhi").projectDir = weizhiAndroidRoot.resolve("weizhi")
    project(":caps").projectDir = weizhiAndroidRoot.resolve("caps")
}
