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

class JvmLoggerNameProbeTest {
    @Test
    fun `derives the logger name of every JVM declaration shape`(): Unit {
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

        assertEquals(expectations.map { it.site }, probes.map { it.site })
        probes.zip(expectations).forEach { (probe, expected) ->
            assertEquals(expected.jvmName, probe.stackClass, probe.site)
            assertEquals(expected.source, platformTypeName(probe.stackType, JvmLoggerNameStyle.SOURCE), probe.site)
            assertEquals(expected.jvmClass, platformTypeName(probe.stackType, JvmLoggerNameStyle.JVM_CLASS), probe.site)
            assertEquals(byStyle(expected.source, expected.jvmClass), platformTypeName(probe.stackType), probe.site)
        }
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

internal class JvmLoggerNameProbe(
    val site: String,
    val stackClass: String,
    val stackType: Class<*>,
)

internal fun captureLoggerNames(site: String): JvmLoggerNameProbe {
    val stackClass = probeWalker.walk { frames ->
        frames.skip(1).map(StackWalker.StackFrame::getDeclaringClass).findFirst().orElseThrow()
    }
    return JvmLoggerNameProbe(site, stackClass.name, stackClass)
}

private val probeWalker: StackWalker = StackWalker.getInstance(RETAIN_CLASS_REFERENCE)

@Suppress("NOTHING_TO_INLINE")
private inline fun inlineProbe(): JvmLoggerNameProbe = captureLoggerNames("inline function")

private fun regularTopLevelProbe(): JvmLoggerNameProbe = captureLoggerNames("top-level function")

private class Expectation(
    val site: String,
    val jvmName: String,
    val source: String = jvmName,
    val jvmClass: String = jvmName,
)

private const val TEST: String = "io.akki.JvmLoggerNameProbeTest"

private val expectations: List<Expectation> = listOf(
    Expectation("top-level class", "io.akki.TopLevelClassProbe"),
    Expectation("nested class", "$TEST\$NestedProbe", source = "$TEST.NestedProbe"),
    Expectation("inner class", "$TEST\$InnerProbe", source = "$TEST.InnerProbe"),
    Expectation("companion", "$TEST\$Companion", source = TEST, jvmClass = TEST),
    Expectation(
        "named companion owner",
        "io.akki.NamedCompanionOwner\$Factory",
        "named-companion-owner",
        "named-companion-owner",
    ),
    Expectation(
        "@LogName companion",
        "io.akki.AnnotatedCompanionOwner\$Companion",
        "named-companion",
        "named-companion",
    ),
    Expectation("nested object", "$TEST\$NestedObjectProbe", source = "$TEST.NestedObjectProbe"),
    Expectation("standalone object", "io.akki.StandaloneProbe"),
    Expectation("lambda", TEST),
    Expectation("SAM", TEST),
    Expectation("object expression", "$TEST\$objectExpressionProbe\$1", source = TEST, jvmClass = TEST),
    Expectation("local class", "$TEST\$localClassProbe\$LocalProbe", source = TEST, jvmClass = TEST),
    Expectation("@LogName local class", "$TEST\$annotatedLocalClassProbe\$LocalProbe", "named-local", "named-local"),
    Expectation("enum entry", "$TEST\$EnumProbe\$ENTRY", source = "$TEST.EnumProbe", jvmClass = "$TEST\$EnumProbe"),
    Expectation("interface default", "$TEST\$DefaultProbe", source = "$TEST.DefaultProbe"),
    Expectation("suspend function", "$TEST\$runSuspendProbe\$1", source = TEST, jvmClass = TEST),
    Expectation("inline function", TEST),
    Expectation("top-level function", "${TEST}Kt", source = TEST),
    Expectation("@file:JvmName", "io.akki.CustomProbeFacade"),
    Expectation("multifile", "io.akki.SharedProbeFacade__MultifileProbeSiteKt", source = "io.akki.MultifileProbeSite"),
    Expectation(
        "@LogName multifile",
        "io.akki.SharedProbeFacade__NamedMultifileProbeSiteKt",
        "named-multifile-probe",
        "named-multifile-probe",
    ),
    Expectation("@LogName class", "io.akki.NamedProbe", "named-probe", "named-probe"),
    Expectation("@LogName file", "io.akki.NamedFileProbeSiteKt", "named-file-probe", "named-file-probe"),
)
