package io.akki

import io.akki.internal.JvmLoggerNameStyle
import io.akki.internal.platformTypeName
import java.lang.StackWalker.Option.RETAIN_CLASS_REFERENCE
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.startCoroutine
import kotlin.coroutines.suspendCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.slf4j.LoggerFactory

class JvmLoggerNameProbeTest {
    @Test
    fun `prints current logger names for JVM declarations`(): Unit {
        val probes = listOf(
            TopLevelClassProbe().probe(),
            NestedProbe().probe(),
            InnerProbe().probe(),
            companionProbe(),
            NamedCompanionOwner.probe(),
            AnnotatedCompanionOwner.probe(),
            NestedObjectProbe.probe(),
            StandaloneProbe.probe(),
            lambdaProbe(),
            samProbe(),
            objectExpressionProbe(),
            localClassProbe(),
            annotatedLocalClassProbe(),
            EnumProbe.ENTRY.probe(),
            DefaultProbeImpl().probe(),
            runSuspendProbe(),
            inlineProbe(),
            regularTopLevelProbe(),
            customJvmNameProbe(),
            multifileProbe(),
            namedMultifileProbe(),
            namedClassProbe(),
            namedFileProbe(),
        )

        println(probes.render())

        assertEquals(expectedCurrentJvmNames, probes.map { it.site to it.stackClass })
        probes.forEach { probe ->
            assertEquals(probe.stackClass, probe.slf4jName, probe.site)
        }
        assertEquals(
            byStyle(expectedSourceNames, expectedJvmClassNames),
            probes.map { it.site to platformTypeName(it.stackType) },
        )
        assertEquals(
            expectedSourceNames,
            probes.map { probe ->
                probe.site to platformTypeName(probe.stackType, JvmLoggerNameStyle.SOURCE)
            },
        )
        assertEquals(
            expectedJvmClassNames,
            probes.map { probe ->
                probe.site to platformTypeName(probe.stackType, JvmLoggerNameStyle.JVM_CLASS)
            },
        )
    }

    @Test
    fun `SLF4J uses the provided class rather than the calling class`(): Unit {
        val providedClass = StandaloneProbe::class.java
        val callingClass = this::class.java
        val slf4jName = LoggerFactory.getLogger(providedClass).name

        println("caller=${callingClass.name}, provided=${providedClass.name}, slf4j=$slf4jName")

        assertEquals(providedClass.name, slf4jName)
        assertTrue(providedClass != callingClass)
    }

    private class NestedProbe {
        fun probe(): JvmLoggerNameProbe = captureLoggerNames("nested class")
    }

    private inner class InnerProbe {
        fun probe(): JvmLoggerNameProbe = captureLoggerNames("inner class")
    }

    private companion object {
        fun companionProbe(): JvmLoggerNameProbe = captureLoggerNames("companion")
    }

    private object NestedObjectProbe {
        fun probe(): JvmLoggerNameProbe = captureLoggerNames("nested object")
    }

    private fun lambdaProbe(): JvmLoggerNameProbe =
        { captureLoggerNames("lambda") }.invoke()

    private fun samProbe(): JvmLoggerNameProbe =
        ProbeSam { captureLoggerNames("SAM") }.probe()

    private fun objectExpressionProbe(): JvmLoggerNameProbe =
        object {
            fun probe(): JvmLoggerNameProbe = captureLoggerNames("object expression")
        }.probe()

    private fun localClassProbe(): JvmLoggerNameProbe {
        class LocalProbe {
            fun probe(): JvmLoggerNameProbe = captureLoggerNames("local class")
        }

        return LocalProbe().probe()
    }

    private fun annotatedLocalClassProbe(): JvmLoggerNameProbe {
        @LogName("named-local")
        class LocalProbe {
            fun probe(): JvmLoggerNameProbe = captureLoggerNames("@LogName local class")
        }

        return LocalProbe().probe()
    }

    private fun runSuspendProbe(): JvmLoggerNameProbe {
        var completion: Result<JvmLoggerNameProbe>? = null
        var suspended: Continuation<Unit>? = null
        suspend {
            suspendCoroutine { continuation -> suspended = continuation }
            captureLoggerNames("suspend function")
        }.startCoroutine(
            object : Continuation<JvmLoggerNameProbe> {
                override val context = EmptyCoroutineContext

                override fun resumeWith(result: Result<JvmLoggerNameProbe>): Unit {
                    completion = result
                }
            },
        )
        requireNotNull(suspended).resume(Unit)
        return requireNotNull(completion).getOrThrow()
    }

