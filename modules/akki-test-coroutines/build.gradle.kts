plugins {
    id("akki.kotlin-multiplatform")
    id("akki.publishing")
    id("org.jetbrains.kotlinx.kover")
}

description = "Suspending recordLogs that captures akki logs in coroutine tests"

kotlin {
    explicitApi()

    sourceSets {
        commonMain.dependencies {
            api(project(":akki-test"))
            api(libs.kotlinx.coroutines.core)
            implementation(project(":akki-coroutines"))
        }

        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
