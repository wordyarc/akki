import akki.buildlogic.jvmTargetVersion
import akki.buildlogic.jvmToolchainVersion
import akki.buildlogic.library
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget

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
