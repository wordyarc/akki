plugins {
    id("akki.kotlin-multiplatform")
}

kotlin {
    explicitApi()

    jvm()

    sourceSets {
        commonMain.dependencies {
            api(project(":akki-core"))
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
