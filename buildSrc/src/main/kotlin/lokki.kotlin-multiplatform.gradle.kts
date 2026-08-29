import lokki.buildlogic.jvmVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget

plugins {
    id("lokki.base")
    id("org.jetbrains.kotlin.multiplatform")
}

kotlin {
    jvmToolchain(jvmVersion)

    targets.withType<KotlinJvmTarget>().configureEach {
        compilerOptions.jvmTarget = JvmTarget.fromTarget(jvmVersion.toString())
    }
}
