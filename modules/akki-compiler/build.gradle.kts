import akki.buildlogic.ClasspathSystemProperty

plugins {
    id("akki.kotlin-jvm")
}

val fixtureRuntime: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    compileOnly(libs.kotlin.compiler)
    fixtureRuntime(project(":akki-core"))
    testImplementation(project(":akki-core"))
    testImplementation(libs.kotlin.compiler)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    jvmArgumentProviders.add(ClasspathSystemProperty("akki.compiler.plugin.jar", files(tasks.jar)))
    jvmArgumentProviders.add(ClasspathSystemProperty("akki.fixture.classpath", fixtureRuntime))
    systemProperty("akki.jvm.target", libs.versions.jvm.target.get())
}
