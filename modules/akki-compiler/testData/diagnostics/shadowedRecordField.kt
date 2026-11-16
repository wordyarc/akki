// RUN_PIPELINE_TILL: FRONTEND
// RENDER_DIAGNOSTICS_FULL_TEXT
// FILE: members.kt
@file:OptIn(io.akki.ExperimentalAkkiApi::class)

package fixture

import io.akki.*

class Form(private val logger: Logger) {
    fun field(key: String, value: Any?) {}

    fun fields(vararg fields: Pair<String, Any?>) {}

    fun fields(fields: Map<String, Any?>) {}

    fun shadowed(items: List<String>) {
        logger.info { <!RECORD_FIELD_SHADOWED!>field<!>("jobId", 1); "one field" }
        logger.info { <!RECORD_FIELD_SHADOWED!>fields<!>("jobId" to 1, "attempt" to 2); "pairs" }
        logger.info { <!RECORD_FIELD_SHADOWED!>fields<!>(mapOf("jobId" to 1)); "a map" }
        logger.info { items.forEach { <!RECORD_FIELD_SHADOWED!>field<!>(it, 1) }; "a nested lambda" }
    }

    context(_: RecordScope)
    fun block() {
        <!RECORD_FIELD_SHADOWED!>fields<!>("jobId" to 1)
    }

    context(_: RecordScope)
    fun expression() = <!RECORD_FIELD_SHADOWED!>field<!>("jobId", 1)

    fun written() {
        logger.info { io.akki.field("jobId", 1); io.akki.fields("attempt" to 2); "qualified" }
        logger.info { this.field("jobId", 1); this@Form.fields("attempt" to 2); "an explicit receiver" }
        logger.info { fields(); "no arguments" }
    }

    fun outsideRecord() {
        field("jobId", 1)
        fields("jobId" to 1)
    }
}

class Fluent(private val logger: Logger) {
    fun field(name: String, value: Any?): Fluent = this

    fun shadowed() {
        logger.info { <!RECORD_FIELD_SHADOWED!>field<!>("jobId", 1); "a statement" }
    }

    fun used() {
        logger.info { field("jobId", 1).field("attempt", 2).toString() }
        logger.info { "built ${field("jobId", 1)}" }
    }
}

class Table(private val logger: Logger) {
    val fields: List<String> = emptyList()

    fun field(index: Int, value: Any?) {}

    fun fields(first: String, second: String) {}

    fun own() {
        logger.info { field(1, "by index"); fields("jobId", "attempt"); "other parameters" }
        logger.info { field("jobId", 1); fields("attempt" to 2); "akki functions of ${fields.size}" }
    }
}

fun local(logger: Logger) {
    fun field(key: String, value: Any?) {}

    logger.info { <!RECORD_FIELD_SHADOWED!>field<!>("jobId", 1); "a local function" }
    field("jobId", 1)
}

// FILE: samePackage.kt
@file:OptIn(io.akki.ExperimentalAkkiApi::class)

package fixture.jobs

import io.akki.*

fun field(key: String, value: Any?) {}

fun shadowed(logger: Logger) {
    logger.info { <!RECORD_FIELD_SHADOWED!>field<!>("jobId", 1); "a function of the same package" }
    logger.info { fixture.jobs.field("jobId", 1); "qualified with the package" }
    logger.info { io.akki.field("jobId", 1); "written" }
}

// FILE: explicitImport.kt
@file:OptIn(io.akki.ExperimentalAkkiApi::class)

package fixture.jobs

import io.akki.Logger
import io.akki.field

fun written(logger: Logger) {
    logger.info { field("jobId", 1); "an explicit import wins over the package" }
}
