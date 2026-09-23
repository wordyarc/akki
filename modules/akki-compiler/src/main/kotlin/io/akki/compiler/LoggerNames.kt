@file:OptIn(UnsafeDuringIrConstructionAPI::class)

package io.akki.compiler

import org.jetbrains.kotlin.backend.jvm.lower.getFileClassInfo
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationContainer
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.name
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.classId
import org.jetbrains.kotlin.ir.util.irError
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.JvmStandardClassIds.MULTIFILE_PART_NAME_DELIMITER
import org.jetbrains.kotlin.name.NameUtils
import org.jetbrains.kotlin.resolve.jvm.JvmClassName

internal class DeclarationName(val source: String, val platform: String)

private const val FACADE_SUFFIX: String = "Kt"

internal fun IrDeclarationContainer.declarationName(isJvm: Boolean): DeclarationName = when (this) {
    is IrClass -> className()
    is IrFile -> if (isJvm) jvmFileName() else sourceFileName().let { DeclarationName(it, it) }
    else -> irError("unexpected logger field owner") { withIrEntry("owner", this@declarationName) }
}

private fun IrClass.className(): DeclarationName {
    val classId = classId ?: return kotlinFqName.asString().let { DeclarationName(it, it) }
    val binaryName = JvmClassName.byClassId(classId).internalName.replace('/', '.')
    return DeclarationName(classId.asSingleFqName().asString(), binaryName)
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
