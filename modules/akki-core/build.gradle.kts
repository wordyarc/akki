import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    id("akki.kotlin-multiplatform")
    alias(libs.plugins.kotlin.power.assert)
}

kotlin {
    explicitApi()

    jvm {
        testRuns.configureEach {
            executionTask.configure {
                systemProperty("dev.ashenarx.akki.loggerNameStyle", "source")
            }
        }
    }

    sourceSets {
        jvmMain.dependencies {
            implementation(libs.kotlin.metadata.jvm)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }

        jvmTest.dependencies {
            implementation(libs.kotlin.test.junit5)
            implementation(libs.junit.jupiter)
            implementation(libs.slf4j.api)
            runtimeOnly(libs.junit.platform.launcher)
            runtimeOnly(libs.slf4j.simple)
        }
    }
}

val jvmTest = tasks.named<Test>("jvmTest")
val jvmClassTest = tasks.register<Test>("jvmClassTest") {
    group = "verification"
    description = "Runs JVM tests with JVM_CLASS logger names"
    testClassesDirs = jvmTest.get().testClassesDirs
    classpath = jvmTest.get().classpath
    systemProperty("dev.ashenarx.akki.loggerNameStyle", "jvm-class")
    shouldRunAfter(jvmTest)
    useJUnitPlatform()
}

tasks.named("check") {
    dependsOn(jvmClassTest)
}

@OptIn(ExperimentalKotlinGradlePluginApi::class)
powerAssert {
    functions = listOf(
        "kotlin.assert",
        "kotlin.test.assertEquals",
        "kotlin.test.assertSame",
        "kotlin.test.assertTrue",
    )
}
