plugins {
    application
}

group = "com.agent1"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    implementation(project(":core"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
}

sourceSets {
    named("main") {
        java.srcDir("../src/main/java")
        java.include("com/agent1/javaagent/cli/**")
    }
    named("test") {
        java.srcDir("../src/test/java")
        java.include("com/agent1/javaagent/cli/**")
    }
}

application {
    mainClass.set("com.agent1.javaagent.cli.JavaAgentCli")
}

tasks.test {
    useJUnitPlatform()
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
