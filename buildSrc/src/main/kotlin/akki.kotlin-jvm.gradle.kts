import akki.buildlogic.compileModuleDescriptor
import akki.buildlogic.jvmModuleName
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

tasks.named<Javadoc>("javadoc") {
    exclude("module-info.java")
}

compileModuleDescriptor(tasks.named<JavaCompile>("compileJava"), tasks.named<KotlinCompile>("compileKotlin"))

tasks.named<KotlinCompile>("compileKotlin") {
    compilerOptions.moduleName = jvmModuleName
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.fromTarget(jvmTargetVersion.toString())
        allWarningsAsErrors = true
        freeCompilerArgs.addAll("-progressive", "-Xjdk-release=$jvmTargetVersion")
    }
}

plugins.withId("akki.publishing") {
    kotlin {
        @OptIn(ExperimentalAbiValidation::class)
        abiValidation()
    }
}

dependencies {
    testImplementation(library("kotlin-test-junit5"))
    testImplementation(library("junit-jupiter"))
    testRuntimeOnly(library("junit-platform-launcher"))
}
