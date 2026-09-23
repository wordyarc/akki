// RUN_PIPELINE_TILL: FRONTEND
package fixture

import io.akki.*

private const val EMPTY = " "

fun named(): Logger = Log.named(<!BLANK_LOG_NAME!>""<!>)

fun namedByConstant(): Logger = Log.named(<!BLANK_LOG_NAME!>EMPTY<!>)

fun namedByArgument(): Logger = Log.named(name = <!BLANK_LOG_NAME!>" "<!>)

fun namedFine(): Logger = Log.named("audit")
