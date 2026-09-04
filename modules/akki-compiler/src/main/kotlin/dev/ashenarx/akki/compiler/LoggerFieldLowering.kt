@file:OptIn(UnsafeDuringIrConstructionAPI::class)

package dev.ashenarx.akki.compiler

import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.DescriptorVisibility
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.declarations.buildField
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationContainer
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrGetFieldImpl
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.SYNTHETIC_OFFSET
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.isAnonymousObject
import org.jetbrains.kotlin.ir.util.isInterface
import org.jetbrains.kotlin.load.java.JavaDescriptorVisibilities
import org.jetbrains.kotlin.platform.jvm.isJvm

internal class LoggerFieldLowering(
    private val context: IrPluginContext,
    private val symbols: AkkiSymbols,
) : IrElementTransformerVoidWithContext() {
    private val fields: MutableMap<IrDeclarationContainer, IrField> = mutableMapOf()

    private val isJvm: Boolean = context.platform.isJvm()

    override fun visitClassNew(declaration: IrClass): IrStatement =
        super.visitClassNew(declaration).also { declaration.prependLoggerField() }

    override fun visitFileNew(declaration: IrFile): IrFile =
        super.visitFileNew(declaration).also { declaration.prependLoggerField() }

    override fun visitCall(expression: IrCall): IrExpression {
        expression.transformChildrenVoid()
        if (!symbols.isCallSite(expression.symbol.owner)) return expression
        val owner = owner() ?: return expression
        val field = fields.getOrPut(owner) { owner.createLoggerField() }
        return IrGetFieldImpl(expression.startOffset, expression.endOffset, field.symbol, field.type)
    }

    private fun owner(): IrDeclarationContainer? {
        if (allScopes.any { (it.irElement as? IrFunction)?.isInline == true }) return null
        val owner = generateSequence(currentDeclarationParent) { (it as? IrDeclaration)?.parent }
            .firstOrNull { it is IrFile || it is IrClass && !it.isHoisted() }
        return when {
            owner is IrClass && owner.isInterface && !isJvm -> null
            else -> owner as? IrDeclarationContainer
        }
    }

    private fun IrClass.isHoisted(): Boolean =
        isAnonymousObject ||
            visibility == DescriptorVisibilities.LOCAL ||
            (isCompanion && !hasAnnotation(AkkiNames.LOG_NAME_ID))

    private fun IrDeclarationContainer.createLoggerField(): IrField {
        val field = context.irFactory.buildField {
            startOffset = SYNTHETIC_OFFSET
            endOffset = SYNTHETIC_OFFSET
            name = AkkiNames.LOGGER_FIELD
            type = symbols.loggerType
            visibility = fieldVisibility()
            isStatic = true
            isFinal = true
            origin = LOGGER_FIELD
        }
        field.parent = this
        val builder = DeclarationIrBuilder(context, field.symbol, SYNTHETIC_OFFSET, SYNTHETIC_OFFSET)
        field.initializer = context.irFactory.createExpressionBody(
            builder.startOffset,
            builder.endOffset,
            builder.irCall(symbols.forCaller).apply { arguments[0] = builder.irGetObject(symbols.logRegistry) },
        )
        return field
    }

    private fun IrDeclarationContainer.prependLoggerField() {
        fields.remove(this)?.let { declarations.add(0, it) }
    }

    private fun IrDeclarationContainer.fieldVisibility(): DescriptorVisibility = when {
        this is IrClass && isInterface -> DescriptorVisibilities.PUBLIC
        isJvm -> JavaDescriptorVisibilities.PACKAGE_VISIBILITY
        else -> DescriptorVisibilities.PRIVATE
    }
}
