plugins {
    id("akki.kotlin-multiplatform")
    id("akki.publishing")
    id("org.jetbrains.kotlinx.kover")
}

description = "Carries an akki LogScope with a coroutine across dispatchers"

kotlin {
    explicitApi()

    jvm()

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
