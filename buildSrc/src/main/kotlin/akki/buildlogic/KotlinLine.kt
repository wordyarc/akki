package akki.buildlogic

import org.gradle.api.Project

val Project.kotlinLine: String
    get() = version("kotlin").split('.').take(2).joinToString(".")

val Project.compilerArtifactId: String
    get() = "akki-compiler-kotlin-$kotlinLine"
