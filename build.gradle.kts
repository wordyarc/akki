plugins {
    base
    id("org.jetbrains.kotlinx.kover")
}

group = providers.gradleProperty("akki.group").get()
version = providers.gradleProperty("akki.version").get()

dependencies {
    kover(project(":akki-core"))
    kover(project(":akki-slf4j"))
    kover(project(":akki-compiler"))
    kover(project(":akki-test"))
}

tasks.register("apiCheck") {
    group = "verification"
    dependsOn(provider { subprojects.filter { it.tasks.findByName("checkKotlinAbi") != null }.map { "${it.path}:checkKotlinAbi" } })
}
