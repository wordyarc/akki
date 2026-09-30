import akki.buildlogic.compileAgainstCompilerApi
import akki.buildlogic.compilerApiBaseline

plugins {
    id("akki.kotlin-jvm")
    id("org.jetbrains.kotlinx.kover")
}

description = "Contract between the akki compiler plugin and its adapters to the compiler API of each Kotlin release"

compileAgainstCompilerApi(compilerApiBaseline)

java {
    withSourcesJar()
}

kotlin {
    explicitApi()
}

dependencies {
    testImplementation("org.jetbrains.kotlin:kotlin-compiler:$compilerApiBaseline")
}
