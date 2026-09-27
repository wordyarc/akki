plugins {
    id("akki.kotlin-jvm")
}

description = "Shared JVM test utilities for akki modules"

dependencies {
    implementation(libs.kotlin.test)
}
