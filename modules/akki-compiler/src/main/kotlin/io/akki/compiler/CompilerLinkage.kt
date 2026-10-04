package io.akki.compiler

import io.akki.compiler.compat.CompatLoadException
import io.akki.compiler.compat.linkageBreak
import org.jetbrains.kotlin.config.KotlinCompilerVersion

internal const val PLUGIN: String = "akki: compiler plugin $AKKI_VERSION"

internal const val CANNOT_START: String = "$PLUGIN cannot start."

internal fun unlinkedCompiler(): String =
    "Kotlin ${KotlinCompilerVersion.getVersion()} is not supported: the compiler plugin does not link against it. " +
        "Kotlin releases up to $LATEST_TESTED_KOTLIN are tested."

internal fun causeLines(cause: Throwable?): String =
    generateSequence(cause, Throwable::cause).joinToString("") { "\nCaused by: $it" }

internal inline fun <T> linked(block: () -> T): T = try {
    block()
} catch (failure: Throwable) {
    val broken = failure.linkageBreak ?: throw failure
    throw CompatLoadException("$PLUGIN failed. ${unlinkedCompiler()}", broken)
}
