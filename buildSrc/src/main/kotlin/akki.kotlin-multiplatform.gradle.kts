import akki.buildlogic.compileModuleDescriptor
import akki.buildlogic.jvmTargetVersion
import akki.buildlogic.jvmToolchainVersion
import akki.buildlogic.library
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("akki.base")
    id("org.jetbrains.kotlin.multiplatform")
    id("akki.testing")
}

kotlin {
    @OptIn(ExperimentalAbiValidation::class)
    abiValidation()

    jvmToolchain(jvmToolchainVersion)

    compilerOptions {
        allWarningsAsErrors = true
        freeCompilerArgs.addAll("-progressive", "-Xexpect-actual-classes")
    }

    targets.withType<KotlinJvmTarget>().configureEach {
        compilerOptions {
            jvmTarget = JvmTarget.fromTarget(jvmTargetVersion.toString())
            freeCompilerArgs.add("-Xjdk-release=$jvmTargetVersion")
        }
    }

    sourceSets {
        commonTest.dependencies {
            implementation(library("kotlin-test"))
        }
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = jvmTargetVersion
}

afterEvaluate {
    if ("compileJvmMainJava" in tasks.names) {
        compileModuleDescriptor(tasks.named<JavaCompile>("compileJvmMainJava"), tasks.named<KotlinCompile>("compileKotlinJvm"))
    }
}
