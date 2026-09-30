package io.akki.compiler.compat

import io.akki.compiler.compat.fixture.AbsentFactory
import io.akki.compiler.compat.fixture.BrokenFactory
import io.akki.compiler.compat.fixture.CrashingFactory
import io.akki.compiler.compat.fixture.CurrentAdapter
import io.akki.compiler.compat.fixture.CurrentFactory
import io.akki.compiler.compat.fixture.ExhaustedFactory
import io.akki.compiler.compat.fixture.FailingFactory
import io.akki.compiler.compat.fixture.ForeignFactory
import io.akki.compiler.compat.fixture.FutureAdapter
import io.akki.compiler.compat.fixture.FutureFactory
import io.akki.compiler.compat.fixture.MalformedFactory
import io.akki.compiler.compat.fixture.ParameterizedFactory
import io.akki.compiler.compat.fixture.PrivateFactory
import io.akki.compiler.compat.fixture.RivalFactory
import io.akki.compiler.compat.fixture.UnlinkedFactory
import java.nio.file.Path
import java.util.ServiceConfigurationError
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.jetbrains.kotlin.config.KotlinCompilerVersion
import org.junit.jupiter.api.io.TempDir

internal class CompilerCompatLoaderTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `creates the selected adapter without loading the others`() {
        val classLoader = AdapterClassLoader.of(
            directory,
            FutureFactory::class,
            CurrentFactory::class,
            forbidden = setOf(FutureAdapter::class),
        )

        val compat = CompilerCompatLoader.load("2.4.0", classLoader)

