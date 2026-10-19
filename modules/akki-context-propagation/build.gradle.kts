plugins {
    id("akki.kotlin-jvm")
    id("akki.publishing")
    id("org.jetbrains.kotlinx.kover")
}

description = "Carries the akki LogContext through Micrometer context-propagation to Reactor and Spring"

kotlin {
    explicitApi()
}

dependencies {
    api(project(":akki-core"))
    compileOnly(libs.micrometer.context.propagation)
    testImplementation(libs.micrometer.context.propagation)
    testImplementation(libs.reactor.core)
}
