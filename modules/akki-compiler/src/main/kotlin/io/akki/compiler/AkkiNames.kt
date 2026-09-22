package io.akki.compiler

import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

internal object AkkiNames {
    const val PLUGIN_ID: String = "io.akki"

    val PACKAGE: FqName = FqName(PLUGIN_ID)
    val INTERNAL_PACKAGE: FqName = PACKAGE.child(Name.identifier("internal"))
    val BACKEND_PACKAGE: FqName = PACKAGE.child(Name.identifier("backend"))

    val LOGGER_ID: ClassId = PACKAGE.classId("Logger")
    val LEVEL_ID: ClassId = PACKAGE.classId("Level")
    val SINK_ID: ClassId = BACKEND_PACKAGE.classId("Sink")
    val LOG_NAME_ID: ClassId = PACKAGE.classId("LogName")
    val CALL_SITE_ID: ClassId = INTERNAL_PACKAGE.classId("CallSite")
    val LOG_ID: ClassId = PACKAGE.classId("Log")
    val LOG_REGISTRY_ID: ClassId = INTERNAL_PACKAGE.classId("LogRegistry")

    val EMPTY_MAP_ID: CallableId =
        CallableId(StandardNames.COLLECTIONS_PACKAGE_FQ_NAME, Name.identifier("emptyMap"))

    val FOR_DECLARATION: Name = Name.identifier("forDeclaration")
    val OF: Name = Name.identifier("of")
    val NAMED: Name = Name.identifier("named")
    val SINK: Name = Name.identifier("sink")
    val EMIT: Name = Name.identifier("emit")

    val VALUE: Name = Name.identifier("value")

    val MESSAGE: Name = Name.identifier("message")
    val CAUSE: Name = Name.identifier("cause")
    val FIELDS: Name = Name.identifier("fields")

    val LOGGER_FIELD: Name = Name.identifier("\$\$log")
    val LOGGER_HOLDER: Name = Name.identifier("\$Log")

    fun loggerField(index: Int): Name = Name.identifier("${LOGGER_FIELD.asString()}\$$index")

    fun levelName(entry: Name): Name = Name.identifier(entry.asString().lowercase())

    private fun FqName.classId(name: String): ClassId = ClassId(this, Name.identifier(name))
}
