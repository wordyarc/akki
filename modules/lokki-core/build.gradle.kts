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

        jvmTest.dependencies {
            implementation(libs.kotlin.test.junit5)
            implementation(libs.junit.jupiter)
            runtimeOnly(libs.junit.platform.launcher)
        }
    }
}
