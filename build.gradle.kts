plugins {
    base
}

group = providers.gradleProperty("lokki.group").get()
version = providers.gradleProperty("lokki.version").get()

tasks.register("apiCheck") {
    group = "verification"
    dependsOn(subprojects.map { "${it.path}:checkKotlinAbi" })
}
