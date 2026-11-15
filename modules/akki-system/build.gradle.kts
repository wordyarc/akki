plugins {
    id("akki.kotlin-jvm")
    id("akki.publishing")
    id("org.jetbrains.kotlinx.kover")
}

description = "System.Logger backend for akki"

kotlin {
    explicitApi()
}

dependencies {
    api(project(":akki-core"))
    testImplementation(project(":test-utils"))
}
