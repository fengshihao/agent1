plugins {
    `java-library`
    `maven-publish`
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
    withSourcesJar()
}

dependencies {
    api(project(":core"))
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

sourceSets {
    named("main") {
        java {
            srcDir(weizhiRepo.resolve("java"))
            srcDir("src/shared/java")
        }
    }
}

tasks.test {
    useJUnitPlatform()
    val nativeDir = weizhiRepo.resolve("build")
    jvmArgs("-Djava.library.path=${nativeDir.absolutePath}")
    environment("AGENT1_WEIZHI_REPO", weizhiRepo.absolutePath)
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = "weizhi-bridge"
            pom {
                name.set("weizhi-bridge")
                description.set(
                    "Agent1 Weizhi 脚本桥：WeizhiScriptEngine、Package 安装、桌面 webview_exec。" +
                        "JAR 不含 libweizhijni，运行时需 weizhi native。",
                )
            }
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
