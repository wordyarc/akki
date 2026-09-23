import akki.buildlogic.ClasspathSystemProperty
import akki.buildlogic.jvmTargetVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    id("org.jetbrains.kotlin.plugin.power-assert")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

val onTargetJdk = "OnJdk$jvmTargetVersion"

afterEvaluate {
    tasks.withType<Test>().names.filter { !it.endsWith(onTargetJdk) }.forEach { name ->
        val onTarget = tasks.register<Test>(name + onTargetJdk) {
            val source = tasks.named<Test>(name).get()
            group = "verification"
            description = "Runs $name on JDK $jvmTargetVersion, the oldest runtime the library supports"
            javaLauncher = project.extensions.getByType<JavaToolchainService>().launcherFor {
                languageVersion = JavaLanguageVersion.of(jvmTargetVersion)
            }
            testClassesDirs = source.testClassesDirs
            classpath = source.classpath
            jvmArgumentProviders.addAll(source.jvmArgumentProviders.filterIsInstance<ClasspathSystemProperty>())
            systemProperties(source.systemProperties)
            filter {
                setIncludePatterns(*source.filter.includePatterns.toTypedArray())
                setExcludePatterns(*source.filter.excludePatterns.toTypedArray())
            }
            shouldRunAfter(source)
        }
        tasks.named("check") { dependsOn(onTarget) }
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
