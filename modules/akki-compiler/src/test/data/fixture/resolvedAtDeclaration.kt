package fixture

import io.akki.*

fun topLevel(): String = log.name

class Service {
    fun member(): String = log.name

    fun lambdaPassedToAnInlineFunction(): String = listOf(0).map { log.name }.single()
}

inline fun explicitLoggerIsFine(): String = Log.named("explicit").name

inline fun withNoinlineDefault(noinline probe: () -> String = { log.name }): String = probe()

interface Contract {
    fun defaultMethod(): String = log.name
}
