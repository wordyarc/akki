package io.akki.compiler.jar

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.Handle
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type

internal sealed interface Reference {
    val owner: String
}

internal data class TypeReference(override val owner: String) : Reference

internal data class MemberReference(
    override val owner: String,
    val name: String,
    val descriptor: String,
    val isField: Boolean,
    val isStatic: Boolean,
    val inInterface: Boolean?,
) : Reference {
    val signature: String
        get() = if (isField) "$name:$descriptor" else "$name$descriptor"
}

internal class ClassShape(
    val name: String,
    val access: Int,
    val superName: String?,
    val interfaces: List<String>,
    val members: Map<String, Int>,
    val references: Set<Reference>,
) {
    val isInterface: Boolean
        get() = access and Opcodes.ACC_INTERFACE != 0

    val isAbstract: Boolean
        get() = access and Opcodes.ACC_ABSTRACT != 0

    val packageName: String
        get() = name.substringBeforeLast('/', "")

    companion object {
        fun read(bytes: ByteArray, withReferences: Boolean): ClassShape {
            val reader = ClassReader(bytes)
            val collector = Collector(withReferences)
            reader.accept(collector, if (withReferences) ClassReader.SKIP_DEBUG else ClassReader.SKIP_CODE)
            val supertypes = listOfNotNull(reader.superName) + reader.interfaces
            return ClassShape(
                name = reader.className,
                access = reader.access,
                superName = reader.superName,
                interfaces = reader.interfaces.toList(),
                members = collector.members,
                references = collector.references + supertypes.map(::TypeReference),
            )
        }
    }
}

private class Collector(private val withReferences: Boolean) : ClassVisitor(Opcodes.ASM9) {
    val members = mutableMapOf<String, Int>()

    val references = mutableSetOf<Reference>()

    override fun visitField(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        value: Any?,
    ): FieldVisitor? {
        members["$name:$descriptor"] = access
        return null
    }

    override fun visitMethod(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        exceptions: Array<out String>?,
    ): MethodVisitor? {
        members["$name$descriptor"] = access
        return if (withReferences) Instructions(references) else null
    }
}

private class Instructions(private val references: MutableSet<Reference>) : MethodVisitor(Opcodes.ASM9) {
    override fun visitTypeInsn(opcode: Int, type: String) = refer(Type.getObjectType(type))

    override fun visitMultiANewArrayInsn(descriptor: String, dimensions: Int) = refer(Type.getType(descriptor))

    override fun visitTryCatchBlock(start: Label, end: Label, handler: Label, type: String?) {
        if (type != null) refer(Type.getObjectType(type))
    }

    override fun visitLdcInsn(value: Any) = constant(value)

    override fun visitFieldInsn(opcode: Int, owner: String, name: String, descriptor: String) {
        val isStatic = opcode == Opcodes.GETSTATIC || opcode == Opcodes.PUTSTATIC
        references += MemberReference(owner, name, descriptor, isField = true, isStatic, inInterface = null)
    }

    override fun visitMethodInsn(opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean) {
        if (owner.startsWith('[')) return refer(Type.getObjectType(owner))
        val isStatic = opcode == Opcodes.INVOKESTATIC
        references += MemberReference(owner, name, descriptor, isField = false, isStatic, isInterface)
    }

    override fun visitInvokeDynamicInsn(name: String, descriptor: String, bootstrap: Handle, vararg arguments: Any) {
        refer(Type.getReturnType(descriptor))
        constant(bootstrap)
        arguments.forEach(::constant)
    }

    private fun constant(value: Any) {
        when (value) {
            is Type -> if (value.sort != Type.METHOD) refer(value)
            is Handle -> references += MemberReference(
                owner = value.owner,
                name = value.name,
                descriptor = value.desc,
                isField = value.tag <= Opcodes.H_PUTSTATIC,
                isStatic = value.tag in staticHandles,
                inInterface = value.isInterface.takeIf { value.tag > Opcodes.H_PUTSTATIC },
            )
        }
    }

    private fun refer(type: Type) {
        when (type.sort) {
            Type.ARRAY -> refer(type.elementType)
            Type.OBJECT -> references += TypeReference(type.internalName)
        }
    }

    private companion object {
        val staticHandles = setOf(Opcodes.H_GETSTATIC, Opcodes.H_PUTSTATIC, Opcodes.H_INVOKESTATIC)
    }
}
