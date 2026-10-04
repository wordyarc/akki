package io.akki.compiler.jar

import java.nio.file.Path
import java.util.jar.JarFile
import org.objectweb.asm.Opcodes

internal class CompilerAbi(private val plugin: Map<String, ClassShape>, host: List<Path>) : AutoCloseable {
    private val jars = host.map { JarFile(it.toFile()) }

    private val shapes = HashMap<String, ClassShape?>(plugin)

    fun violations(
        checked: Set<String> = plugin.keys,
        skipped: Map<String, Set<String>> = emptyMap(),
    ): List<String> = checked.map(plugin::getValue).flatMap { shape ->
        val references = shape.references(skipped[shape.name].orEmpty()).filterNot { it.owner.isPlatform }
        val broken = references.mapNotNull { shape.violation(it) }
        (broken + shape.inheritance() + shape.unimplemented()).map { "${shape.name}: $it" }
    }.distinct()

    override fun close() = jars.forEach(JarFile::close)

    private fun ClassShape.violation(reference: Reference): String? {
        val owner = find(reference.owner) ?: return "${reference.owner} is missing"
        if (!owner.isPublic && owner.packageName != packageName) return "${reference.owner} is package-private"
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
            declaring.packageName == packageName -> null
            access and visible == 0 -> "$member is package-private"
            access and Opcodes.ACC_PROTECTED != 0 && declaring !in lineage() -> "$member is protected"
            else -> null
        }
    }

    private fun ClassShape.declaring(reference: MemberReference): ClassShape? {
        val candidates = if (reference.name == "<init>") listOf(this) else ancestry()
        return candidates.firstOrNull { reference.signature in it.members }
    }

    private fun ClassShape.inheritance(): List<String> {
        val superclass = superName?.let(::find)
        val implemented = interfaces.mapNotNull(::find).filterNot(ClassShape::isInterface)
        val ancestry = ancestry().drop(1)
        val overridden = members.filter { (signature, access) -> '(' in signature && access and notInherited == 0 }
            .keys
            .filterNot { it.startsWith('<') }
            .flatMap { signature ->
                ancestry.filter { it.overridesFinal(signature, packageName) }.map { "${it.name}.$signature is final" }
            }
        return listOfNotNull(
            superclass?.takeIf(ClassShape::isFinal)?.let { "${it.name} is final" },
            superclass?.takeIf(ClassShape::isInterface)?.let { "${it.name} is extended, but it is an interface" },
        ) + implemented.map { "${it.name} is implemented, but it is a class" } + overridden
    }

    private fun ClassShape.overridesFinal(signature: String, overridingPackage: String): Boolean {
        val access = members[signature] ?: return false
        return access and Opcodes.ACC_FINAL != 0 && access and notInherited == 0 &&
            (access and visible != 0 || packageName == overridingPackage)
    }

    private fun ClassShape.unimplemented(): List<String> {
        if (isInterface || isAbstract) return emptyList()
        val ancestry = ancestry()
        val lineage = lineage()
        val interfaces = ancestry.filter(ClassShape::isInterface)
        val implemented = HashMap<String, Boolean>()
        return ancestry.flatMap { shape ->
            shape.members.filterValues { it and Opcodes.ACC_ABSTRACT != 0 }.keys
                .filterNot { signature ->
                    implemented.getOrPut(signature) { hasImplementation(signature, lineage, interfaces) }
                }
                .map { "${shape.name}.$it is not implemented" }
        }
    }

    private fun ClassShape.hasImplementation(
        signature: String,
        lineage: List<ClassShape>,
        interfaces: List<ClassShape>,
    ): Boolean {
        val classMethod = lineage.firstNotNullOfOrNull { shape ->
            shape.members[signature]?.takeIf { access ->
                access and notInherited == 0 && (access and visible != 0 || shape.packageName == packageName)
            }
        }
        if (classMethod != null) return classMethod and Opcodes.ACC_ABSTRACT == 0

        val declaring = interfaces.filter { shape ->
            shape.members[signature]?.let { it and notInherited == 0 } == true
        }
        val mostSpecific = declaring.filter { candidate ->
            declaring.none { it !== candidate && candidate in it.ancestry() }
        }
        return mostSpecific.count { it.members.getValue(signature) and Opcodes.ACC_ABSTRACT == 0 } == 1
    }

    private fun ClassShape.lineage(): List<ClassShape> = generateSequence(this) { it.superName?.let(::find) }.toList()

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

        const val notInherited: Int = Opcodes.ACC_STATIC or Opcodes.ACC_PRIVATE

        val platformPackages = listOf("java/", "javax/", "jdk/", "sun/")
    }
}
