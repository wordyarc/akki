package io.akki.compiler.jar

import java.nio.file.Path
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

internal const val COMPAT_API: String = "Lio/akki/compiler/compat/CompatApi;"

internal const val COMPAT_API_CONTAINER: String = "Lio/akki/compiler/compat/CompatApi\$Container;"

internal val pluginClasses: Map<String, ByteArray> = compilerJar.entries()
    .filter { (name, _) -> name.endsWith(".class") }
    .associate { (name, bytes) -> name.removeSuffix(".class") to bytes }

internal val pluginShapes: Map<String, ClassShape> =
    pluginClasses.mapValues { (_, bytes) -> ClassShape.read(bytes, withReferences = true) }

internal val factories: Set<String> = adapters.mapTo(mutableSetOf()) { it.factory.internalName }

internal val operations: Set<String> =
    pluginShapes.getValue(CONTRACT).members.filterValues { it and Opcodes.ACC_ABSTRACT != 0 }.keys

internal fun implementation(adapter: Adapter): ClassShape = pluginShapes.getValue(adapter.implementation.internalName)

internal fun chain(adapter: Adapter): List<Adapter> {
    val previous = adapters.getOrNull(adapters.indexOf(adapter) - 1)
    val delegates = implementation(adapter).delegates
    return listOf(adapter) + if (delegates && previous != null) chain(previous) else emptyList()
}

internal fun pluginWith(adapter: Adapter): Set<String> {
    val unused = adapters - chain(adapter).toSet()
    return pluginClasses.keys.filterTo(mutableSetOf()) { name -> name in factories || unused.none { name in it } }
}

internal fun violations(adapter: Adapter, host: List<Path>): List<String> =
    CompilerAbi(pluginShapes, host).use { it.violations(pluginWith(adapter), shadowed(adapter)) }

internal fun compatApiChanges(contract: ByteArray): Map<String, Set<String>> {
    val changes = mutableMapOf<String, MutableSet<String>>()
    val visitor = object : ClassVisitor(Opcodes.ASM9) {
        override fun visitMethod(
            access: Int,
            name: String,
            descriptor: String,
            signature: String?,
            exceptions: Array<out String>?,
        ): MethodVisitor = object : MethodVisitor(Opcodes.ASM9) {
            override fun visitAnnotation(annotation: String, visible: Boolean): AnnotationVisitor? =
                if (annotation == COMPAT_API || annotation == COMPAT_API_CONTAINER) {
                    Since(changes.getOrPut("$name$descriptor", ::mutableSetOf))
                } else {
                    null
                }
        }
    }
    ClassReader(contract).accept(visitor, ClassReader.SKIP_CODE)
    return changes
}

private fun shadowed(adapter: Adapter): Map<String, Set<String>> {
    val chain = chain(adapter)
    return chain.withIndex().associate { (index, delegate) ->
        delegate.implementation.internalName to operations.filterTo(mutableSetOf()) { operation ->
            chain.take(index).any { !implementation(it).callsDelegate(operation) }
        }
    }
}

private operator fun Adapter.contains(className: String): Boolean =
    className.startsWith("${packageName.internalName}/")

private class Since(private val since: MutableSet<String>) : AnnotationVisitor(Opcodes.ASM9) {
    override fun visit(name: String?, value: Any?) {
        if (name == "since") since += value as String
    }

    override fun visitArray(name: String?): AnnotationVisitor = this

    override fun visitAnnotation(name: String?, descriptor: String): AnnotationVisitor = this
}
