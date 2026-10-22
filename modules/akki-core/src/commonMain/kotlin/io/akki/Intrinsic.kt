package io.akki

import io.akki.internal.CallSite
import io.akki.internal.akkiError
import kotlin.jvm.JvmSynthetic

@CallSite
public val log: Logger
    @JvmSynthetic get() = pluginNotApplied()

@CallSite
@JvmSynthetic
public fun logger(): Logger = pluginNotApplied()

private fun pluginNotApplied(): Nothing = akkiError(
    "the compiler plugin is not applied to this source set, so 'log' and 'logger()' cannot resolve " +
        "a name. Apply the akki Gradle plugin, or use 'Log.of<T>()' or 'Log.named(\"...\")' instead.",
)
