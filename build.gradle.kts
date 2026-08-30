plugins {
    base
}

group = providers.gradleProperty("akki.group").get()
version = providers.gradleProperty("akki.version").get()

tasks.register("apiCheck") {
    group = "verification"
    dependsOn(subprojects.map { "${it.path}:checkKotlinAbi" })
}
