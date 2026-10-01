import akki.buildlogic.checkLatestCompilerApi
import akki.buildlogic.compileAgainstCompilerApi
import akki.buildlogic.compilerAdapters
import akki.buildlogic.compilerApiBaseline

plugins {
    id("akki.kotlin-jvm")
    id("org.jetbrains.kotlinx.kover")
}

description = "Contract between the akki compiler plugin and its adapters to the compiler API of each Kotlin release"

compileAgainstCompilerApi(compilerApiBaseline)
checkLatestCompilerApi()

java {
    withSourcesJar()
}

kotlin {
    explicitApi()
}

dependencies {
    testImplementation("org.jetbrains.kotlin:kotlin-compiler:$compilerApiBaseline")
    testImplementation(libs.kotlin.tooling.core)
}

tasks.test {
    systemProperty("akki.compiler.minimums", compilerAdapters.joinToString(",") { it.minVersion })
}
