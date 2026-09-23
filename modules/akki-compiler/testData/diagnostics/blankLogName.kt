// RUN_PIPELINE_TILL: FRONTEND
package fixture

import io.akki.*

@LogName(<!BLANK_LOG_NAME!>"  "<!>)
class Blank {
    fun handle(): String = log.name
}

private const val EMPTY = " "

@LogName(<!BLANK_LOG_NAME!>EMPTY<!>)
class BlankConstant {
    fun handle(): String = log.name
}

@LogName("audit")
class Named {
    fun handle(): String = log.name
}

fun named(): Logger = Log.named(<!BLANK_LOG_NAME!>""<!>)

fun namedByConstant(): Logger = Log.named(<!BLANK_LOG_NAME!>EMPTY<!>)

fun namedByArgument(): Logger = Log.named(name = <!BLANK_LOG_NAME!>" "<!>)

fun namedFine(): Logger = Log.named("audit")
