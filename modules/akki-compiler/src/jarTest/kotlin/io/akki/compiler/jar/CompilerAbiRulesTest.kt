package io.akki.compiler.jar

import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.io.path.outputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.ACC_ABSTRACT
import org.objectweb.asm.Opcodes.ACC_INTERFACE
import org.objectweb.asm.Opcodes.ACC_PRIVATE
import org.objectweb.asm.Opcodes.ACC_PUBLIC
import org.objectweb.asm.Opcodes.ACC_STATIC
import org.objectweb.asm.Opcodes.ALOAD
import org.objectweb.asm.Opcodes.GETFIELD
import org.objectweb.asm.Opcodes.GETSTATIC
import org.objectweb.asm.Opcodes.INVOKEINTERFACE
import org.objectweb.asm.Opcodes.INVOKESTATIC
import org.objectweb.asm.Opcodes.INVOKEVIRTUAL
import org.objectweb.asm.Opcodes.RETURN
import org.objectweb.asm.Opcodes.V17

internal class CompilerAbiRulesTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `accepts members found through the class and interface hierarchy`() {
        val plugin = classFile("plugin/Good", superName = "host/Base", interfaces = listOf("host/Api")) {
            method("run", "()V")
            method("use", "()V") {
                call(INVOKEVIRTUAL, "host/Base", "greet", "()V")
                call(INVOKEVIRTUAL, "host/Base", "hashCode", "()I")
                call(INVOKESTATIC, "host/Base", "create", "()V")
                call(INVOKEINTERFACE, "host/Api", "ready", "()V", isInterface = true)
                visitFieldInsn(GETSTATIC, "host/Holder", "COUNT", "I")
                visitFieldInsn(GETFIELD, "host/Holder", "name", "Ljava/lang/String;")
            }
        }

