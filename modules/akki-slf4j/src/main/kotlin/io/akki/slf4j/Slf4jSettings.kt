@file:OptIn(InternalAkkiApi::class)

package io.akki.slf4j

import io.akki.InternalAkkiApi
import io.akki.internal.AkkiProperties
import io.akki.internal.toEnum

internal const val SLF4J_PROPERTY_PREFIX: String = "io.akki.slf4j."
internal const val CONTEXT_PROPERTY: String = "${SLF4J_PROPERTY_PREFIX}context"

internal enum class ContextTarget {
    KEY_VALUE_PAIRS,
    MDC,
}

internal fun configuredContextTarget(): ContextTarget {
    AkkiProperties.resourceSettings(SLF4J_PROPERTY_PREFIX).forEach { (name, setting) ->
        if (name != CONTEXT_PROPERTY) {
            System.err.println("akki: unknown property ${setting.source}: expected '$CONTEXT_PROPERTY'; ignoring it")
        }
    }
    return AkkiProperties[CONTEXT_PROPERTY].toEnum(ContextTarget.KEY_VALUE_PAIRS)
}
