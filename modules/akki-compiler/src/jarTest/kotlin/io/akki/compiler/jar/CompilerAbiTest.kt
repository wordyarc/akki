package io.akki.compiler.jar

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

internal class CompilerAbiTest {
    private val classes: Map<String, ByteArray> = compilerJar.entries()
        .filter { (name, _) -> name.endsWith(".class") }
        .associate { (name, bytes) -> name.removeSuffix(".class") to bytes }

    private val shapes: Map<String, ClassShape> =
        classes.mapValues { (_, bytes) -> ClassShape.read(bytes, withReferences = true) }

    private val factories: Set<String> = adapters.mapTo(mutableSetOf()) { it.factory.internalName }

    private val operations: Set<String> =
        shapes.getValue(CONTRACT).members.filterValues { it and Opcodes.ACC_ABSTRACT != 0 }.keys

    @ParameterizedTest(name = "Kotlin {0}")
    @MethodSource("checkedKotlin")
    fun `resolves every reference of the plugin with the selected adapter`(kotlin: String) {
        val adapter = adapterOf(kotlin)
        val plugin = pluginWith(adapter)

        assertContains(plugin, adapter.implementation.internalName)
        assertTrue(plugin.containsAll(factories), plugin.toString())
        assertEquals(emptyList(), violations(adapter, host(kotlin)))
    }

    @Test
    fun `resolves every reference of the plugin in the embeddable compiler that Gradle runs`() {
        val host = classpath("akki.compiler.embeddable")

        assertContains(host.map { it.fileName.toString() }, "kotlin-compiler-embeddable-$latestTestedKotlin.jar")
        assertEquals(emptyList(), violations(adapterOf(latestTestedKotlin), host))
    }

    @ParameterizedTest(name = "Kotlin {0}")
    @MethodSource("checkedKotlin")
    fun `reports every reference into the compiler once the compiler is missing`(kotlin: String) {
        val libraries = host(kotlin).filterNot { it.fileName.toString().startsWith("kotlin-compiler-") }

        val violations = violations(adapterOf(kotlin), libraries)

        val registrar = "io/akki/compiler/AkkiCompilerPluginRegistrar"
        assertContains(violations, "$registrar: org/jetbrains/kotlin/config/CompilerConfiguration is missing")
        assertContains(violations, "$registrar: org/jetbrains/kotlin/config/CommonConfigurationKeys is missing")
        assertTrue(violations.all { it.endsWith(" is missing") }, violations.toString())
        assertTrue(violations.none { it.startsWith("io/akki/compiler/compat/CompilerVersion") }, violations.toString())
    }

    @Test
    fun `checks the operations that the newest adapter takes from its delegates`() {
        val newest = adapters.last()
        val oldest = adapters.first().implementation.internalName

        val unshadowed = CompilerAbi(shapes, host(latestTestedKotlin)).use { it.violations(pluginWith(newest)) }

        assertTrue(operations.any { implementation(newest).forwards(it) }, "$newest forwards no operation")
        assertTrue(unshadowed.any { it.startsWith("$oldest: ") }, unshadowed.toString())
    }

    @Test
    fun `creates the delegate of every adapter but the oldest with the factory of the previous adapter`() {
        assertTrue(implementation(adapters.first()).members.keys.none { it.startsWith(DELEGATE) })
        for ((previous, adapter) in adapters.zipWithNext()) {
            val constructor = implementation(adapter).methodReferences.getValue("<init>()V")
            val factory = MemberReference(previous.factory.internalName, "<init>", "()V", false, false, false)

            assertContains(constructor, factory, adapter.toString())
        }
    }

    @Test
    fun `implements every operation in the oldest adapter and in the adapter of its latest change`() {
        val changes = changes()

        assertEquals(operations, changes.keys)
        for (operation in operations) {
            val changed = adapters.single { it.minVersion == changes[operation] }
            assertFalse(implementation(adapters.first()).forwards(operation), operation)
            assertFalse(implementation(changed).forwards(operation), "$changed forwards $operation")
        }
    }

    private fun violations(adapter: Adapter, host: List<Path>): List<String> =
        CompilerAbi(shapes, host).use { it.violations(pluginWith(adapter), shadowed(adapter)) }

    private fun pluginWith(adapter: Adapter): Set<String> {
        val unused = adapters - chain(adapter).toSet()
        return classes.keys.filterTo(mutableSetOf()) { name -> name in factories || unused.none { name in it } }
    }

    private fun chain(adapter: Adapter): List<Adapter> {
        val previous = adapters.getOrNull(adapters.indexOf(adapter) - 1)
        val delegates = implementation(adapter).members.keys.any { it.startsWith(DELEGATE) }
        return listOf(adapter) + if (delegates && previous != null) chain(previous) else emptyList()
    }

    private fun shadowed(adapter: Adapter): Map<String, Set<String>> {
        val chain = chain(adapter)
        return chain.withIndex().associate { (index, delegate) ->
            delegate.implementation.internalName to operations.filterTo(mutableSetOf()) { operation ->
                chain.take(index).any { !implementation(it).forwards(operation) }
            }
        }
    }

    private fun implementation(adapter: Adapter): ClassShape = shapes.getValue(adapter.implementation.internalName)

    private fun ClassShape.forwards(operation: String): Boolean {
        val references = methodReferences[operation].orEmpty().filterIsInstance<MemberReference>()
        return references.any { it.owner == name && it.isField && it.name.startsWith(DELEGATE) } &&
            references.any { it.owner == CONTRACT && it.signature == operation }
    }

    private fun changes(): Map<String, String> {
        val changes = mutableMapOf<String, String>()
        val visitor = object : ClassVisitor(Opcodes.ASM9) {
            override fun visitMethod(
                access: Int,
                name: String,
                descriptor: String,
                signature: String?,
                exceptions: Array<out String>?,
            ): MethodVisitor = object : MethodVisitor(Opcodes.ASM9) {
                override fun visitAnnotation(annotation: String, visible: Boolean): AnnotationVisitor? {
                    if (annotation != COMPAT_API) return null
                    return object : AnnotationVisitor(Opcodes.ASM9) {
                        override fun visit(key: String?, value: Any?) {
                            if (key == "since") changes["$name$descriptor"] = value as String
                        }
                    }
                }
            }
        }
        ClassReader(classes.getValue(CONTRACT)).accept(visitor, ClassReader.SKIP_CODE)
        return changes
    }

    private fun host(kotlin: String): List<Path> {
        val host = classpath("akki.compiler.host.$kotlin")
        val libraries = host.map { it.fileName.toString() }
        assertEquals(listOf("kotlin-compiler-$kotlin.jar"), libraries.filter { it.startsWith("kotlin-compiler-") })
        assertContains(libraries, "kotlin-stdlib-$kotlin.jar")
        return host
    }

    private operator fun Adapter.contains(className: String): Boolean =
        className.startsWith("${packageName.internalName}/")

    private companion object {
        const val CONTRACT: String = "io/akki/compiler/compat/CompilerCompat"

        const val COMPAT_API: String = "Lio/akki/compiler/compat/CompatApi;"

        const val DELEGATE: String = "\$\$delegate_"

        @JvmStatic
        fun checkedKotlin(): List<String> = io.akki.compiler.jar.checkedKotlin
    }
}
