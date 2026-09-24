// RUN_PIPELINE_TILL: FRONTEND
// WITHOUT_AKKI
// CHECK_SOURCELESS_DIAGNOSTICS

// MODULE: core
// WITHOUT_PLUGIN
// FILE: Logger.kt
package io.akki

interface Logger

// FILE: AkkiVersion.kt
package io.akki.internal

internal const val AKKI_VERSION: String = "0.0.1"

// MODULE: user(core)
// FILE: User.kt
package fixture

import io.akki.Logger

fun probe(logger: Logger): String = "unreachable"
