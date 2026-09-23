plugins {
    base
    id("org.jetbrains.kotlinx.kover")
}

group = providers.gradleProperty("akki.group").get()
version = providers.gradleProperty("akki.version").get()

kover {
    reports {
        filters {
            excludes {
                packages("io.akki.compiler.test")
            }
        }
    }
}

dependencies {
    kover(project(":akki-core"))
    kover(project(":akki-slf4j"))
    kover(project(":akki-compiler"))
    kover(project(":akki-test"))
    kover(project(":akki-coroutines"))
    kover(project(":akki-test-coroutines"))
}

tasks.register("apiCheck") {
    group = "verification"
    dependsOn(provider { subprojects.filter { it.tasks.findByName("checkKotlinAbi") != null }.map { "${it.path}:checkKotlinAbi" } })
}
