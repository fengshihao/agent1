plugins {
    `java-library`
}

group = "com.agent1"
version = "0.1.0-SNAPSHOT"

val weizhiRepo: java.io.File =
    providers.gradleProperty("weizhiRepo")
        .map { file(it) }
        .orElse(
            sequenceOf(file("../../../weizhi"), file("../../weizhi"))
                .firstOrNull { it.isDirectory }
                ?: file("../../../weizhi"),
        )
        .get()

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    api(project(":core"))
    implementation("com.google.code.gson:gson:2.10.1")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
}

sourceSets {
    named("main") {
        java {
            srcDir(weizhiRepo.resolve("java"))
            srcDir(weizhiRepo.resolve("android/agent-tools/src/main/java"))
            srcDir("src/shared/java")
            srcDir("src/main/java")
            exclude("**/AgentToolsBundle.java")
            exclude("**/AssetSkillRepository.java")
        }
    }
}

tasks.test {
    useJUnitPlatform()
    val nativeDir = weizhiRepo.resolve("build")
    jvmArgs("-Djava.library.path=${nativeDir.absolutePath}")
    environment("AGENT1_WEIZHI_REPO", weizhiRepo.absolutePath)
}
