import org.gradle.external.javadoc.StandardJavadocDocletOptions

plugins {
    `java-library`
    `maven-publish`
}

group = "com.agent1"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

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
        java.exclude("com/agent1/javaagent/examples/**")
    }
}

tasks.test {
    useJUnitPlatform()
}

// javadocJar：关闭 doclint「missing」，其余检查保留。
tasks.withType<Javadoc>().configureEach {
    val opts = options as StandardJavadocDocletOptions
    opts.encoding = "UTF-8"
    opts.charSet = "UTF-8"
    opts.addStringOption("Xdoclint:all,-missing", "-quiet")
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
