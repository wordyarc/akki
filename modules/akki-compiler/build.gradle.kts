import akki.buildlogic.ClasspathSystemProperty

plugins {
    id("akki.kotlin-jvm")
}

val fixtureRuntime: Configuration = configurations.create("fixtureRuntime") {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    compileOnly(libs.kotlin.compiler)
    fixtureRuntime(project(":akki-core"))
    fixtureRuntime(project(":akki-slf4j"))
    fixtureRuntime(libs.logback.classic)
    testImplementation(project(":akki-core"))
    testImplementation(project(":akki-slf4j"))
    testImplementation(libs.logback.classic)
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
