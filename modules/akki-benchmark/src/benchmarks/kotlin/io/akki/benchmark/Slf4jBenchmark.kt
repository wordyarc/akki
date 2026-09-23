package io.akki.benchmark

import ch.qos.logback.classic.Level as LogbackLevel
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import io.akki.DelicateAkkiApi
import io.akki.Log
import io.akki.Logger
import io.akki.slf4j.Slf4jBackend
import java.util.concurrent.TimeUnit
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.BenchmarkMode
import kotlinx.benchmark.Mode
import kotlinx.benchmark.OutputTimeUnit
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlinx.benchmark.TearDown
import org.slf4j.LoggerFactory

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
class Slf4jBenchmark {
    private lateinit var installation: Log.Installation
    private lateinit var log: Logger
    private lateinit var slf4j: org.slf4j.Logger
    private var id: Long = 0

    @OptIn(DelicateAkkiApi::class)
    @Setup
    fun install() {
        val context = LoggerFactory.getILoggerFactory() as LoggerContext
        context.reset()
        context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).apply {
            level = LogbackLevel.INFO
            addAppender(DiscardingAppender().also { it.context = context; it.start() })
        }
        installation = Log.install(Slf4jBackend())
        log = Log.named(NAME)
        slf4j = LoggerFactory.getLogger(NAME)
    }

    @TearDown
    fun uninstall(): Unit = installation.close()

    @Benchmark
    fun akkiRecorded() {
        log.info("order ${id++} accepted")
    }

    @Benchmark
    fun akkiRecordedWithFields() {
        log.info("order accepted", fields = FIELDS)
    }

    @Benchmark
    fun akkiSuppressed() {
        log.debug("order ${id++} accepted")
    }

    @Benchmark
    fun akkiSuppressedLazy() {
        log.debug { "order ${id++} accepted" }
    }

    @Benchmark
    fun slf4jRecorded() {
        slf4j.info("order ${id++} accepted")
    }

    @Benchmark
    fun slf4jFluentRecorded() {
        slf4j.atInfo().log("order ${id++} accepted")
    }

    @Benchmark
    fun slf4jFluentRecordedWithFields() {
        slf4j.atInfo().addKeyValue("id", 1).log("order accepted")
    }

    @Benchmark
    fun slf4jSuppressed() {
        slf4j.debug("order ${id++} accepted")
    }

    @Benchmark
    fun slf4jSuppressedGuarded() {
        if (slf4j.isDebugEnabled) slf4j.debug("order ${id++} accepted")
    }

    private companion object {
        const val NAME: String = "io.akki.benchmark.Slf4jBenchmark"

        val FIELDS: Map<String, Any?> = mapOf("id" to 1)
    }
}

private class DiscardingAppender : AppenderBase<ILoggingEvent>() {
    override fun append(event: ILoggingEvent) = Unit
}
