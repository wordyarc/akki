plugins {
    id("akki.kotlin-multiplatform")
    id("akki.publishing")
    id("org.jetbrains.kotlinx.kover")
}

description = "Keeps an akki LogScope attached to a coroutine as it moves between dispatchers"

kotlin {
    explicitApi()

    sourceSets {
        commonMain.dependencies {
            api(project(":akki-core"))
            api(libs.kotlinx.coroutines.core)
        }

        commonTest.dependencies {
            implementation(project(":akki-test"))
        }
    }
}
