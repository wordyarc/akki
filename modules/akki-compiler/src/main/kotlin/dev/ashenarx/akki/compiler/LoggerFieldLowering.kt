@file:OptIn(UnsafeDuringIrConstructionAPI::class)

package dev.ashenarx.akki.compiler

import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.builders.declarations.buildField
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationContainer
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrDeclarationParent
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrGetFieldImpl
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.isAnonymousObject
import org.jetbrains.kotlin.ir.util.isInterface
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

internal class LoggerFieldLowering(
    private val context: IrPluginContext,
    private val symbols: AkkiSymbols,
) : IrElementTransformerVoidWithContext() {
    private val fields: MutableMap<IrDeclarationContainer, IrField> = mutableMapOf()

    override fun visitCall(expression: IrCall): IrExpression {
        expression.transformChildrenVoid()
        if (!symbols.isCallSite(expression.symbol.owner)) return expression
        val owner = owner() ?: return expression
        val field = fields.getOrPut(owner) { owner.createLoggerField() }
        return IrGetFieldImpl(expression.startOffset, expression.endOffset, field.symbol, field.type)
    }

    private fun owner(): IrDeclarationContainer? {
        if (allScopes.any { (it.irElement as? IrFunction)?.isInline == true }) return null
        var current: IrDeclarationParent = currentDeclarationParent ?: return null
        while (true) {
            when {
                current is IrFile -> return current
                current is IrClass && current.isInterface -> return null
                current is IrClass && !current.isHoisted() -> return current
                current is IrDeclaration -> current = current.parent
                else -> return null
            }
        }
    }

    private fun IrClass.isHoisted(): Boolean =
        isAnonymousObject ||
            visibility == DescriptorVisibilities.LOCAL ||
            (isCompanion && !hasAnnotation(LOG_NAME))

    private fun IrDeclarationContainer.createLoggerField(): IrField {
        val field = context.irFactory.buildField {
            name = FIELD_NAME
            type = symbols.loggerType
            visibility = DescriptorVisibilities.PRIVATE
            isStatic = true
            isFinal = true
            origin = IrDeclarationOrigin.DEFINED
        }
        field.parent = this
        val builder = DeclarationIrBuilder(context, field.symbol)
        field.initializer = context.irFactory.createExpressionBody(
            builder.startOffset,
            builder.endOffset,
            builder.irCall(symbols.forCaller).apply { arguments[0] = builder.irGetObject(symbols.logRegistry) },
        )
        declarations += field
        return field
    }

    private companion object {
        val FIELD_NAME: Name = Name.identifier("\$\$log")
        val LOG_NAME: FqName = FqName("dev.ashenarx.akki.LogName")
    }
}
