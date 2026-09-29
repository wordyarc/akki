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
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        mavenCentral()
    }

    providers.gradleProperty("akki.kotlin").orNull?.let { kotlin ->
        versionCatalogs {
            create("libs") {
                version("kotlin", kotlin)
            }
        }
    }
}

rootProject.name = "akki"

listOf(
    "akki-core",
    "akki-slf4j",
    "akki-compiler",
    "akki-gradle",
    "akki-test",
    "akki-coroutines",
    "akki-test-coroutines",
    "akki-benchmark",
    "test-utils",
).forEach { module ->
    include(module)
    project(":$module").projectDir = file("modules/$module")
}
