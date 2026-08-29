plugins {
    id("lokki.kotlin-multiplatform")
}

kotlin {
    explicitApi()

    jvm()

    sourceSets {
        commonMain.dependencies {
            api(project(":lokki-core"))
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
