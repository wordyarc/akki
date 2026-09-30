plugins {
    base
    id("org.jetbrains.kotlinx.kover")
}

kover {
    reports {
        filters {
            excludes {
                packages("io.akki.compiler.test")
            }
        }
    }
}

dependencies {
    kover(project(":akki-core"))
    kover(project(":akki-slf4j"))
    kover(project(":akki-compiler-compat"))
    kover(project(":akki-compiler"))
    kover(project(":akki-test"))
    kover(project(":akki-coroutines"))
    kover(project(":akki-test-coroutines"))
}