        assertEquals(emptyList(), violations(plugin))
    }

    @Test
    fun `reports a missing owner and a missing member`() {
        val plugin = classFile("plugin/Missing") {
            method("use", "()V") {
                call(INVOKEVIRTUAL, "host/Gone", "greet", "()V")
                call(INVOKEVIRTUAL, "host/Base", "absent", "()V")
                visitFieldInsn(GETSTATIC, "host/Holder", "SIZE", "I")
            }
        }

        assertEquals(
            listOf(
                "plugin/Missing: host/Gone is missing",
                "plugin/Missing: host/Base.absent()V is missing",
                "plugin/Missing: host/Holder.SIZE:I is missing",
            ),
            violations(plugin),
        )
    }

    @Test
    fun `reports static members used as instance members and the reverse`() {
        val plugin = classFile("plugin/Static") {
            method("use", "()V") {
                call(INVOKESTATIC, "host/Base", "greet", "()V")
                call(INVOKEVIRTUAL, "host/Base", "create", "()V")
                visitFieldInsn(GETFIELD, "host/Holder", "COUNT", "I")
                visitFieldInsn(GETSTATIC, "host/Holder", "name", "Ljava/lang/String;")
            }
        }

        assertEquals(
            listOf(
                "plugin/Static: host/Base.greet()V is not static",
                "plugin/Static: host/Base.create()V is static",
                "plugin/Static: host/Holder.COUNT:I is static",
                "plugin/Static: host/Holder.name:Ljava/lang/String; is not static",
            ),
            violations(plugin),
        )
    }

    @Test
    fun `reports a class called as an interface and the reverse`() {
        val plugin = classFile("plugin/Kind") {
            method("use", "()V") {
                call(INVOKEINTERFACE, "host/Base", "greet", "()V", isInterface = true)
                call(INVOKEVIRTUAL, "host/Api", "ready", "()V")
            }
        }

        assertEquals(
            listOf(
                "plugin/Kind: host/Base.greet()V is called an interface, but its owner is a class",
                "plugin/Kind: host/Api.ready()V is called a class, but its owner is an interface",
            ),
            violations(plugin),
        )
    }

    @Test
    fun `reports private and package-private members`() {
        val plugin = classFile("plugin/Access") {
            method("use", "()V") {
                call(INVOKEVIRTUAL, "host/Base", "secret", "()V")
                call(INVOKEVIRTUAL, "host/Base", "local", "()V")
            }
        }

        assertEquals(
            listOf(
                "plugin/Access: host/Base.secret()V is private",
                "plugin/Access: host/Base.local()V is package-private",
            ),
            violations(plugin),
        )
    }

    @Test
    fun `reports abstract members that a concrete class leaves unimplemented`() {
        val plugin = classFile("plugin/Incomplete", superName = "host/Template", interfaces = listOf("host/Api")) +
            classFile("plugin/Abstract", access = ACC_PUBLIC or ACC_ABSTRACT, superName = "host/Template")

        assertEquals(
            listOf(
                "plugin/Incomplete: host/Template.build()V is not implemented",
                "plugin/Incomplete: host/Api.run()V is not implemented",
            ),
            violations(plugin),
        )
    }

    private fun violations(plugin: Map<String, ByteArray>): List<String> {
        val host = directory.resolve("host.jar")
        JarOutputStream(host.outputStream()).use { jar ->
            hostClasses().forEach { (name, bytes) ->
                jar.putNextEntry(JarEntry("$name.class"))
                jar.write(bytes)
                jar.closeEntry()
            }
        }
        val shapes = plugin.mapValues { (_, bytes) -> ClassShape.read(bytes, withReferences = true) }
        return CompilerAbi(shapes, listOf(host)).use(CompilerAbi::violations)
    }

    private fun hostClasses(): Map<String, ByteArray> =
        classFile("host/Base") {
            method("greet", "()V")
            method("create", "()V", ACC_PUBLIC or ACC_STATIC)
            method("secret", "()V", ACC_PRIVATE)
            method("local", "()V", 0)
        } + classFile("host/Api", access = ACC_PUBLIC or ACC_INTERFACE or ACC_ABSTRACT) {
            method("run", "()V", ACC_PUBLIC or ACC_ABSTRACT)
            method("ready", "()V")
        } + classFile("host/Template", access = ACC_PUBLIC or ACC_ABSTRACT) {
            method("build", "()V", ACC_PUBLIC or ACC_ABSTRACT)
        } + classFile("host/Holder") {
            visitField(ACC_PUBLIC or ACC_STATIC, "COUNT", "I", null, null).visitEnd()
            visitField(ACC_PUBLIC, "name", "Ljava/lang/String;", null, null).visitEnd()
        }

    private fun classFile(
        name: String,
        access: Int = ACC_PUBLIC,
        superName: String = "java/lang/Object",
        interfaces: List<String> = emptyList(),
        members: ClassWriter.() -> Unit = {},
    ): Map<String, ByteArray> {
        val writer = ClassWriter(ClassWriter.COMPUTE_MAXS)
        writer.visit(V17, access, name, null, superName, interfaces.toTypedArray())
        writer.members()
        writer.visitEnd()
        return mapOf(name to writer.toByteArray())
    }

    private fun ClassWriter.method(
        name: String,
        descriptor: String,
        access: Int = ACC_PUBLIC,
        code: MethodVisitor.() -> Unit = {},
    ) {
        val method = visitMethod(access, name, descriptor, null, null)
        if (access and ACC_ABSTRACT == 0) {
            method.visitCode()
            method.code()
            method.visitInsn(RETURN)
            method.visitMaxs(0, 0)
        }
        method.visitEnd()
    }

    private fun MethodVisitor.call(
        opcode: Int,
        owner: String,
        name: String,
        descriptor: String,
        isInterface: Boolean = false,
    ) {
        if (opcode != INVOKESTATIC) visitVarInsn(ALOAD, 0)
        visitMethodInsn(opcode, owner, name, descriptor, isInterface)
    }
}
