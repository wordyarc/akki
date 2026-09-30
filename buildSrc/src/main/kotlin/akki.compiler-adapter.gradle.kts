import akki.buildlogic.COMPILER_COMPAT_PROJECT
import akki.buildlogic.GenerateCompilerCompatProvider
import akki.buildlogic.compileAgainstCompilerApi
import akki.buildlogic.compilerAdapter
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("akki.kotlin-jvm")
}

val adapter = compilerAdapter

description = "Adapter of the akki compiler plugin to the compiler API of Kotlin ${adapter.minVersion}"

compileAgainstCompilerApi(adapter.minVersion)

dependencies {
    api(project(COMPILER_COMPAT_PROJECT))
}

val generateCompilerCompatProvider = tasks.register<GenerateCompilerCompatProvider>("generateCompilerCompatProvider") {
    minVersion = adapter.minVersion
    implementationClassName = adapter.implementationClassName
    sourceDirectory = layout.buildDirectory.dir("generated/compat/kotlin")
    resourceDirectory = layout.buildDirectory.dir("generated/compat/resources")
}

java {
    withSourcesJar()
}

kotlin {
    explicitApi()
    sourceSets.main {
        kotlin.srcDir(generateCompilerCompatProvider.flatMap { it.sourceDirectory })
        resources.srcDir(generateCompilerCompatProvider.flatMap { it.resourceDirectory })
    }
}

tasks.named<KotlinCompile>("compileKotlin") {
    compilerOptions.moduleName = adapter.packageName
}