    private enum class EnumProbe {
        ENTRY {
            override fun probe(): JvmLoggerNameProbe = captureLoggerNames("enum entry")
        },
        ;

        abstract fun probe(): JvmLoggerNameProbe
    }

    private fun interface ProbeSam {
        fun probe(): JvmLoggerNameProbe
    }

    private interface DefaultProbe {
        fun probe(): JvmLoggerNameProbe = captureLoggerNames("interface default")
    }

    private class DefaultProbeImpl : DefaultProbe
}

private class TopLevelClassProbe {
    fun probe(): JvmLoggerNameProbe = captureLoggerNames("top-level class")
}

private object StandaloneProbe {
    fun probe(): JvmLoggerNameProbe = captureLoggerNames("standalone object")
}

@LogName("named-companion-owner")
private class NamedCompanionOwner {
    companion object Factory {
        fun probe(): JvmLoggerNameProbe = captureLoggerNames("named companion owner")
    }
}

private class AnnotatedCompanionOwner {
    @LogName("named-companion")
    companion object {
        fun probe(): JvmLoggerNameProbe = captureLoggerNames("@LogName companion")
    }
}

@LogName("named-probe")
private class NamedProbe {
    fun probe(): JvmLoggerNameProbe = captureLoggerNames("@LogName class")
}

private fun namedClassProbe(): JvmLoggerNameProbe = NamedProbe().probe()

internal data class JvmLoggerNameProbe(
    val site: String,
    val stackClass: String,
    val slf4jName: String,
    val stackType: Class<*>,
)

internal fun captureLoggerNames(site: String): JvmLoggerNameProbe {
    val stackClass = probeWalker.walk { frames ->
        frames.skip(1).map(StackWalker.StackFrame::getDeclaringClass).findFirst().orElseThrow()
    }
    return JvmLoggerNameProbe(
        site = site,
        stackClass = stackClass.name,
        slf4jName = LoggerFactory.getLogger(stackClass).name,
        stackType = stackClass,
    )
}

private fun List<JvmLoggerNameProbe>.render(): String = buildString {
    appendLine("site | StackWalker | SLF4J(Class) | Akki SOURCE | JVM_CLASS")
    for (probe in this@render) {
        append(probe.site)
        append(" | ")
        append(probe.stackClass)
        append(" | ")
        append(probe.slf4jName)
        append(" | ")
        append(platformTypeName(probe.stackType, JvmLoggerNameStyle.SOURCE))
        append(" | ")
        appendLine(platformTypeName(probe.stackType, JvmLoggerNameStyle.JVM_CLASS))
    }
}

private val probeWalker: StackWalker = StackWalker.getInstance(RETAIN_CLASS_REFERENCE)

@Suppress("NOTHING_TO_INLINE")
private inline fun inlineProbe(): JvmLoggerNameProbe = captureLoggerNames("inline function")

private fun regularTopLevelProbe(): JvmLoggerNameProbe = captureLoggerNames("top-level function")

private val expectedCurrentJvmNames: List<Pair<String, String>> = listOf(
    "top-level class" to "io.akki.TopLevelClassProbe",
    "nested class" to "io.akki.JvmLoggerNameProbeTest\$NestedProbe",
    "inner class" to "io.akki.JvmLoggerNameProbeTest\$InnerProbe",
    "companion" to "io.akki.JvmLoggerNameProbeTest\$Companion",
    "named companion owner" to "io.akki.NamedCompanionOwner\$Factory",
    "@LogName companion" to "io.akki.AnnotatedCompanionOwner\$Companion",
    "nested object" to "io.akki.JvmLoggerNameProbeTest\$NestedObjectProbe",
    "standalone object" to "io.akki.StandaloneProbe",
    "lambda" to "io.akki.JvmLoggerNameProbeTest",
    "SAM" to "io.akki.JvmLoggerNameProbeTest",
    "object expression" to "io.akki.JvmLoggerNameProbeTest\$objectExpressionProbe\$1",
    "local class" to "io.akki.JvmLoggerNameProbeTest\$localClassProbe\$LocalProbe",
    "@LogName local class" to "io.akki.JvmLoggerNameProbeTest\$annotatedLocalClassProbe\$LocalProbe",
    "enum entry" to "io.akki.JvmLoggerNameProbeTest\$EnumProbe\$ENTRY",
    "interface default" to "io.akki.JvmLoggerNameProbeTest\$DefaultProbe",
    "suspend function" to "io.akki.JvmLoggerNameProbeTest\$runSuspendProbe\$1",
    "inline function" to "io.akki.JvmLoggerNameProbeTest",
    "top-level function" to "io.akki.JvmLoggerNameProbeTestKt",
    "@file:JvmName" to "io.akki.CustomProbeFacade",
    "multifile" to "io.akki.SharedProbeFacade__MultifileProbeSiteKt",
    "@LogName multifile" to "io.akki.SharedProbeFacade__NamedMultifileProbeSiteKt",
    "@LogName class" to "io.akki.NamedProbe",
    "@LogName file" to "io.akki.NamedFileProbeSiteKt",
)

