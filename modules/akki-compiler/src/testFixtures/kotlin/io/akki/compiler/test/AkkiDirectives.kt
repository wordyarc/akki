package io.akki.compiler.test

import io.akki.compiler.MinLevel
import org.jetbrains.kotlin.test.directives.model.SimpleDirectivesContainer

internal object AkkiDirectives : SimpleDirectivesContainer() {
    val MIN_LEVEL by enumDirective<MinLevel>("The minLevel option of the Akki compiler plugin")

    val WARNING_LEVEL by stringDirective("-Xwarning-level overrides, one NAME:(error|warning|disabled) per value")

    val WITHOUT_PLUGIN by directive("Compile the module without the Akki compiler plugin")

    val WITHOUT_AKKI by directive("Leave akki-core, akki-slf4j, akki-test and logback off the classpath")

    val WITH_LOGBACK by directive("Compile testData/helpers/Logback.kt into the module")

    val WITH_HELPERS by directive(
        "Compile testData/helpers/Helpers.kt into the module; with CHECK_BYTECODE_TEXT add TREAT_AS_ONE_FILE " +
            "so that the helpers are not counted",
    )

    val BACKEND_SERVICES by stringDirective("LogBackend providers on the runtime classpath instead of akki-slf4j")

    val CHECK_SOURCELESS_DIAGNOSTICS by directive(
        "Compare the diagnostics reported without a source element against the .sourceless.txt file",
    )
}
