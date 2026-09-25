// PMD + SpotBugs（JavaExec CLI），挂到 check。规则：仓库根 config/*.xml
// 见 doc/代码质量硬性要求与静态检测.md

import org.gradle.api.tasks.SourceSetContainer

val repoConfigDir = rootProject.projectDir.parentFile.resolve("config")
val mainSourceSet = extensions.getByType<SourceSetContainer>().named("main")
val pmdRuleset = repoConfigDir.resolve("pmd-ruleset.xml")
val spotbugsExclude = repoConfigDir.resolve("spotbugs-exclude.xml")

configurations {
    create("pmdTool")
    create("spotbugsTool")
}

dependencies {
    add("pmdTool", "net.sourceforge.pmd:pmd-java:6.55.0")
    add("spotbugsTool", "com.github.spotbugs:spotbugs:4.8.3")
}

tasks.register<JavaExec>("pmdMain") {
    group = "verification"
    description = "PMD static analysis for main Java sources"
    mainClass.set("net.sourceforge.pmd.PMD")
    classpath = configurations["pmdTool"]
    val reportFile = layout.buildDirectory.file("reports/pmd/pmdMain.txt")
    doFirst {
        val report = reportFile.get().asFile
        report.parentFile.mkdirs()
        standardOutput = report.outputStream()
        args(
            "-rulesets", pmdRuleset.absolutePath,
            "-dir", "src/main/java",
            "-format", "text",
            "-language", "java",
            "-cache", layout.buildDirectory.file("pmd-cache").get().asFile.absolutePath,
        )
    }
    isIgnoreExitValue = true
    doLast {
        val report = reportFile.get().asFile
        if (executionResult.get().exitValue != 0) {
            val lines = report.readLines().filter { it.startsWith("src/") || it.startsWith("src\\") }
            logger.lifecycle("${lines.size} PMD violations in ${project.name}:")
            lines.forEach { logger.lifecycle("  $it") }
            throw GradleException(
                "PMD found violations, see ${report}（豁免须 // NOPMD + 理由）",
            )
        }
    }
}

tasks.register<JavaExec>("spotbugsMain") {
    group = "verification"
    description = "SpotBugs static analysis for main classes"
    dependsOn(tasks.named("compileJava"))
    mainClass.set("edu.umd.cs.findbugs.FindBugs2")
    classpath = configurations["spotbugsTool"]
    val reportXml = layout.buildDirectory.file("reports/spotbugs/spotbugsMain.xml")
    val classesDir = mainSourceSet.get().output.classesDirs.singleFile
    doFirst {
        reportXml.get().asFile.parentFile.mkdirs()
        args(
            "-xml:withMessages",
            "-effort:max",
            "-medium",
            "-exclude", spotbugsExclude.absolutePath,
            "-output", reportXml.get().asFile.absolutePath,
            "-auxclasspath", mainSourceSet.get().runtimeClasspath.asPath,
            classesDir.absolutePath,
        )
    }
    isIgnoreExitValue = true
    doLast {
        val xml = reportXml.get().asFile
        if (!xml.exists()) {
            throw GradleException("SpotBugs did not produce a report")
        }
        if (xml.readText().contains("<BugInstance")) {
            throw GradleException(
                "SpotBugs found issues, see ${xml}（豁免须在 config/spotbugs-exclude.xml 写理由）",
            )
        }
    }
}

tasks.named("check") {
    dependsOn("pmdMain", "spotbugsMain")
}
