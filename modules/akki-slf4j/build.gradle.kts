plugins {
    id("akki.kotlin-jvm")
}

kotlin {
    explicitApi()
}

dependencies {
    api(project(":akki-core"))
    implementation(libs.slf4j.api)
    testImplementation(libs.logback.classic)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
