@file:OptIn(InternalAkkiApi::class)

package io.akki.benchmark

import io.akki.InternalAkkiApi
import io.akki.Log
import io.akki.Logger
import io.akki.internal.LogRegistry
import java.util.concurrent.TimeUnit
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.BenchmarkMode
import kotlinx.benchmark.Mode
import kotlinx.benchmark.OutputTimeUnit
import kotlinx.benchmark.Scope
import kotlinx.benchmark.State


@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
class ResolutionBenchmark {
    class Owner {
        class Nested
    }

    @Benchmark
    fun factoryByRuntimeClass(): Logger = Log.of(Owner.Nested::class.java)

    @Benchmark
    fun factoryByType(): Logger = Log.of<Owner.Nested>()

    @Benchmark
    fun factoryByName(): Logger = Log.named("io.akki.benchmark.ResolutionBenchmark.Owner.Nested")

    @Benchmark
    fun compiledConstants(): Logger = LogRegistry.forDeclaration(
        "io.akki.benchmark.ResolutionBenchmark.Owner.Nested",
        "io.akki.benchmark.ResolutionBenchmark\$Owner\$Nested",
    )
}
