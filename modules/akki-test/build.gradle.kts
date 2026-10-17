plugins {
    id("akki.kotlin-multiplatform")
    id("akki.publishing")
    id("org.jetbrains.kotlinx.kover")
}

description = "Records what the code under test logs through akki"

kotlin {
    explicitApi()

    sourceSets {
        commonMain.dependencies {
            api(project(":akki-core"))
        }
    }
}
