rootProject.name = "java_agent"
include(":core")
include(":cli")
include(":weizhi-bridge")
project(":core").projectDir = file("../agent_core")
