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
    implementation(project(":weizhi-bridge"))
    implementation("org.jline:jline:3.26.3")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
}

application {
    mainClass.set("com.agent1.javaagent.cli.JavaAgentCli")
}

val webUiDir = rootProject.projectDir.resolve("web_ui")
val skipWebUi = System.getenv("AGENT1_SKIP_WEB_UI") == "1"

/** 可选：本地改前端后刷新 classpath 资源。CI 使用已提交的 web-ui/ 产物，默认跳过。 */
tasks.register<Exec>("buildWebUi") {
    group = "build"
    description = "npm build Web UI → cli/src/main/resources/web-ui（AGENT1_SKIP_WEB_UI=1 跳过）"
    workingDir = webUiDir
    onlyIf { !skipWebUi && webUiDir.resolve("package.json").isFile }
    commandLine(
        "bash",
        "-lc",
        """
        set -euo pipefail
        if [[ ! -d node_modules ]]; then
          if [[ -f package-lock.json ]]; then npm ci; else npm install; fi
        fi
        npm run build
        """.trimIndent()
    )
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
