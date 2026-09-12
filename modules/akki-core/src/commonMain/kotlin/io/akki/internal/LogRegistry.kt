package io.akki.internal

import io.akki.InternalAkkiApi
import io.akki.Logger

@InternalAkkiApi
public object LogRegistry {
    public fun of(name: String): Logger = platformLogger(name)

    public fun forDeclaration(source: String, platformName: String): Logger =
        platformLogger(platformDeclarationName(source, platformName))

    public fun forCaller(): Logger = error(
        "akki: the compiler plugin is not applied to this source set, so `log`, `logger()` and " +
            "`Log.forCaller()` cannot resolve a name. Apply the io.akki Gradle plugin, " +
            "or use Log.of<T>() / Log.named(\"...\") instead.",
    )
}
