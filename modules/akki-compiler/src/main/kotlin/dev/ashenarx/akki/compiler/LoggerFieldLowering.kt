@file:OptIn(UnsafeDuringIrConstructionAPI::class)

package dev.ashenarx.akki.compiler

import org.jetbrains.kotlin.backend.common.FileLoweringPass
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.DescriptorVisibility
import org.jetbrains.kotlin.ir.builders.declarations.buildField
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irExprBody
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationContainer
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrBlockImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetFieldImpl
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.SYNTHETIC_OFFSET
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.isAnonymousObject
import org.jetbrains.kotlin.ir.util.isInterface
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.load.java.JavaDescriptorVisibilities
import org.jetbrains.kotlin.platform.jvm.isJvm

internal class LoggerFieldLowering(
    private val context: IrPluginContext,
    private val symbols: AkkiSymbols,
) : FileLoweringPass, IrElementTransformerVoidWithContext() {
    private val isJvm: Boolean = context.platform.isJvm()

    private val fields = mutableMapOf<IrDeclarationContainer, IrField>()

    override fun lower(irFile: IrFile) {
        irFile.transform(this, null)
        fields.forEach { (container, field) -> container.declarations.add(0, field) }
        fields.clear()
    }

    override fun visitCall(expression: IrCall): IrExpression {
        expression.transformChildrenVoid()
        if (!symbols.isCallSite(expression.symbol.owner) || isInlined()) return expression
        val owner = loggerOwner() ?: return expression
        val field = fields.getOrPut(owner) { owner.createLoggerField() }
        val read = IrGetFieldImpl(expression.startOffset, expression.endOffset, field.symbol, field.type)
        val receiver = expression.arguments.firstOrNull() ?: return read
        return IrBlockImpl(expression.startOffset, expression.endOffset, field.type, null, listOf(receiver, read))
    }

    private fun isInlined(): Boolean = allScopes.any { (it.irElement as? IrFunction)?.isInline == true }

    private fun loggerOwner(): IrDeclarationContainer? {
        for (scope in allScopes.asReversed()) {
            val enclosing = scope.irElement as? IrClass ?: continue
            if (enclosing.isHoisted()) continue
            return enclosing.takeIf { isJvm || !it.isInterface }
        }
        return currentFile
    }

    private fun IrClass.isHoisted(): Boolean =
        !hasAnnotation(AkkiNames.LOG_NAME_ID) &&
            (isAnonymousObject || visibility == DescriptorVisibilities.LOCAL || isCompanion)

    private fun IrDeclarationContainer.createLoggerField(): IrField {
        val field = context.irFactory.buildField {
            startOffset = SYNTHETIC_OFFSET
            endOffset = SYNTHETIC_OFFSET
            name = AkkiNames.LOGGER_FIELD
            type = symbols.loggerType
            visibility = fieldVisibility()
            isStatic = true
            isFinal = true
            origin = GENERATED_LOGGER_FIELD
        }
        field.parent = this
        field.initializer = DeclarationIrBuilder(context, field.symbol, SYNTHETIC_OFFSET, SYNTHETIC_OFFSET).run {
            irExprBody(irCall(symbols.forCaller).apply { arguments[0] = irGetObject(symbols.logRegistry) })
        }
        return field
    }

    private fun IrDeclarationContainer.fieldVisibility(): DescriptorVisibility = when {
        this is IrClass && isInterface -> DescriptorVisibilities.PUBLIC
        isJvm -> JavaDescriptorVisibilities.PACKAGE_VISIBILITY
        else -> DescriptorVisibilities.PRIVATE
    }
}
