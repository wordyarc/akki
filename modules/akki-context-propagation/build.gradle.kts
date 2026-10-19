plugins {
    id("akki.kotlin-jvm")
    id("akki.publishing")
    id("org.jetbrains.kotlinx.kover")
}

description = "Carries the akki LogContext through Micrometer context-propagation: Reactor, Spring and wrapped executors"

kotlin {
    explicitApi()
}

dependencies {
    api(project(":akki-core"))
    compileOnly(libs.micrometer.context.propagation)
    testImplementation(libs.micrometer.context.propagation)
    testImplementation(libs.reactor.core)
}