        assertEquals(CurrentAdapter::class.java.name, compat.javaClass.name)
        assertSame(classLoader, compat.javaClass.classLoader)
        assertContains(classLoader.requested, FutureFactory::class.java.name)
        assertContains(classLoader.requested, CurrentFactory::class.java.name)
        assertContains(classLoader.requested, CurrentAdapter::class.java.name)
        assertTrue(FutureAdapter::class.java.name !in classLoader.requested, classLoader.requested.toString())
    }

    @Test
    fun `fails on the selected adapter that must not be loaded`() {
        val classLoader = AdapterClassLoader.of(
            directory,
            FutureFactory::class,
            CurrentFactory::class,
            forbidden = setOf(FutureAdapter::class),
        )

        val failure = assertFailsWith<CompatLoadException> { CompilerCompatLoader.load("9.1.0", classLoader) }

        assertContains(classLoader.requested, FutureAdapter::class.java.name)
        assertIs<ClassFormatError>(failure.cause)
        assertContains(failure.message.orEmpty(), "The compiler adapter for Kotlin 9.0.0 created by")
        assertContains(failure.message.orEmpty(), "does not link against Kotlin 9.1.0.")
        assertTrue(CurrentAdapter::class.java.name !in classLoader.requested, "fell back to the previous adapter")
    }

    @Test
    fun `fails on the selected adapter that is absent`() {
        val failure = failure("9.0.0", AbsentFactory::class, CurrentFactory::class)

        assertIs<ClassNotFoundException>(failure.cause)
        assertContains(failure.message.orEmpty(), "The compiler adapter for Kotlin 9.0.0 cannot be created by")
        assertContains(failure.message.orEmpty(), AbsentFactory::class.java.name)
    }

    @Test
    fun `fails on the adapter that does not implement the contract`() {
        assertIs<ClassCastException>(failure("9.0.0", ForeignFactory::class).cause)
    }

    @Test
    fun `fails on the adapter without an accessible constructor`() {
        assertIs<NoSuchMethodException>(failure("9.0.0", ParameterizedFactory::class).cause)
        assertIs<IllegalAccessException>(failure("9.0.0", PrivateFactory::class).cause)
    }

    @Test
    fun `reports the exception of the adapter constructor as the cause`() {
        val cause = assertIs<IllegalStateException>(failure("9.0.0", FailingFactory::class).cause)

        assertEquals("adapter constructor failed", cause.message)
    }

    @Test
    fun `reports a linkage error of the adapter without another attempt`() {
        val classLoader = AdapterClassLoader.of(directory, BrokenFactory::class, CurrentFactory::class)

        val failure = assertFailsWith<CompatLoadException> { CompilerCompatLoader.load("9.0.0", classLoader) }

        assertIs<NoSuchMethodError>(failure.cause)
        assertContains(failure.message.orEmpty(), "does not link against Kotlin 9.0.0.")
        assertTrue(CurrentAdapter::class.java.name !in classLoader.requested, "fell back to the previous adapter")
    }

    @Test
    fun `rethrows a virtual machine error of the adapter`() {
        val classLoader = AdapterClassLoader.of(directory, ExhaustedFactory::class)

        val failure = assertFailsWith<InternalError> { CompilerCompatLoader.load("9.0.0", classLoader) }

        assertEquals("virtual machine failed", failure.message)
    }

    @Test
    fun `reports a factory that does not link`() {
        val failure = failure("2.4.0", UnlinkedFactory::class, CurrentFactory::class)

        assertEquals("Compiler adapter factories cannot be discovered.", failure.message)
        assertIs<NoSuchMethodError>(assertIs<ServiceConfigurationError>(failure.cause).cause)
    }

    @Test
    fun `rethrows a virtual machine error of a factory`() {
        val classLoader = AdapterClassLoader.of(directory, CrashingFactory::class, CurrentFactory::class)

        val failure = assertFailsWith<InternalError> { CompilerCompatLoader.load("2.4.0", classLoader) }

        assertEquals("virtual machine failed in a factory", failure.message)
    }

    @Test
    fun `reports a provider that cannot be instantiated`() {
        val classLoader = AdapterClassLoader(directory, listOf(CurrentFactory::class.java.name, "fixture.Missing"))

        val failure = assertFailsWith<CompatLoadException> { CompilerCompatLoader.load("2.4.0", classLoader) }

        assertEquals("Compiler adapter factories cannot be discovered.", failure.message)
        assertContains(assertIs<ServiceConfigurationError>(failure.cause).message.orEmpty(), "fixture.Missing")
    }

    @Test
    fun `reports invalid and ambiguous factory metadata`() {
        val malformed = failure("2.4.0", CurrentFactory::class, MalformedFactory::class)
        assertContains(malformed.message.orEmpty(), MalformedFactory::class.java.name)
        assertContains(malformed.message.orEmpty(), "declares an invalid minimum Kotlin version 'nine'")

        val ambiguous = failure("2.4.0", CurrentFactory::class, RivalFactory::class)
        assertContains(ambiguous.message.orEmpty(), "Several factories declare the minimum Kotlin version 2.3.20")
        assertContains(ambiguous.message.orEmpty(), CurrentFactory::class.java.name)
        assertContains(ambiguous.message.orEmpty(), RivalFactory::class.java.name)
    }

    @Test
    fun `rejects a compiler version that is not a stable release`() {
        for (version in listOf("2.5.0-RC", "2.4.20-dev-1234", "2.4.0-ij261-64", "2.4", "")) {
            val failure = failure(version, CurrentFactory::class)

            assertEquals(
                "Kotlin '$version' is not supported. Only stable MAJOR.MINOR.PATCH releases are recognized.",
                failure.message,
            )
            assertNull(failure.cause)
        }
    }

    @Test
    fun `rejects a compiler without a version`() {
        val failure = failure(null, CurrentFactory::class)

        assertEquals("The Kotlin compiler does not report its version.", failure.message)
    }

    @Test
    fun `rejects a compiler older than every adapter`() {
        val failure = failure("2.3.10", CurrentFactory::class, FutureFactory::class)

        assertEquals("Kotlin 2.3.10 is not supported. The oldest supported version is 2.3.20.", failure.message)
    }

    @Test
    fun `traces the compiler version, the minimum and the factory of a loaded adapter`() {
        val classLoader = AdapterClassLoader.of(directory, CurrentFactory::class, FutureFactory::class)
        val traced = mutableListOf<String>()

        CompilerCompatLoader.load("2.4.10", classLoader, traced::add)

        val message = traced.single()
        assertContains(
            message,
            "Kotlin 2.4.10 uses ${CurrentAdapter::class.java.name}, the compiler adapter for Kotlin 2.3.20 " +
                "created by ${CurrentFactory::class.java.name} from ",
        )
    }

    @Test
    fun `creates a new adapter for every load`() {
        val classLoader = AdapterClassLoader.of(directory, CurrentFactory::class)

        assertNotSame(CompilerCompatLoader.load("2.4.0", classLoader), CompilerCompatLoader.load("2.4.0", classLoader))
    }

    @Test
    fun `reads the version of the compiler on the classpath and finds no factories in the contract`() {
        val failure = assertFailsWith<CompatLoadException> { CompilerCompatLoader.load() }

        assertNotNull(CompilerVersion.parseCanonicalOrNull(assertNotNull(KotlinCompilerVersion.getVersion())))
        assertEquals("No compiler adapter factories were found on the compiler plugin classpath.", failure.message)
    }

    private fun failure(compiler: String?, vararg providers: KClass<*>): CompatLoadException {
        val classLoader = AdapterClassLoader.of(directory, *providers)
        return assertFailsWith<CompatLoadException> { CompilerCompatLoader.load(compiler, classLoader) }
    }
}
