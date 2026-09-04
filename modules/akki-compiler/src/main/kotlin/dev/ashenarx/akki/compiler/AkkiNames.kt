package dev.ashenarx.akki.compiler

import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

internal object AkkiNames {
    const val PLUGIN_ID: String = "dev.ashenarx.akki"

    val PACKAGE: FqName = FqName(PLUGIN_ID)
    val INTERNAL_PACKAGE: FqName = PACKAGE.child(Name.identifier("internal"))

    val LOGGER_ID: ClassId = PACKAGE.classId("Logger")
    val LEVEL_ID: ClassId = PACKAGE.classId("Level")
    val SINK_ID: ClassId = PACKAGE.classId("Sink")
    val LOG_NAME_ID: ClassId = PACKAGE.classId("LogName")
    val CALL_SITE_ID: ClassId = INTERNAL_PACKAGE.classId("CallSite")
    val LOG_REGISTRY_ID: ClassId = INTERNAL_PACKAGE.classId("LogRegistry")

    val EMPTY_MAP_ID: CallableId =
        CallableId(StandardNames.COLLECTIONS_PACKAGE_FQ_NAME, Name.identifier("emptyMap"))

    val FOR_CALLER: Name = Name.identifier("forCaller")
    val SINK: Name = Name.identifier("sink")
    val EMIT: Name = Name.identifier("emit")

    val MESSAGE: Name = Name.identifier("message")
    val CAUSE: Name = Name.identifier("cause")
    val FIELDS: Name = Name.identifier("fields")

    val LOGGER_FIELD: Name = Name.identifier("\$\$log")

    fun levelId(entry: Name): CallableId = CallableId(PACKAGE, Name.identifier(entry.asString().lowercase()))

    private fun FqName.classId(name: String): ClassId = ClassId(this, Name.identifier(name))
}
