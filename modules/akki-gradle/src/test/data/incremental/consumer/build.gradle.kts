plugins {
    application
    kotlin("jvm")
    @akkiPlugin@
}

dependencies {
    implementation(project(":library"))
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xrender-internal-diagnostic-names")
    }
}

application {
    mainClass.set("consumer.MainKt")
}
