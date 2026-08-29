plugins {
    id("lokki.kotlin-multiplatform")
}

kotlin {
    explicitApi()

    jvm()

    sourceSets {
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
