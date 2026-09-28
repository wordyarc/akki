package akki.buildlogic

import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.register
import org.gradle.language.base.plugins.LifecycleBasePlugin

internal fun Test.copyTestsFrom(source: Test) {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
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

fun Project.jvmClassTest(source: TaskProvider<Test>, configure: Test.() -> Unit = {}): TaskProvider<Test> {
    val jvmClassTest = tasks.register<Test>("jvmClassTest") {
        description = "Runs ${source.name} with JVM_CLASS logger names"
        copyTestsFrom(source.get())
        systemProperty("io.akki.loggerNameStyle", "jvm-class")
        configure()
    }
    tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME) { dependsOn(jvmClassTest) }
    return jvmClassTest
}
