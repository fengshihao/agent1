pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

// 无本地 JDK 17 时由 Gradle 按 toolchain 自动拉取（与 :core/:cli 的 Java 17 一致）
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

rootProject.name = "java_agent"
include(":core")
include(":cli")
include(":weizhi-bridge")
project(":core").projectDir = file("../agent_core")
