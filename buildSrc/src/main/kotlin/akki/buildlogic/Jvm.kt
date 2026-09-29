package akki.buildlogic

import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions

private val Project.jvmToolchainVersion: Int
    get() = version("jvm-toolchain").toInt()

internal val Project.jvmTargetVersion: Int
    get() = version("jvm-target").toInt()

fun Project.targetJvm(compilerOptions: KotlinJvmCompilerOptions) {
    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(jvmToolchainVersion))
    }
    tasks.withType<JavaCompile>().configureEach {
        options.release.set(jvmTargetVersion)
    }
    compilerOptions.jvmTarget.set(JvmTarget.fromTarget(jvmTargetVersion.toString()))
    compilerOptions.freeCompilerArgs.add("-Xjdk-release=$jvmTargetVersion")
}
