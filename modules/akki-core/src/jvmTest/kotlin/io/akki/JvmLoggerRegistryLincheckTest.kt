package io.akki

import java.util.concurrent.ConcurrentHashMap
import kotlin.test.Test
import org.jetbrains.lincheck.datastructures.ModelCheckingOptions
import org.jetbrains.lincheck.datastructures.Operation
import org.jetbrains.lincheck.datastructures.forClasses

class JvmLoggerRegistryLincheckTest {
    @Operation
    fun first(): Logger = Log.named(FIRST)

    @Operation
    fun second(): Logger = Log.named(SECOND)

    @Test
    fun `resolving a known name takes no lock even when its hash collides`() {
        ModelCheckingOptions()
            .checkObstructionFreedom()
            .addGuarantee(forClasses(ConcurrentHashMap::class).methods("get").treatAsAtomic())
            .check(this::class)
    }

    private companion object {
        const val FIRST = "lincheck.registry.Aa"
        const val SECOND = "lincheck.registry.BB"

        init {
            check(FIRST.hashCode() == SECOND.hashCode())
            Log.named(FIRST)
            Log.named(SECOND)
        }
    }
}
