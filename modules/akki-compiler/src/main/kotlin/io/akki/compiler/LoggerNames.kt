@file:OptIn(UnsafeDuringIrConstructionAPI::class)

package io.akki.compiler

import org.jetbrains.kotlin.backend.jvm.lower.getFileClassInfo
import org.jetbrains.kotlin.ir.declarations.IrAnnotationContainer
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationContainer
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.name
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.classId
import org.jetbrains.kotlin.ir.util.getAnnotation
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.JvmStandardClassIds.MULTIFILE_PART_NAME_DELIMITER
import org.jetbrains.kotlin.name.NameUtils

internal class DeclarationName(val source: String, val platform: String)

private const val FACADE_SUFFIX: String = "Kt"

private val LOG_NAME_FQ_NAME: FqName = AkkiNames.LOG_NAME_ID.asSingleFqName()

internal fun IrDeclarationContainer.declarationName(isJvm: Boolean): DeclarationName = when (this) {
    is IrClass -> logName() ?: className()
    is IrFile -> logName() ?: if (isJvm) jvmFileName() else sourceFileName().let { DeclarationName(it, it) }
    else -> error("unexpected logger field owner: ${this::class.simpleName}")
}

private fun IrAnnotationContainer.logName(): DeclarationName? {
    val value = getAnnotation(LOG_NAME_FQ_NAME)?.arguments?.firstOrNull()
    val name = (value as? IrConst)?.value as? String ?: return null
    return DeclarationName(name, name)
}

private fun IrClass.className(): DeclarationName {
    val classId = classId ?: return kotlinFqName.asString().let { DeclarationName(it, it) }
    val relative = classId.relativeClassName.asString()
    val prefix = classId.packageFqName.qualifier()
    return DeclarationName(prefix + relative, prefix + relative.replace('.', '$'))
}

private fun IrFile.jvmFileName(): DeclarationName {
    val fileClass = getFileClassInfo().fileClassFqName
    val stem = fileClass.shortName().asString()
        .substringAfterLast(MULTIFILE_PART_NAME_DELIMITER)
        .removeSuffix(FACADE_SUFFIX)
    return DeclarationName(fileClass.parent().qualifier() + stem, fileClass.asString())
}

private fun IrFile.sourceFileName(): String =
    packageFqName.qualifier() + NameUtils.getPackagePartClassNamePrefix(name.substringBeforeLast('.'))

private fun FqName.qualifier(): String = asString().let { if (it.isEmpty()) "" else "$it." }
