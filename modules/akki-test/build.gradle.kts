plugins {
    id("akki.kotlin-multiplatform")
    id("akki.publishing")
    id("org.jetbrains.kotlinx.kover")
}

description = "Log capture for tests of code that logs with akki"

kotlin {
    explicitApi()

    jvm()

    sourceSets {
        commonMain.dependencies {
            api(project(":akki-core"))
        }
    }
}
