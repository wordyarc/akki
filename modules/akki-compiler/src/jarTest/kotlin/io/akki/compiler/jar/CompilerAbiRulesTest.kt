package io.akki.compiler.jar

import java.lang.reflect.InvocationTargetException
import java.net.URLClassLoader
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.io.path.outputStream
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.ACC_ABSTRACT
import org.objectweb.asm.Opcodes.ACC_FINAL
import org.objectweb.asm.Opcodes.ACC_INTERFACE
import org.objectweb.asm.Opcodes.ACC_PRIVATE
import org.objectweb.asm.Opcodes.ACC_PROTECTED
import org.objectweb.asm.Opcodes.ACC_PUBLIC
import org.objectweb.asm.Opcodes.ACC_STATIC
import org.objectweb.asm.Opcodes.ALOAD
import org.objectweb.asm.Opcodes.GETFIELD
import org.objectweb.asm.Opcodes.GETSTATIC
import org.objectweb.asm.Opcodes.INVOKEINTERFACE
import org.objectweb.asm.Opcodes.INVOKESPECIAL
import org.objectweb.asm.Opcodes.INVOKESTATIC
import org.objectweb.asm.Opcodes.INVOKEVIRTUAL
import org.objectweb.asm.Opcodes.NEW
import org.objectweb.asm.Opcodes.POP
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

    @Test
    fun `reports a concrete method redeclared abstract by a nearer superclass before its invocation fails`() {
        val grandparent = classFile("host/Grandparent") {
            constructor()
            method("run", "()V")
        }
        val oldHost = grandparent + classFile("host/Parent", ACC_PUBLIC or ACC_ABSTRACT, "host/Grandparent") {
            constructor("host/Grandparent")
        }
        val newHost = grandparent + classFile("host/Parent", ACC_PUBLIC or ACC_ABSTRACT, "host/Grandparent") {
            constructor("host/Grandparent")
            method("run", "()V", ACC_PUBLIC or ACC_ABSTRACT)
        }
        val plugin = classFile("plugin/Inherited", superName = "host/Parent") {
            constructor("host/Parent")
        } + classFile("plugin/Overridden", superName = "host/Parent") {
            constructor("host/Parent")
            method("run", "()V")
        }

        assertEquals(emptyList(), violations(plugin, host = oldHost))
        invokeRun("plugin.Inherited", plugin, oldHost)
        assertEquals(
            listOf("plugin/Inherited: host/Parent.run()V is not implemented"),
            violations(plugin, host = newHost),
        )
        assertEquals(emptyList(), linkageFailures(plugin.keys, listOf(jar("plugin", plugin), jar("host", newHost))))
        val failure = assertFailsWith<InvocationTargetException> { invokeRun("plugin.Inherited", plugin, newHost) }
        assertIs<AbstractMethodError>(failure.cause)
        invokeRun("plugin.Overridden", plugin, newHost)
    }

    @Test
    fun `reports a default method redeclared abstract by a more specific interface`() {
        val api = classFile("host/Api", ACC_PUBLIC or ACC_INTERFACE or ACC_ABSTRACT) {
            method("run", "()V")
        }
        val oldHost = api + classFile(
            "host/Refined", ACC_PUBLIC or ACC_INTERFACE or ACC_ABSTRACT, interfaces = listOf("host/Api"),
        )
        val newHost = api + classFile(
            "host/Refined", ACC_PUBLIC or ACC_INTERFACE or ACC_ABSTRACT, interfaces = listOf("host/Api"),
        ) {
            method("run", "()V", ACC_PUBLIC or ACC_ABSTRACT)
        }
        val plugin = classFile("plugin/Inherited", interfaces = listOf("host/Api", "host/Refined")) {
            constructor()
        }

        assertEquals(emptyList(), violations(plugin, host = oldHost))
        invokeRun("plugin.Inherited", plugin, oldHost)
        assertEquals(
            listOf("plugin/Inherited: host/Refined.run()V is not implemented"),
            violations(plugin, host = newHost),
        )
        val failure = assertFailsWith<InvocationTargetException> { invokeRun("plugin.Inherited", plugin, newHost) }
        assertIs<AbstractMethodError>(failure.cause)
    }

    @Test
    fun `prefers class declarations to interface defaults`() {
        val api = classFile("host/Api", ACC_PUBLIC or ACC_INTERFACE or ACC_ABSTRACT) {
            method("run", "()V")
        }
        val host = api + classFile("host/Parent", ACC_PUBLIC or ACC_ABSTRACT) {
            constructor()
            method("run", "()V", ACC_PUBLIC or ACC_ABSTRACT)
        }
        val plugin = classFile("plugin/Inherited", superName = "host/Parent", interfaces = listOf("host/Api")) {
            constructor("host/Parent")
        }

        assertEquals(
            listOf("plugin/Inherited: host/Parent.run()V is not implemented"),
            violations(plugin, host = host),
        )
        val failure = assertFailsWith<InvocationTargetException> { invokeRun("plugin.Inherited", plugin, host) }
        assertIs<AbstractMethodError>(failure.cause)
    }

    @Test
    fun `accepts the most specific default and a class implementation of an abstract interface method`() {
        val host = classFile("host/Api", ACC_PUBLIC or ACC_INTERFACE or ACC_ABSTRACT) {
            method("run", "()V", ACC_PUBLIC or ACC_ABSTRACT)
        } + classFile("host/Default", ACC_PUBLIC or ACC_INTERFACE or ACC_ABSTRACT, interfaces = listOf("host/Api")) {
            method("run", "()V")
        } + classFile("host/Parent") {
            constructor()
            method("run", "()V")
        }
        val plugin = classFile("plugin/Default", interfaces = listOf("host/Api", "host/Default")) {
            constructor()
        } + classFile("plugin/Inherited", superName = "host/Parent", interfaces = listOf("host/Api")) {
            constructor("host/Parent")
        }

        assertEquals(emptyList(), violations(plugin, host = host))
        invokeRun("plugin.Default", plugin, host)
        invokeRun("plugin.Inherited", plugin, host)
    }

    @Test
    fun `reports protected members outside subclasses and package-private classes of another package`() {
        val plugin = classFile("plugin/Outsider") {
            method("use", "()V") {
                call(INVOKEVIRTUAL, "host/Base", "guarded", "()V")
                visitTypeInsn(NEW, "host/Hidden")
                visitInsn(POP)
            }
        } + classFile("plugin/Subclass", superName = "host/Base") {
            method("use", "()V") {
                call(INVOKEVIRTUAL, "host/Base", "guarded", "()V")
            }
        }

        assertEquals(
            listOf(
                "plugin/Outsider: host/Base.guarded()V is protected",
                "plugin/Outsider: host/Hidden is package-private",
            ),
            violations(plugin),
        )
    }

    @Test
    fun `reports supertypes that cannot be extended or implemented`() {
        val plugin = classFile("plugin/ExtendsFinal", superName = "host/Closed") +
            classFile("plugin/OverridesFinal", superName = "host/Opened") { method("locked", "()V") } +
            classFile("plugin/ExtendsInterface", superName = "host/Marker") +
            classFile("plugin/ImplementsClass", interfaces = listOf("host/Holder"))

        assertEquals(
            listOf(
                "plugin/ExtendsFinal: host/Closed is final",
                "plugin/OverridesFinal: host/Opened.locked()V is final",
                "plugin/ExtendsInterface: host/Marker is extended, but it is an interface",
                "plugin/ImplementsClass: host/Holder is implemented, but it is a class",
            ),
            violations(plugin),
        )
    }

    @Test
    fun `leaves to the JVM a value whose class no longer extends the parameter class`() {
        val plugin = passing("plugin/Linked", "host/Derived") + passing("plugin/Unlinked", "host/Detached")

        val failure = linkageFailures(plugin.keys, listOf(jar("plugin", plugin), jar("host", hostClasses()))).single()

        assertEquals(emptyList(), violations(plugin))
        assertTrue(failure.startsWith("plugin/Unlinked: java.lang.VerifyError"), failure)
        assertContains(failure, "'host/Detached' (current frame, stack[0]) is not assignable to 'host/Base'")
    }

    @Test
    fun `skips the methods that the caller excludes`() {
        val plugin = classFile("plugin/Delegate") {
            method("unused", "()V") {
                call(INVOKEVIRTUAL, "host/Base", "absent", "()V")
            }
            method("used", "()V") {
                call(INVOKEVIRTUAL, "host/Base", "missing", "()V")
            }
        }

        assertEquals(
            listOf("plugin/Delegate: host/Base.missing()V is missing"),
            violations(plugin, skipped = mapOf("plugin/Delegate" to setOf("unused()V"))),
        )
    }

    @Test
    fun `recognizes a forwarding to the delegate field of either form`() {
        for (field in listOf("\$\$delegate_0", "delegate")) {
            val adapter = adapter("plugin/Adapter", field) {
                method("forwarded", "()V") {
                    forward("plugin/Adapter", field, "forwarded")
                }
                method("fallback", "()V") {
                    forward("plugin/Adapter", field, "fallback")
                    call(INVOKEVIRTUAL, "host/Base", "greet", "()V")
                }
                method("overridden", "()V") {
                    call(INVOKEVIRTUAL, "host/Base", "greet", "()V")
                }
            }

            assertTrue(adapter.forwards("forwarded()V"), field)
            assertTrue(adapter.callsDelegate("fallback()V") && !adapter.forwards("fallback()V"), field)
            assertFalse(adapter.callsDelegate("overridden()V") || adapter.forwards("overridden()V"), field)
        }
    }

    @Test
    fun `reads every change of an operation from its annotation and from the container of repeated ones`() {
        val contract = classFile(CONTRACT, access = ACC_PUBLIC or ACC_INTERFACE or ACC_ABSTRACT) {
            operation("once", COMPAT_API) { change("2.4.0-Beta1") }
            operation("twice", COMPAT_API_CONTAINER) {
                val changes = visitArray("value")
                for (since in listOf("2.4.20-Beta1", "2.5.0-Beta1")) {
                    changes.visitAnnotation(null, COMPAT_API).apply { change(since) }.visitEnd()
                }
                changes.visitEnd()
            }
            method("unchanged", "()V", ACC_PUBLIC or ACC_ABSTRACT)
        }.getValue(CONTRACT)

        assertEquals(
            mapOf("once()V" to setOf("2.4.0-Beta1"), "twice()V" to setOf("2.4.20-Beta1", "2.5.0-Beta1")),
            compatApiChanges(contract),
        )
    }

    private fun ClassWriter.operation(name: String, annotation: String, values: AnnotationVisitor.() -> Unit) {
        val method = visitMethod(ACC_PUBLIC or ACC_ABSTRACT, name, "()V", null, null)
        method.visitAnnotation(annotation, true).apply(values).visitEnd()
        method.visitEnd()
    }

    private fun AnnotationVisitor.change(since: String) {
        visit("since", since)
        visit("change", "the compiler API changed in Kotlin $since")
    }

    private fun adapter(name: String, field: String, members: ClassWriter.() -> Unit): ClassShape {
        val bytes = classFile(name, interfaces = listOf(CONTRACT)) {
            visitField(ACC_PRIVATE or ACC_FINAL, field, "L$CONTRACT;", null, null).visitEnd()
            members()
        }.getValue(name)
        return ClassShape.read(bytes, withReferences = true)
    }

    private fun MethodVisitor.forward(owner: String, field: String, operation: String) {
        visitVarInsn(ALOAD, 0)
        visitFieldInsn(GETFIELD, owner, field, "L$CONTRACT;")
        visitMethodInsn(INVOKEINTERFACE, CONTRACT, operation, "()V", true)
    }

    private fun passing(name: String, argument: String): Map<String, ByteArray> = classFile(name) {
        method("pass", "(L$argument;)V") {
            visitVarInsn(ALOAD, 1)
            call(INVOKESTATIC, "host/Base", "accept", "(Lhost/Base;)V")
        }
    }

    private fun violations(
        plugin: Map<String, ByteArray>,
        skipped: Map<String, Set<String>> = emptyMap(),
        host: Map<String, ByteArray> = hostClasses(),
    ): List<String> {
        val shapes = plugin.mapValues { (_, bytes) -> ClassShape.read(bytes, withReferences = true) }
        return CompilerAbi(shapes, listOf(jar("host", host))).use { it.violations(skipped = skipped) }
    }

    private fun invokeRun(name: String, plugin: Map<String, ByteArray>, host: Map<String, ByteArray>) {
        val classpath = listOf(jar("plugin", plugin), jar("host", host)).map { it.toUri().toURL() }.toTypedArray()
        URLClassLoader(classpath, ClassLoader.getPlatformClassLoader()).use { loader ->
            val type = loader.loadClass(name)
            type.getMethod("run").invoke(type.getConstructor().newInstance())
        }
    }

    private fun jar(name: String, classes: Map<String, ByteArray>): Path {
        val path = directory.resolve("$name.jar")
        JarOutputStream(path.outputStream()).use { output ->
            classes.forEach { (className, bytes) ->
                output.putNextEntry(JarEntry("$className.class"))
                output.write(bytes)
                output.closeEntry()
            }
        }
        return path
    }

    private fun hostClasses(): Map<String, ByteArray> = listOf(
        classFile("host/Base") {
            method("greet", "()V")
            method("create", "()V", ACC_PUBLIC or ACC_STATIC)
            method("accept", "(Lhost/Base;)V", ACC_PUBLIC or ACC_STATIC)
            method("secret", "()V", ACC_PRIVATE)
            method("local", "()V", 0)
            method("guarded", "()V", ACC_PROTECTED)
        },
        classFile("host/Derived", superName = "host/Base"),
        classFile("host/Detached"),
        classFile("host/Hidden", access = 0) {
            method("<init>", "()V")
        },
        classFile("host/Closed", access = ACC_PUBLIC or ACC_FINAL),
        classFile("host/Opened") {
            method("locked", "()V", ACC_PUBLIC or ACC_FINAL)
        },
        classFile("host/Marker", access = ACC_PUBLIC or ACC_INTERFACE or ACC_ABSTRACT),
        classFile("host/Api", access = ACC_PUBLIC or ACC_INTERFACE or ACC_ABSTRACT) {
            method("run", "()V", ACC_PUBLIC or ACC_ABSTRACT)
            method("ready", "()V")
        },
        classFile("host/Template", access = ACC_PUBLIC or ACC_ABSTRACT) {
            method("build", "()V", ACC_PUBLIC or ACC_ABSTRACT)
        },
        classFile("host/Holder") {
            visitField(ACC_PUBLIC or ACC_STATIC, "COUNT", "I", null, null).visitEnd()
            visitField(ACC_PUBLIC, "name", "Ljava/lang/String;", null, null).visitEnd()
        },
    ).reduce(Map<String, ByteArray>::plus)

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

    private fun ClassWriter.constructor(superName: String = "java/lang/Object") {
        method("<init>", "()V") {
            call(INVOKESPECIAL, superName, "<init>", "()V")
        }
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
