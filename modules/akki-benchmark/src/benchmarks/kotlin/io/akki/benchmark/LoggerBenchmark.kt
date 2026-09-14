package io.akki.benchmark

import io.akki.DelicateAkkiApi
import io.akki.Level
import io.akki.Log
import io.akki.Logger
import io.akki.backend.LogBackend
import io.akki.backend.LoggerBinding
import io.akki.backend.Sink
import java.util.concurrent.TimeUnit
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.BenchmarkMode
import kotlinx.benchmark.Blackhole
import kotlinx.benchmark.Mode
import kotlinx.benchmark.OutputTimeUnit
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlinx.benchmark.TearDown

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
class LoggerBenchmark {
    private lateinit var installation: Log.Installation
    private lateinit var log: Logger
    private var id: Long = 0

    @OptIn(DelicateAkkiApi::class)
    @Setup
    fun install() {
        installation = Log.install(CountingBackend)
        log = Log.named("io.akki.benchmark.LoggerBenchmark")
    }

    @TearDown
    fun uninstall(): Unit = installation.close()

    @Benchmark
    fun suppressedLevel() {
        log.debug("order ${id++} accepted at ${System.nanoTime()}")
    }

    @Benchmark
    fun suppressedLevelLazy() {
        log.debug { "order ${id++} accepted at ${System.nanoTime()}" }
    }

    @Benchmark
    fun recordedLevel() {
        log.info("order ${id++} accepted")
    }

    @Benchmark
    fun constantMessage() {
        log.info("accepted")
    }

    @Benchmark
    fun baselineWithoutLogging(blackhole: Blackhole) {
        blackhole.consume("order ${id++} accepted at ${System.nanoTime()}")
    }
}

private object CountingBackend : LogBackend {
    private val sink = Sink { _, _, _ -> }

    private val binding = LoggerBinding { level -> sink.takeIf { level >= Level.INFO } }

    override fun bind(name: String): LoggerBinding = binding
}
