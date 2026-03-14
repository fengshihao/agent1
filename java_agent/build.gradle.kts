plugins {
    `java-library`
    application
}

group = "com.agent1"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
    withSourcesJar()
    withJavadocJar()
}

repositories {
    mavenCentral()
}

dependencies {
    api("com.squareup.okhttp3:okhttp:4.12.0")
    api("com.squareup.okhttp3:okhttp-sse:4.12.0")
    api("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    api("io.reactivex.rxjava3:rxjava:3.1.9")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("com.agent1.javaagent.cli.JavaAgentCli")
}

tasks.register<JavaExec>("runJavaAgentCli") {
    group = "application"
    description = "Run Java Agent interactive CLI"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.agent1.javaagent.cli.JavaAgentCli")
    standardInput = System.`in`
}

tasks.register<Jar>("fatJar") {
    group = "build"
    description = "Build standalone executable fat jar"
    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = "com.agent1.javaagent.cli.JavaAgentCli"
    }
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith(".jar") }
            .map { zipTree(it) }
    })
}
