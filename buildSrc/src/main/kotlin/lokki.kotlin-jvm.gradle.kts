import lokki.buildlogic.jvmVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    id("lokki.base")
    id("org.jetbrains.kotlin.jvm")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(jvmVersion)
    sourceCompatibility = JavaVersion.toVersion(jvmVersion)
    targetCompatibility = JavaVersion.toVersion(jvmVersion)
}

kotlin {
    @OptIn(ExperimentalAbiValidation::class)
    abiValidation()

    compilerOptions {
        jvmTarget = JvmTarget.fromTarget(jvmVersion.toString())
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
