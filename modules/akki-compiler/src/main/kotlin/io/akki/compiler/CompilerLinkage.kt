package io.akki.compiler

import io.akki.compiler.compat.CompatLoadException
import org.jetbrains.kotlin.config.KotlinCompilerVersion

internal const val PLUGIN: String = "akki: compiler plugin $AKKI_VERSION"

internal fun unlinkedCompiler(): String =
    "Kotlin ${KotlinCompilerVersion.getVersion()} is not supported: the compiler plugin does not link against it. " +
        "Kotlin releases up to $LATEST_TESTED_KOTLIN are tested."

internal inline fun <T> linked(block: () -> T): T = try {
    block()
} catch (failure: LinkageError) {
    throw CompatLoadException("$PLUGIN failed. ${unlinkedCompiler()}", failure)
}
