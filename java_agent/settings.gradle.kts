rootProject.name = "java_agent"
include(":core")
include(":cli")
project(":core").projectDir = file("../agent_core")
