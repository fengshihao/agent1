plugins {
    `java-library`
}

group = "com.agent1"
version = "0.1.0-SNAPSHOT"

val weizhiRepo: java.io.File =
    providers.gradleProperty("weizhiRepo")
        .map { file(it) }
        .orElse(file("../../../weizhi"))
        .get()

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    api(project(":core"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
}

sourceSets {
    named("main") {
        java {
            srcDir(weizhiRepo.resolve("java"))
            srcDir("src/main/java")
        }
    }
}

tasks.test {
    useJUnitPlatform()
    val nativeDir = weizhiRepo.resolve("build")
    jvmArgs("-Djava.library.path=${nativeDir.absolutePath}")
    environment("AGENT1_WEIZHI_REPO", weizhiRepo.absolutePath)
}
