pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "lokki"

listOf(
    "lokki-core",
    "lokki-slf4j",
    "lokki-compiler",
    "lokki-gradle",
    "lokki-test",
).forEach { module ->
    include(module)
    project(":$module").projectDir = file("modules/$module")
}
