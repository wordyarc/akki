@file:JvmName("DeclarationLoggers")

package io.akki.internal

import io.akki.Logger
import kotlin.jvm.JvmName

@PublishedApi
internal fun declarationLogger(sourceName: String, jvmClassName: String): Logger =
    namedLogger(platformDeclarationName(sourceName, jvmClassName))
