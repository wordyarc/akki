plugins {
    base
}

group = providers.gradleProperty("akki.group").get()
version = providers.gradleProperty("akki.version").get()

tasks.register("apiCheck") {
    group = "verification"
    dependsOn(provider { subprojects.filter { it.tasks.findByName("checkKotlinAbi") != null }.map { "${it.path}:checkKotlinAbi" } })
}
