package dev.ashenarx.akki

import dev.ashenarx.akki.internal.JvmLoggerNameStyle
import dev.ashenarx.akki.internal.platformTypeName
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
        assertEquals(byStyle(expectedSourceNames, expectedJvmClassNames), probes.map { it.site to it.akkiName })
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
        fun probe(): JvmLoggerNameProbe = captureLoggerNames("nested class", log)
    }

    private inner class InnerProbe {
        fun probe(): JvmLoggerNameProbe = captureLoggerNames("inner class", log)
    }

    private companion object {
        fun companionProbe(): JvmLoggerNameProbe = captureLoggerNames("companion", log)
    }

    private object NestedObjectProbe {
        fun probe(): JvmLoggerNameProbe = captureLoggerNames("nested object", log)
    }

    private fun lambdaProbe(): JvmLoggerNameProbe =
        { captureLoggerNames("lambda", log) }.invoke()

    private fun samProbe(): JvmLoggerNameProbe =
        ProbeSam { captureLoggerNames("SAM", log) }.probe()

    private fun objectExpressionProbe(): JvmLoggerNameProbe =
        object {
            fun probe(): JvmLoggerNameProbe = captureLoggerNames("object expression", log)
        }.probe()

    private fun localClassProbe(): JvmLoggerNameProbe {
        class LocalProbe {
            fun probe(): JvmLoggerNameProbe = captureLoggerNames("local class", log)
        }

        return LocalProbe().probe()
    }

    private fun annotatedLocalClassProbe(): JvmLoggerNameProbe {
        @LogName("named-local")
        class LocalProbe {
            fun probe(): JvmLoggerNameProbe = captureLoggerNames("@LogName local class", log)
        }

        return LocalProbe().probe()
    }

    private fun runSuspendProbe(): JvmLoggerNameProbe {
        var completion: Result<JvmLoggerNameProbe>? = null
        var suspended: Continuation<Unit>? = null
        suspend {
            suspendCoroutine { continuation -> suspended = continuation }
            captureLoggerNames("suspend function", log)
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
            override fun probe(): JvmLoggerNameProbe = captureLoggerNames("enum entry", log)
        },
        ;

        abstract fun probe(): JvmLoggerNameProbe
    }

    private fun interface ProbeSam {
        fun probe(): JvmLoggerNameProbe
    }

    private interface DefaultProbe {
        fun probe(): JvmLoggerNameProbe = captureLoggerNames("interface default", log)
    }

    private class DefaultProbeImpl : DefaultProbe
}

private class TopLevelClassProbe {
    fun probe(): JvmLoggerNameProbe = captureLoggerNames("top-level class", log)
}

private object StandaloneProbe {
    fun probe(): JvmLoggerNameProbe = captureLoggerNames("standalone object", log)
}

@LogName("named-companion-owner")
private class NamedCompanionOwner {
    companion object Factory {
        fun probe(): JvmLoggerNameProbe = captureLoggerNames("named companion owner", log)
    }
}

private class AnnotatedCompanionOwner {
    @LogName("named-companion")
    companion object {
        fun probe(): JvmLoggerNameProbe = captureLoggerNames("@LogName companion", log)
    }
}

@LogName("named-probe")
private class NamedProbe {
    fun probe(): JvmLoggerNameProbe = captureLoggerNames("@LogName class", log)
}

private fun namedClassProbe(): JvmLoggerNameProbe = NamedProbe().probe()

internal data class JvmLoggerNameProbe(
    val site: String,
    val stackClass: String,
    val slf4jName: String,
    val akkiName: String,
    val stackType: Class<*>,
)

internal fun captureLoggerNames(site: String, akkiLogger: Logger): JvmLoggerNameProbe {
    val stackClass = probeWalker.walk { frames ->
        frames.skip(1).map(StackWalker.StackFrame::getDeclaringClass).findFirst().orElseThrow()
    }
    return JvmLoggerNameProbe(
        site = site,
        stackClass = stackClass.name,
        slf4jName = LoggerFactory.getLogger(stackClass).name,
        akkiName = akkiLogger.name,
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
        append(probe.akkiName)
        append(" | ")
        appendLine(platformTypeName(probe.stackType, JvmLoggerNameStyle.JVM_CLASS))
    }
}

private val probeWalker: StackWalker = StackWalker.getInstance(RETAIN_CLASS_REFERENCE)

@Suppress("NOTHING_TO_INLINE")
private inline fun inlineProbe(): JvmLoggerNameProbe = captureLoggerNames("inline function", log)

private fun regularTopLevelProbe(): JvmLoggerNameProbe = captureLoggerNames("top-level function", log)

