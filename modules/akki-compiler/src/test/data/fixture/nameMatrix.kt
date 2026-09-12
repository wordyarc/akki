package fixture

import io.akki.*

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

interface Contract {
    fun probe(): Logger = log
}

private class ContractImpl : Contract

fun topLevel(): Logger = log

private fun agrees(site: String, intrinsic: Logger, factory: Logger): String =
    if (intrinsic === factory) "$site=${intrinsic.name}" else "$site=${intrinsic.name}!=${factory.name}"

fun box(): String = listOf(
    agrees("topLevel", topLevel(), Log.of(Class.forName("fixture.FixtureKt"))),
    agrees("member", Service().member(), Log.of<Service>()),
    agrees("nested", Service.Nested().probe(), Log.of<Service.Nested>()),
    agrees("inner", Service().Inner().probe(), Log.of<Service.Inner>()),
    agrees("companion", Service.probe(), Log.of<Service>()),
    agrees("namedCompanion", NamedCompanionHost.probe(), Log.of(NamedCompanionHost.Companion::class.java)),
    agrees("standaloneObject", Standalone.probe(), Log.of<Standalone>()),
    agrees("nestedObject", Service.NestedObject.probe(), Log.of<Service.NestedObject>()),
    agrees("lambda", Service().lambda(), Log.of<Service>()),
    agrees("localClass", Service().localClass(), Log.of<Service>()),
    agrees("objectExpression", Service().objectExpression(), Log.of<Service>()),
    agrees("enumEntry", Colour.RED.probe(), Log.of<Colour>()),
    agrees("renamedClass", Renamed().probe(), Log.of<Renamed>()),
    agrees("interfaceMethod", ContractImpl().probe(), Log.of<Contract>()),
).joinToString(",")
