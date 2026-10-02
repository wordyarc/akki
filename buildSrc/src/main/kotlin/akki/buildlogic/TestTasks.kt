package akki.buildlogic

import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.extra
import org.gradle.kotlin.dsl.register
import org.gradle.language.base.plugins.LifecycleBasePlugin

private const val LIFECYCLE: String = "akki.lifecycle"

private const val LOGGER_NAME_STYLE: String = "io.akki.loggerNameStyle"

fun Project.onTargetJdk(test: String): String = "${test}OnJdk$jvmTargetVersion"

fun Test.outsideCheck(lifecycle: TaskProvider<*>) {
    extra[LIFECYCLE] = lifecycle.name
}

internal val Test.lifecycleOutsideCheck: String?
    get() = extra.properties[LIFECYCLE] as String?

internal fun Test.copyTestsFrom(source: Test) {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs = source.testClassesDirs
    classpath = source.classpath
    jvmArgumentProviders.addAll(source.jvmArgumentProviders.filterIsInstance<ClasspathSystemProperty>())
    systemProperties(source.systemProperties)
    filter {
        source.filter.includePatterns.forEach { includeTestsMatching(it) }
        source.filter.excludePatterns.forEach { excludeTestsMatching(it) }
    }
    shouldRunAfter(source)
}

fun Project.jvmClassTest(source: TaskProvider<Test>, configure: Test.() -> Unit = {}): TaskProvider<Test> {
    source.configure { systemProperty(LOGGER_NAME_STYLE, "source") }
    val jvmClassTest = tasks.register<Test>("jvmClassTest") {
        description = "Runs ${source.name} with JVM_CLASS logger names"
        copyTestsFrom(source.get())
        systemProperty(LOGGER_NAME_STYLE, "jvm-class")
        configure()
    }
    tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME) { dependsOn(jvmClassTest) }
    return jvmClassTest
}
