plugins {
    `java-library`
    `maven-publish`
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

dependencies {
    api("com.squareup.okhttp3:okhttp:4.12.0")
    api("com.squareup.okhttp3:okhttp-sse:4.12.0")
    api("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    api("io.reactivex.rxjava3:rxjava:3.1.9")
    api("org.xerial:sqlite-jdbc:3.51.3.0")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

sourceSets {
    named("main") {
        java.srcDir("../src/main/java")
        java.exclude("com/agent1/javaagent/cli/**")
        java.exclude("com/agent1/javaagent/examples/**")
    }
    named("test") {
        java.srcDir("../src/test/java")
        java.exclude("com/agent1/javaagent/cli/**")
    }
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = "java-agent-core"
        }
    }
    repositories {
        maven {
            name = "localRepo"
            url = rootProject.layout.buildDirectory.dir("local-maven").get().asFile.toURI()
        }
        mavenLocal()
    }
}
