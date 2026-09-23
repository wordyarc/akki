// RUN_PIPELINE_TILL: FRONTEND
package fixture

import io.akki.*

fun probe(): String {
    val viaProperty = <!CONTEXTUAL_LOGGER_REFERENCE!>::log<!>
    val viaFunction = <!CONTEXTUAL_LOGGER_REFERENCE!>::logger<!>
    return listOf(viaProperty.get(), viaFunction()).joinToString(",") { it.name }
}
