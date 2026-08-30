plugins {
    id("akki.kotlin-jvm")
}

kotlin {
    explicitApi()
}

dependencies {
    compileOnly(libs.kotlin.compiler)
}
