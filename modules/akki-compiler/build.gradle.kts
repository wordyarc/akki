import akki.buildlogic.ClasspathSystemProperty
import akki.buildlogic.WriteVersionConstant

plugins {
    id("akki.kotlin-jvm")
}

val writeVersionConstant = tasks.register<WriteVersionConstant>("writeVersionConstant") {
    packageName = "io.akki.compiler"
    version = project.version.toString()
    outputDirectory = layout.buildDirectory.dir("generated/source/version")
}

kotlin {
    sourceSets.main {
        kotlin.srcDir(writeVersionConstant)
    }
}

val fixtureRuntime: Configuration = configurations.create("fixtureRuntime") {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    compileOnly(libs.kotlin.compiler)
    fixtureRuntime(project(":akki-core"))
    fixtureRuntime(project(":akki-slf4j"))
    fixtureRuntime(project(":akki-test"))
    fixtureRuntime(libs.logback.classic)
    testImplementation(project(":akki-core"))
    testImplementation(project(":akki-slf4j"))
    testImplementation(project(":akki-test"))
    testImplementation(libs.logback.classic)
    testImplementation(libs.kotlin.compiler)
}

sourceSets.test {
    resources.srcDir("src/test/data")
}

tasks.test {
    jvmArgumentProviders.add(ClasspathSystemProperty("akki.compiler.plugin.jar", files(tasks.jar)))
    jvmArgumentProviders.add(ClasspathSystemProperty("akki.fixture.classpath", fixtureRuntime))
    systemProperty("akki.jvm.target", libs.versions.jvm.target.get())
}