private val expectedCurrentJvmNames: List<Pair<String, String>> = listOf(
    "top-level class" to "dev.ashenarx.akki.TopLevelClassProbe",
    "nested class" to "dev.ashenarx.akki.JvmLoggerNameProbeTest\$NestedProbe",
    "inner class" to "dev.ashenarx.akki.JvmLoggerNameProbeTest\$InnerProbe",
    "companion" to "dev.ashenarx.akki.JvmLoggerNameProbeTest\$Companion",
    "named companion owner" to "dev.ashenarx.akki.NamedCompanionOwner\$Factory",
    "@LogName companion" to "dev.ashenarx.akki.AnnotatedCompanionOwner\$Companion",
    "nested object" to "dev.ashenarx.akki.JvmLoggerNameProbeTest\$NestedObjectProbe",
    "standalone object" to "dev.ashenarx.akki.StandaloneProbe",
    "lambda" to "dev.ashenarx.akki.JvmLoggerNameProbeTest",
    "SAM" to "dev.ashenarx.akki.JvmLoggerNameProbeTest",
    "object expression" to "dev.ashenarx.akki.JvmLoggerNameProbeTest\$objectExpressionProbe\$1",
    "local class" to "dev.ashenarx.akki.JvmLoggerNameProbeTest\$localClassProbe\$LocalProbe",
    "@LogName local class" to "dev.ashenarx.akki.JvmLoggerNameProbeTest\$annotatedLocalClassProbe\$LocalProbe",
    "enum entry" to "dev.ashenarx.akki.JvmLoggerNameProbeTest\$EnumProbe\$ENTRY",
    "interface default" to "dev.ashenarx.akki.JvmLoggerNameProbeTest\$DefaultProbe",
    "suspend function" to "dev.ashenarx.akki.JvmLoggerNameProbeTest\$runSuspendProbe\$1",
    "inline function" to "dev.ashenarx.akki.JvmLoggerNameProbeTest",
    "top-level function" to "dev.ashenarx.akki.JvmLoggerNameProbeTestKt",
    "@file:JvmName" to "dev.ashenarx.akki.CustomProbeFacade",
    "multifile" to "dev.ashenarx.akki.SharedProbeFacade__MultifileProbeSiteKt",
    "@LogName multifile" to "dev.ashenarx.akki.SharedProbeFacade__NamedMultifileProbeSiteKt",
    "@LogName class" to "dev.ashenarx.akki.NamedProbe",
    "@LogName file" to "dev.ashenarx.akki.NamedFileProbeSiteKt",
)

private val expectedSourceNames: List<Pair<String, String>> = listOf(
    "top-level class" to "dev.ashenarx.akki.TopLevelClassProbe",
    "nested class" to "dev.ashenarx.akki.JvmLoggerNameProbeTest.NestedProbe",
    "inner class" to "dev.ashenarx.akki.JvmLoggerNameProbeTest.InnerProbe",
    "companion" to "dev.ashenarx.akki.JvmLoggerNameProbeTest",
    "named companion owner" to "named-companion-owner",
    "@LogName companion" to "named-companion",
    "nested object" to "dev.ashenarx.akki.JvmLoggerNameProbeTest.NestedObjectProbe",
    "standalone object" to "dev.ashenarx.akki.StandaloneProbe",
    "lambda" to "dev.ashenarx.akki.JvmLoggerNameProbeTest",
    "SAM" to "dev.ashenarx.akki.JvmLoggerNameProbeTest",
    "object expression" to "dev.ashenarx.akki.JvmLoggerNameProbeTest",
    "local class" to "dev.ashenarx.akki.JvmLoggerNameProbeTest",
    "@LogName local class" to "named-local",
    "enum entry" to "dev.ashenarx.akki.JvmLoggerNameProbeTest.EnumProbe",
    "interface default" to "dev.ashenarx.akki.JvmLoggerNameProbeTest.DefaultProbe",
    "suspend function" to "dev.ashenarx.akki.JvmLoggerNameProbeTest",
    "inline function" to "dev.ashenarx.akki.JvmLoggerNameProbeTest",
    "top-level function" to "dev.ashenarx.akki.JvmLoggerNameProbeTest",
    "@file:JvmName" to "dev.ashenarx.akki.CustomProbeFacade",
    "multifile" to "dev.ashenarx.akki.MultifileProbeSite",
    "@LogName multifile" to "named-multifile-probe",
    "@LogName class" to "named-probe",
    "@LogName file" to "named-file-probe",
)

private val expectedJvmClassNames: List<Pair<String, String>> = listOf(
    "top-level class" to "dev.ashenarx.akki.TopLevelClassProbe",
    "nested class" to "dev.ashenarx.akki.JvmLoggerNameProbeTest\$NestedProbe",
    "inner class" to "dev.ashenarx.akki.JvmLoggerNameProbeTest\$InnerProbe",
    "companion" to "dev.ashenarx.akki.JvmLoggerNameProbeTest",
    "named companion owner" to "named-companion-owner",
    "@LogName companion" to "named-companion",
    "nested object" to "dev.ashenarx.akki.JvmLoggerNameProbeTest\$NestedObjectProbe",
    "standalone object" to "dev.ashenarx.akki.StandaloneProbe",
    "lambda" to "dev.ashenarx.akki.JvmLoggerNameProbeTest",
    "SAM" to "dev.ashenarx.akki.JvmLoggerNameProbeTest",
    "object expression" to "dev.ashenarx.akki.JvmLoggerNameProbeTest",
    "local class" to "dev.ashenarx.akki.JvmLoggerNameProbeTest",
    "@LogName local class" to "named-local",
    "enum entry" to "dev.ashenarx.akki.JvmLoggerNameProbeTest\$EnumProbe",
    "interface default" to "dev.ashenarx.akki.JvmLoggerNameProbeTest\$DefaultProbe",
    "suspend function" to "dev.ashenarx.akki.JvmLoggerNameProbeTest",
    "inline function" to "dev.ashenarx.akki.JvmLoggerNameProbeTest",
    "top-level function" to "dev.ashenarx.akki.JvmLoggerNameProbeTestKt",
    "@file:JvmName" to "dev.ashenarx.akki.CustomProbeFacade",
    "multifile" to "dev.ashenarx.akki.SharedProbeFacade__MultifileProbeSiteKt",
    "@LogName multifile" to "named-multifile-probe",
    "@LogName class" to "named-probe",
    "@LogName file" to "named-file-probe",
)
