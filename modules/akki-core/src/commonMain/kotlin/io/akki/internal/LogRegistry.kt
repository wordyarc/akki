package io.akki.internal

import io.akki.AkkiException
import io.akki.InternalAkkiApi
import io.akki.Logger

@InternalAkkiApi
public object LogRegistry {
    public fun of(name: String): Logger = platformLogger(name.orRejectBlank())

    public fun forDeclaration(source: String, platformName: String): Logger =
        platformLogger(platformDeclarationName(source, platformName).orRejectBlank())

    public fun forCaller(): Logger = throw AkkiException(
        "akki: the compiler plugin is not applied to this source set, so `log`, `logger()` and " +
            "`Log.forCaller()` cannot resolve a name. Apply the io.akki Gradle plugin, " +
            "or use Log.of<T>() / Log.named(\"...\") instead.",
    )
}

private fun String.orRejectBlank(): String {
    require(isNotBlank()) {
        "akki: a logger name must not be blank, it is what every backend routes and filters on"
    }
    return this
}
