import lokki.buildlogic.jvmVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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
    compilerOptions {
        jvmTarget = JvmTarget.fromTarget(jvmVersion.toString())
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
