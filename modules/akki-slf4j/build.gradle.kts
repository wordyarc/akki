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
}
