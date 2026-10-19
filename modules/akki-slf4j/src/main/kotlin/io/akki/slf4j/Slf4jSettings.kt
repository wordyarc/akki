@file:OptIn(InternalAkkiApi::class)

package io.akki.slf4j

import io.akki.InternalAkkiApi
import io.akki.internal.AkkiConfigurationException
import io.akki.internal.AkkiProperties

internal const val SLF4J_PROPERTY_PREFIX: String = "io.akki.slf4j."
internal const val CONTEXT_PROPERTY: String = "${SLF4J_PROPERTY_PREFIX}context"

internal enum class ContextTarget {
    KEY_VALUE_PAIRS,
    MDC,
    ;

    val propertyValue: String = name.lowercase().replace('_', '-')
}

internal fun configuredContextTarget(): ContextTarget {
    AkkiProperties.resourceSettings(SLF4J_PROPERTY_PREFIX).forEach { (name, setting) ->
        if (name != CONTEXT_PROPERTY) {
            throw AkkiConfigurationException("akki: unknown property ${setting.source}: expected '$CONTEXT_PROPERTY'")
        }
    }
    val setting = AkkiProperties[CONTEXT_PROPERTY] ?: return ContextTarget.KEY_VALUE_PAIRS
    val normalized = setting.value.lowercase().replace('_', '-')
    return ContextTarget.entries.firstOrNull { it.propertyValue == normalized } ?: throw AkkiConfigurationException(
        "akki: unknown value '${setting.value}' set by ${setting.source}: " +
            "expected ${ContextTarget.entries.joinToString(" or ") { "'${it.propertyValue}'" }}",
    )
}
