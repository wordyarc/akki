package fixture

import dev.ashenarx.akki.*

class Service {
    class Nested {
        fun probe(): String = log.name
    }

    inner class Inner {
        fun probe(): String = log.name
    }

    object NestedObject {
        fun probe(): String = log.name
    }

    companion object {
        fun probe(): String = log.name
    }

    fun member(): String = log.name

    fun lambda(): String = listOf(0).map { log.name }.single()

    fun localClass(): String {
        class Local {
            fun probe(): String = log.name
        }
        return Local().probe()
    }

    fun objectExpression(): String {
        val probe = object : Any() {
            fun probe(): String = log.name
        }
        return probe.probe()
    }
}

class NamedCompanionHost {
    @LogName("companion-audit")
    companion object {
        fun probe(): String = log.name
    }
}

object Standalone {
    fun probe(): String = log.name
}

enum class Colour {
    RED {
        override fun probe(): String = log.name
    };

    abstract fun probe(): String
}

@LogName("class-audit")
class Renamed {
    fun probe(): String = log.name
}

interface Contract {
    fun probe(): String = log.name
}

private class ContractImpl : Contract

fun topLevel(): String = log.name

fun box(): String = listOf(
    "topLevel=" + topLevel(),
    "member=" + Service().member(),
    "nested=" + Service.Nested().probe(),
    "inner=" + Service().Inner().probe(),
    "companion=" + Service.probe(),
    "namedCompanion=" + NamedCompanionHost.probe(),
    "standaloneObject=" + Standalone.probe(),
    "nestedObject=" + Service.NestedObject.probe(),
    "lambda=" + Service().lambda(),
    "localClass=" + Service().localClass(),
    "objectExpression=" + Service().objectExpression(),
    "enumEntry=" + Colour.RED.probe(),
    "renamedClass=" + Renamed().probe(),
    "interfaceMethod=" + ContractImpl().probe(),
).joinToString(",")
