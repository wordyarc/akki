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
    }
}
