import akki.buildlogic.jvmTargetVersion
import akki.buildlogic.jvmToolchainVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    id("akki.base")
    id("org.jetbrains.kotlin.jvm")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(jvmToolchainVersion)
    sourceCompatibility = JavaVersion.toVersion(jvmTargetVersion)
    targetCompatibility = JavaVersion.toVersion(jvmTargetVersion)
}

kotlin {
    @OptIn(ExperimentalAbiValidation::class)
    abiValidation()

    compilerOptions {
        jvmTarget = JvmTarget.fromTarget(jvmTargetVersion.toString())
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
