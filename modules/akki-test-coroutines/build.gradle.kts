plugins {
    id("akki.kotlin-multiplatform")
    id("akki.publishing")
    id("org.jetbrains.kotlinx.kover")
}

kotlin {
    explicitApi()

    jvm()

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
