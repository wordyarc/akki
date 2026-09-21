package fixture

import io.akki.*
import kotlin.reflect.KClass

class Service {
    class Nested {
        fun probe(): Logger = log
    }

    inner class Inner {
        fun probe(): Logger = log
    }

    object NestedObject {
        fun probe(): Logger = log
    }

    companion object {
        fun probe(): Logger = log
    }

    fun member(): Logger = log

    fun lambda(): Logger = listOf(0).map { log }.single()

    fun localClass(): Logger {
        class Local {
            fun probe(): Logger = log
        }
        return Local().probe()
    }

    fun innerOfLocalClass(): Logger {
        class Local {
            inner class Deep {
                fun probe(): Logger = log
            }
        }
        return Local().Deep().probe()
    }

    fun objectExpression(): Logger {
        val probe = object : Any() {
            fun probe(): Logger = log
        }
        return probe.probe()
    }
}

class NamedCompanionHost {
    @LogName("companion-audit")
    companion object {
        fun probe(): Logger = log
    }
}

object Standalone {
    fun probe(): Logger = log
}

enum class Colour {
    RED {
        override fun probe(): Logger = log
    };

    abstract fun probe(): Logger
}

@LogName("class-audit")
class Renamed {
    fun probe(): Logger = log
}

private const val CONSTANT_NAME = "constant-audit"

@LogName(CONSTANT_NAME)
class RenamedByConstant {
    fun probe(): Logger = log
}

interface Contract {
    fun probe(): Logger = log
}

private class ContractImpl : Contract

fun topLevel(): Logger = log

private fun factory(type: KClass<*>): Logger = Log.of(type)

private fun agrees(site: String, intrinsic: Logger, factory: Logger): String =
    if (intrinsic === factory) "$site=${intrinsic.name}" else "$site=${intrinsic.name}!=${factory.name}"

fun box(): String = listOf(
    agrees("topLevel", topLevel(), Log.of(Class.forName("fixture.FixtureKt"))),
    agrees("member", Service().member(), factory(Service::class)),
    agrees("nested", Service.Nested().probe(), factory(Service.Nested::class)),
    agrees("inner", Service().Inner().probe(), factory(Service.Inner::class)),
    agrees("companion", Service.probe(), factory(Service::class)),
    agrees("namedCompanion", NamedCompanionHost.probe(), Log.of(NamedCompanionHost.Companion::class.java)),
    agrees("standaloneObject", Standalone.probe(), factory(Standalone::class)),
    agrees("nestedObject", Service.NestedObject.probe(), factory(Service.NestedObject::class)),
    agrees("lambda", Service().lambda(), factory(Service::class)),
    agrees("localClass", Service().localClass(), factory(Service::class)),
    agrees("innerOfLocalClass", Service().innerOfLocalClass(), factory(Service::class)),
    agrees("objectExpression", Service().objectExpression(), factory(Service::class)),
    agrees("enumEntry", Colour.RED.probe(), factory(Colour::class)),
    agrees("renamedClass", Renamed().probe(), factory(Renamed::class)),
    agrees("renamedByConstant", RenamedByConstant().probe(), factory(RenamedByConstant::class)),
    agrees("interfaceMethod", ContractImpl().probe(), factory(Contract::class)),
).joinToString(",")
