import akki.buildlogic.compileModuleDescriptor
import akki.buildlogic.jvmTargetVersion
import akki.buildlogic.jvmToolchainVersion
import akki.buildlogic.library
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("akki.base")
    id("org.jetbrains.kotlin.jvm")
    id("akki.testing")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(jvmToolchainVersion)
}

tasks.withType<JavaCompile>().configureEach {
    options.release = jvmTargetVersion
}

compileModuleDescriptor(tasks.named<JavaCompile>("compileJava"), tasks.named<KotlinCompile>("compileKotlin"))

kotlin {
    @OptIn(ExperimentalAbiValidation::class)
    abiValidation()

    compilerOptions {
        jvmTarget = JvmTarget.fromTarget(jvmTargetVersion.toString())
        allWarningsAsErrors = true
        freeCompilerArgs.addAll("-progressive", "-Xjdk-release=$jvmTargetVersion")
    }
}

dependencies {
    testImplementation(library("kotlin-test-junit5"))
    testImplementation(library("junit-jupiter"))
    testRuntimeOnly(library("junit-platform-launcher"))
}
