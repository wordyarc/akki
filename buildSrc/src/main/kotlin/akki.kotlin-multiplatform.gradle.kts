import akki.buildlogic.jvmTargetVersion
import akki.buildlogic.jvmToolchainVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget

plugins {
    id("akki.base")
    id("org.jetbrains.kotlin.multiplatform")
}

kotlin {
    @OptIn(ExperimentalAbiValidation::class)
    abiValidation()

    jvmToolchain(jvmToolchainVersion)

    targets.withType<KotlinJvmTarget>().configureEach {
        compilerOptions.jvmTarget = JvmTarget.fromTarget(jvmTargetVersion.toString())

        testRuns.configureEach {
            executionTask.configure {
                useJUnitPlatform()
            }
        }
    }
}
