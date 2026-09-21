package fixture

import io.akki.*

@LogName("  ")
class Blank {
    fun handle(): String = log.name
}

private const val EMPTY = " "

@LogName(EMPTY)
class BlankConstant {
    fun handle(): String = log.name
}

@LogName("audit")
class Named {
    fun handle(): String = log.name
}

fun box(): String = Blank().handle() + "|" + BlankConstant().handle() + "|" + Named().handle()
