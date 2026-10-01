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
    "akki-compiler-compat",
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

providers.fileContents(layout.rootDirectory.file("modules/akki-compiler-compat/adapters.properties")).asText.get()
    .lineSequence()
    .map { it.substringBefore('=').trim() }
    .filterNot { it.isEmpty() || it.startsWith('#') }
    .map { it.substringBefore('-') }
    .forEach { release ->
        include(":akki-compiler-compat:kotlin-$release")
        project(":akki-compiler-compat:kotlin-$release").projectDir =
            file("modules/akki-compiler-compat/kotlin-$release")
    }
