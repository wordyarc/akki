// RUN_PIPELINE_TILL: FRONTEND
// WITHOUT_AKKI
// CHECK_SOURCELESS_DIAGNOSTICS

// MODULE: core
// WITHOUT_PLUGIN
// FILE: Logger.kt
package io.akki

interface Logger

// MODULE: user(core)
// FILE: User.kt
package fixture

import io.akki.Logger

fun probe(logger: Logger): String = "unreachable"
