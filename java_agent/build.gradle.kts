plugins {
    base
}

group = "com.agent1"
version = "0.1.0-SNAPSHOT"

subprojects {
    repositories {
        mavenCentral()
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
