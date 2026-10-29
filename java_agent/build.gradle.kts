import org.gradle.api.tasks.testing.Test

plugins {
    base
}

group = "com.agent1"
version = "0.1.0-SNAPSHOT"

fun applyLocalCdnEnv(task: Test) {
    val file = rootProject.projectDir.resolve("../artifact-cdn/cdn.local.env")
    if (!file.isFile) {
        return
    }
    file.readLines().forEach { raw ->
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith("#")) {
            return@forEach
        }
        val idx = line.indexOf('=')
        if (idx <= 0) {
            return@forEach
        }
        val key = line.substring(0, idx).trim()
        var value = line.substring(idx + 1).trim()
        if ((value.startsWith("\"") && value.endsWith("\""))
            || (value.startsWith("'") && value.endsWith("'"))
        ) {
            value = value.substring(1, value.length - 1)
        }
        if (key.isNotEmpty() && task.environment[key] == null && System.getenv(key) == null) {
            task.environment(key, value)
        }
    }
}

subprojects {
    repositories {
        mavenCentral()
    }
    plugins.withId("java") {
        apply(from = rootProject.file("static-analysis.gradle.kts"))
        tasks.withType<Test>().configureEach {
            applyLocalCdnEnv(this)
        }
    }
}

tasks.register("fatJar") {
    group = "build"
    description = "Build standalone executable fat jar"
    dependsOn(":cli:fatJar")
}

tasks.register("runJavaAgentCli") {
    group = "application"
    description = "Run Java Agent interactive CLI"
    dependsOn(":cli:runJavaAgentCli")
}

tasks.register("publishCoreToLocalRepo") {
    group = "publishing"
    description = "Publish java-agent-core to local project maven repo"
    dependsOn(":core:publishMavenJavaPublicationToLocalRepoRepository")
}

tasks.register("publishWeizhiBridgeToLocalRepo") {
    group = "publishing"
    description = "Publish weizhi-bridge and java-agent-core to local-maven + mavenLocal"
    dependsOn(
        ":core:publishMavenJavaPublicationToLocalRepoRepository",
        ":core:publishToMavenLocal",
        ":weizhi-bridge:publishMavenJavaPublicationToLocalRepoRepository",
        ":weizhi-bridge:publishToMavenLocal",
    )
}
