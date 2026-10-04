package io.akki.compiler.jar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.objectweb.asm.Opcodes.ACC_PUBLIC

internal class AdapterChainTest {
    private val oldest = Adapter("2.4.0", "adapter.oldest.Compat")
    private val middle = Adapter("2.4.10", "adapter.middle.Compat")
    private val latest = Adapter("2.4.20", "adapter.latest.Compat")
    private val adapters = listOf(oldest, middle, latest)

    @Test
    fun `an independent adapter does not load older implementations`() {
        assertEquals(listOf(latest), adapterChain(latest, adapters, shapes()))
    }

    @Test
    fun `follows the actual delegate when an intermediate adapter is skipped`() {
        assertEquals(
            listOf(latest, oldest),
            adapterChain(latest, adapters, shapes(latest to listOf(oldest))),
        )
    }

    @Test
    fun `follows transitive delegates`() {
        assertEquals(
            listOf(latest, middle, oldest),
            adapterChain(latest, adapters, shapes(latest to listOf(middle), middle to listOf(oldest))),
        )
    }

    @Test
    fun `rejects delegation to itself or a newer adapter`() {
        for (delegate in listOf(middle, latest)) {
            assertFailsWith<IllegalArgumentException> {
                adapterChain(middle, adapters, shapes(middle to listOf(delegate)))
            }
        }
    }

    @Test
    fun `rejects ambiguous delegation`() {
        assertFailsWith<IllegalArgumentException> {
            adapterChain(latest, adapters, shapes(latest to listOf(oldest, middle)))
        }
    }

    private fun shapes(vararg delegates: Pair<Adapter, List<Adapter>>): Map<String, ClassShape> {
        val byAdapter = delegates.toMap()
        return adapters.associate { adapter ->
            val name = adapter.implementation.internalName
            val constructors = byAdapter[adapter].orEmpty().mapTo(mutableSetOf()) { delegate ->
                MemberReference(delegate.factory.internalName, "<init>", "()V", false, false, false)
            }
            name to ClassShape(
                name, ACC_PUBLIC, "java/lang/Object", listOf(CONTRACT), emptyMap(), mapOf("<init>()V" to constructors),
            )
        }
    }
}
