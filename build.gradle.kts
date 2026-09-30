import akki.buildlogic.KoverMarkdownReport

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

tasks.register<KoverMarkdownReport>("koverMarkdownReport") {
    group = "verification"
    description = "Generates an aggregated Kover coverage summary in Markdown."
    xmlReport.fileProvider(tasks.named("koverXmlReport").map { it.outputs.files.singleFile })
    markdownReport.set(layout.buildDirectory.file("reports/kover/summary.md"))
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
