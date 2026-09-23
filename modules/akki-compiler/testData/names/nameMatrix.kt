// CHECK_BYTECODE_TEXT
// 1 LDC "fixture\.Service\.Nested"
// 1 LDC "fixture\.Service\$Nested"
package fixture

import io.akki.*
import java.lang.invoke.MethodHandles
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class Service {
    class Nested {
        fun probe(): Logger = agrees(log, this::class, Log.of<Nested>(), Log.of(Nested::class))
    }

    inner class Inner {
        fun probe(): Logger = agrees(log, this::class, Log.of<Inner>(), Log.of(Inner::class))
    }

    object NestedObject {
        fun probe(): Logger = agrees(log, this::class, Log.of<NestedObject>(), Log.of(NestedObject::class))
    }

    companion object {
        fun probe(): Logger = agrees(log, this::class, Log.of<Companion>(), Log.of(Companion::class))
    }

    fun member(): Logger = agrees(log, this::class, Log.of<Service>(), Log.of(Service::class))

    fun lambda(): Logger {
        val body = { log }
        return agrees(body(), body::class)
    }

    fun localClass(): Logger {
        class Local {
            fun probe(): Logger = agrees(log, this::class, Log.of<Local>(), Log.of(Local::class))
        }
        return Local().probe()
    }

    fun innerOfLocalClass(): Logger {
        class Local {
            inner class Deep {
                inner class Deeper {
                    fun probe(): Logger = agrees(log, this::class, Log.of<Deeper>(), Log.of(Deeper::class))
                }

                fun probe(): Logger {
                    val own = agrees(log, this::class, Log.of<Deep>(), Log.of(Deep::class))
                    assertSame(own, Deeper().probe())
                    return own
                }
            }
        }
        return Local().Deep().probe()
    }

    fun objectExpression(): Logger {
        val probe = object : Any() {
            inner class Inner {
                fun probe(): Logger = agrees(log, this::class, Log.of<Inner>(), Log.of(Inner::class))
            }

            fun probe(): Logger {
                val own = agrees(log, this::class)
                assertSame(own, Inner().probe())
                return own
            }
        }
        return probe.probe()
    }
}

object Standalone {
    fun probe(): Logger = agrees(log, this::class, Log.of<Standalone>(), Log.of(Standalone::class))
}

enum class Colour {
    RED {
        override fun probe(): Logger = agrees(log, this::class, Log.of<Colour>(), Log.of(Colour::class))
    };

    abstract fun probe(): Logger
}

interface Contract {
    fun probe(): Logger {
        assertNotSame(log, Log.of(this::class))
        return agrees(log, Contract::class, Log.of<Contract>(), Log.of(Contract::class))
    }
}

private class ContractImpl : Contract

fun topLevel(): Logger {
    class Local {
        inner class Inner {
            fun probe(): Logger = agrees(log, this::class, Log.of<Inner>(), Log.of(Inner::class))
        }
    }
    val own = agrees(log, MethodHandles.lookup().lookupClass().kotlin)
    assertSame(own, Local().Inner().probe())
    return own
}

private fun agrees(intrinsic: Logger, type: KClass<*>, vararg folded: Logger): Logger {
    assertSame(intrinsic, Log.of(type), type.toString())
    assertSame(intrinsic, Log.of(type.java), type.toString())
    folded.forEach { assertSame(intrinsic, it, type.toString()) }
    return intrinsic
}

private fun byStyle(source: String, jvm: String): String =
    if (System.getProperty(LOGGER_NAME_STYLE_PROPERTY_NAME) == LOGGER_NAME_STYLE_VALUE_JVM_CLASS) jvm else source

fun box(): String {
    assertEquals(
        listOf(
            byStyle("topLevel=fixture.NameMatrix", "topLevel=fixture.NameMatrixKt"),
            "member=fixture.Service",
            byStyle("nested=fixture.Service.Nested", "nested=fixture.Service\$Nested"),
            byStyle("inner=fixture.Service.Inner", "inner=fixture.Service\$Inner"),
            "companion=fixture.Service",
            "standaloneObject=fixture.Standalone",
            byStyle("nestedObject=fixture.Service.NestedObject", "nestedObject=fixture.Service\$NestedObject"),
            "lambda=fixture.Service",
            "localClass=fixture.Service",
            "innerOfLocalClass=fixture.Service",
            "objectExpression=fixture.Service",
            "enumEntry=fixture.Colour",
            "interfaceMethod=fixture.Contract",
        ),
        listOf(
            "topLevel=${topLevel().name}",
            "member=${Service().member().name}",
            "nested=${Service.Nested().probe().name}",
            "inner=${Service().Inner().probe().name}",
            "companion=${Service.probe().name}",
            "standaloneObject=${Standalone.probe().name}",
            "nestedObject=${Service.NestedObject.probe().name}",
            "lambda=${Service().lambda().name}",
            "localClass=${Service().localClass().name}",
            "innerOfLocalClass=${Service().innerOfLocalClass().name}",
            "objectExpression=${Service().objectExpression().name}",
            "enumEntry=${Colour.RED.probe().name}",
            "interfaceMethod=${ContractImpl().probe().name}",
        ),
    )
    return "OK"
}