private val expectedSourceNames: List<Pair<String, String>> = listOf(
    "top-level class" to "io.akki.TopLevelClassProbe",
    "nested class" to "io.akki.JvmLoggerNameProbeTest.NestedProbe",
    "inner class" to "io.akki.JvmLoggerNameProbeTest.InnerProbe",
    "companion" to "io.akki.JvmLoggerNameProbeTest",
    "named companion owner" to "named-companion-owner",
    "@LogName companion" to "named-companion",
    "nested object" to "io.akki.JvmLoggerNameProbeTest.NestedObjectProbe",
    "standalone object" to "io.akki.StandaloneProbe",
    "lambda" to "io.akki.JvmLoggerNameProbeTest",
    "SAM" to "io.akki.JvmLoggerNameProbeTest",
    "object expression" to "io.akki.JvmLoggerNameProbeTest",
    "local class" to "io.akki.JvmLoggerNameProbeTest",
    "@LogName local class" to "named-local",
    "enum entry" to "io.akki.JvmLoggerNameProbeTest.EnumProbe",
    "interface default" to "io.akki.JvmLoggerNameProbeTest.DefaultProbe",
    "suspend function" to "io.akki.JvmLoggerNameProbeTest",
    "inline function" to "io.akki.JvmLoggerNameProbeTest",
    "top-level function" to "io.akki.JvmLoggerNameProbeTest",
    "@file:JvmName" to "io.akki.CustomProbeFacade",
    "multifile" to "io.akki.MultifileProbeSite",
    "@LogName multifile" to "named-multifile-probe",
    "@LogName class" to "named-probe",
    "@LogName file" to "named-file-probe",
)

private val expectedJvmClassNames: List<Pair<String, String>> = listOf(
    "top-level class" to "io.akki.TopLevelClassProbe",
    "nested class" to "io.akki.JvmLoggerNameProbeTest\$NestedProbe",
    "inner class" to "io.akki.JvmLoggerNameProbeTest\$InnerProbe",
    "companion" to "io.akki.JvmLoggerNameProbeTest",
    "named companion owner" to "named-companion-owner",
    "@LogName companion" to "named-companion",
    "nested object" to "io.akki.JvmLoggerNameProbeTest\$NestedObjectProbe",
    "standalone object" to "io.akki.StandaloneProbe",
    "lambda" to "io.akki.JvmLoggerNameProbeTest",
    "SAM" to "io.akki.JvmLoggerNameProbeTest",
    "object expression" to "io.akki.JvmLoggerNameProbeTest",
    "local class" to "io.akki.JvmLoggerNameProbeTest",
    "@LogName local class" to "named-local",
    "enum entry" to "io.akki.JvmLoggerNameProbeTest\$EnumProbe",
    "interface default" to "io.akki.JvmLoggerNameProbeTest\$DefaultProbe",
    "suspend function" to "io.akki.JvmLoggerNameProbeTest",
    "inline function" to "io.akki.JvmLoggerNameProbeTest",
    "top-level function" to "io.akki.JvmLoggerNameProbeTestKt",
    "@file:JvmName" to "io.akki.CustomProbeFacade",
    "multifile" to "io.akki.SharedProbeFacade__MultifileProbeSiteKt",
    "@LogName multifile" to "named-multifile-probe",
    "@LogName class" to "named-probe",
    "@LogName file" to "named-file-probe",
)
