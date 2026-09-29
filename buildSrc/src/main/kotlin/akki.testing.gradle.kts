import akki.buildlogic.copyTestsFrom
import akki.buildlogic.jvmTargetVersion
import akki.buildlogic.lifecycleOutsideCheck
import akki.buildlogic.onTargetJdk
import org.gradle.jvm.toolchain.JavaToolchainService
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    id("org.jetbrains.kotlin.plugin.power-assert")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

afterEvaluate {
    tasks.withType<Test>().toList().forEach { source ->
        val lifecycle = source.lifecycleOutsideCheck
        val onTarget = tasks.register<Test>(onTargetJdk(source.name)) {
            description = "Runs ${source.name} on JDK $jvmTargetVersion, the oldest runtime the library supports"
            javaLauncher = project.extensions.getByType<JavaToolchainService>().launcherFor {
                languageVersion = JavaLanguageVersion.of(jvmTargetVersion)
            }
            copyTestsFrom(source)
            if (lifecycle != null) mustRunAfter(source)
        }
        tasks.named(lifecycle ?: "check") { dependsOn(onTarget) }
    }
}

@OptIn(ExperimentalKotlinGradlePluginApi::class)
powerAssert {
    functions = listOf(
        "kotlin.assert",
        "kotlin.test.assertContains",
        "kotlin.test.assertEquals",
        "kotlin.test.assertFalse",
        "kotlin.test.assertNotEquals",
        "kotlin.test.assertSame",
        "kotlin.test.assertTrue",
    )
}
