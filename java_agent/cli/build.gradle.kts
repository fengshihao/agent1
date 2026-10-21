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

configurations {
    register("uiFatRuntime") {
        extendsFrom(configurations.runtimeClasspath.get())
        exclude(group = "org.xerial", module = "sqlite-jdbc")
        exclude(group = "org.jline", module = "jline")
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":weizhi-bridge"))
    implementation("org.jline:jline:3.26.3")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
}

/** 与 scripts/package-desktop-ui.sh 中 detect_sqlite_classifier 一致。 */
fun sqliteNativeKeepPrefixes(classifier: String): List<String> = when (classifier) {
    "macosx-universal" -> listOf(
        "org/sqlite/native/Mac/aarch64/",
        "org/sqlite/native/Mac/x86_64/",
    )
    "macosx-aarch64" -> listOf("org/sqlite/native/Mac/aarch64/")
    "macosx-x86_64" -> listOf("org/sqlite/native/Mac/x86_64/")
    "linux-x86_64" -> listOf("org/sqlite/native/Linux/x86_64/")
    "linux-aarch_64" -> listOf("org/sqlite/native/Linux/aarch64/")
    "win-x86_64" -> listOf("org/sqlite/native/Windows/x86_64/")
    else -> error(
        "Unknown agent1.sqliteClassifier=$classifier " +
            "(use macosx-universal, macosx-aarch64, macosx-x86_64, linux-x86_64, linux-aarch_64, win-x86_64)"
    )
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

/** 仅 Web UI：无 JLine；配合 -Pagent1.sqliteClassifier= 只打当前平台 SQLite native，缩小 zip。 */
tasks.register<Jar>("uiFatJar") {
    group = "build"
    description =
        "Desktop Web UI fat jar (Main-Class DesktopUiLauncher). Set -Pagent1.sqliteClassifier=macosx-universal etc."
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = "com.agent1.javaagent.cli.DesktopUiLauncher"
    }
    val sqliteClassifierProp = (findProperty("agent1.sqliteClassifier") as String?)
        ?: System.getenv("AGENT1_SQLITE_CLASSIFIER")
    val classifierSlug = if (sqliteClassifierProp.isNullOrBlank()) "full" else sqliteClassifierProp
    archiveClassifier.set("ui-$classifierSlug")
    inputs.property("agent1.sqliteClassifier", classifierSlug)

    from(sourceSets.main.get().output)
    from({
        configurations.named("uiFatRuntime").get()
            .filter { it.name.endsWith(".jar") }
            .map { zipTree(it) }
    })
    from({
        val sqliteJars = configurations.runtimeClasspath.get()
            .filter { it.name.contains("sqlite-jdbc") && it.name.endsWith(".jar") }
        if (sqliteClassifierProp.isNullOrBlank()) {
            sqliteJars.map { zipTree(it) }
        } else {
            val keepPrefixes = sqliteNativeKeepPrefixes(sqliteClassifierProp)
            sqliteJars.map { jar ->
                zipTree(jar).matching {
                    exclude { details ->
                        val path = details.relativePath.pathString
                        path.startsWith("org/sqlite/native/") &&
                            keepPrefixes.none { path.startsWith(it) }
                    }
                }
            }
        }
    })
}
