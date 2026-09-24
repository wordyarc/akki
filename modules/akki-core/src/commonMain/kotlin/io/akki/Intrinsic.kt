package io.akki

import io.akki.internal.CallSite
import io.akki.internal.akkiError

@CallSite
public val log: Logger
    get() = pluginNotApplied()

@CallSite
public fun logger(): Logger = pluginNotApplied()

private fun pluginNotApplied(): Nothing = akkiError(
    "the compiler plugin is not applied to this source set, so `log` and `logger()` cannot resolve " +
        "a name. Apply the io.akki Gradle plugin, or use Log.of<T>() / Log.named(\"...\") instead.",
)
