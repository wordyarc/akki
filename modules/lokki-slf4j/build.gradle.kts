plugins {
    id("lokki.kotlin-jvm")
}

kotlin {
    explicitApi()
}

dependencies {
    api(project(":lokki-core"))
    implementation(libs.slf4j.api)
}
