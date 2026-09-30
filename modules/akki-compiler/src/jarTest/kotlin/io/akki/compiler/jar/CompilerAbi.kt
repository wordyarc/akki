package io.akki.compiler.jar

import java.nio.file.Path
import java.util.jar.JarFile
import org.objectweb.asm.Opcodes

internal class CompilerAbi(private val plugin: Map<String, ClassShape>, host: List<Path>) : AutoCloseable {
    private val jars = host.map { JarFile(it.toFile()) }

    private val shapes = HashMap<String, ClassShape?>(plugin)

    fun violations(): List<String> = plugin.values.flatMap { shape ->
        val broken = shape.references.filterNot { it.owner.isPlatform }.mapNotNull { shape.violation(it) }
        (broken + shape.unimplemented()).map { "${shape.name}: $it" }
    }

    override fun close() = jars.forEach(JarFile::close)

    private fun ClassShape.violation(reference: Reference): String? {
        val owner = find(reference.owner) ?: return "${reference.owner} is missing"
        if (reference !is MemberReference) return null
        val member = "${reference.owner}.${reference.signature}"
        if (reference.inInterface != null && reference.inInterface != owner.isInterface) {
            return "$member is called ${kind(reference.inInterface)}, but its owner is ${kind(owner.isInterface)}"
        }
        val declaring = owner.declaring(reference) ?: return "$member is missing"
        val access = declaring.members.getValue(reference.signature)
        return when {
            (access and Opcodes.ACC_STATIC != 0) != reference.isStatic ->
                "$member is ${if (reference.isStatic) "not static" else "static"}"
            access and Opcodes.ACC_PRIVATE != 0 && declaring !== this -> "$member is private"
            access and visible == 0 && declaring.packageName != packageName -> "$member is package-private"
            else -> null
        }
    }

    private fun ClassShape.declaring(reference: MemberReference): ClassShape? {
        val candidates = if (reference.name == "<init>") listOf(this) else ancestry()
        return candidates.firstOrNull { reference.signature in it.members }
    }

    private fun ClassShape.unimplemented(): List<String> {
        if (isInterface || isAbstract) return emptyList()
        val ancestry = ancestry()
        val lineage = generateSequence(this) { it.superName?.let(::find) }.toSet()
        val implemented = ancestry.filter { it in lineage || it.isInterface }
            .flatMap { shape -> shape.members.filterValues { it and notImplementing == 0 }.keys }
            .toSet()
        return ancestry.flatMap { shape ->
            shape.members.filterValues { it and Opcodes.ACC_ABSTRACT != 0 }.keys
                .filterNot(implemented::contains)
                .map { "${shape.name}.$it is not implemented" }
        }
    }

    private fun ClassShape.ancestry(): List<ClassShape> {
        val ancestry = linkedSetOf(this)
        val pending = ArrayDeque(listOf(this))
        while (pending.isNotEmpty()) {
            val shape = pending.removeFirst()
            val supertypes = (listOfNotNull(shape.superName) + shape.interfaces).mapNotNull(::find)
            pending += supertypes.filter(ancestry::add)
        }
        return ancestry.toList()
    }

    private fun find(name: String): ClassShape? {
        if (name !in shapes) shapes[name] = hosted(name) ?: platform(name)
        return shapes[name]
    }

    private fun hosted(name: String): ClassShape? = jars.firstNotNullOfOrNull { jar ->
        jar.getJarEntry("$name.class")?.let { entry -> jar.getInputStream(entry).use { it.readBytes() } }
    }?.let { ClassShape.read(it, withReferences = false) }

    private fun platform(name: String): ClassShape? =
        ClassLoader.getPlatformClassLoader().getResourceAsStream("$name.class")
            ?.use { ClassShape.read(it.readBytes(), withReferences = false) }

    private fun kind(isInterface: Boolean): String = if (isInterface) "an interface" else "a class"

    private val String.isPlatform: Boolean
        get() = platformPackages.any(::startsWith)

    private companion object {
        const val visible: Int = Opcodes.ACC_PUBLIC or Opcodes.ACC_PROTECTED or Opcodes.ACC_PRIVATE

        const val notImplementing: Int = Opcodes.ACC_ABSTRACT or Opcodes.ACC_STATIC or Opcodes.ACC_PRIVATE

        val platformPackages = listOf("java/", "javax/", "jdk/", "sun/")
    }
}
