@file:OptIn(UnsafeDuringIrConstructionAPI::class)

package dev.ashenarx.akki.compiler

import org.jetbrains.kotlin.backend.common.FileLoweringPass
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.DescriptorVisibility
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.declarations.buildField
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationContainer
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.impl.IrGetFieldImpl
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.SYNTHETIC_OFFSET
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.isAnonymousObject
import org.jetbrains.kotlin.ir.util.isInterface
import org.jetbrains.kotlin.ir.visitors.IrTransformer
import org.jetbrains.kotlin.load.java.JavaDescriptorVisibilities
import org.jetbrains.kotlin.platform.jvm.isJvm

internal class LoggerFieldLowering(
    private val context: IrPluginContext,
    private val symbols: AkkiSymbols,
) : FileLoweringPass, IrTransformer<LoggerFieldLowering.Owner?>() {
    class Owner(val container: IrDeclarationContainer) {
        var field: IrField? = null
    }

    val generated: MutableMap<IrFile, MutableSet<IrField>> = mutableMapOf()

    private val isJvm: Boolean = context.platform.isJvm()

    private var inlined: Boolean = false

    private lateinit var file: IrFile

    override fun lower(irFile: IrFile) {
        file = irFile
        irFile.lowerInto(Owner(irFile))
    }

    override fun visitClass(declaration: IrClass, data: Owner?): IrStatement {
        when {
            declaration.isHoisted() -> declaration.transformChildren(this, data)
            declaration.isInterface && !isJvm -> declaration.transformChildren(this, null)
            else -> declaration.lowerInto(Owner(declaration))
        }
        return declaration
    }

    override fun visitFunction(declaration: IrFunction, data: Owner?): IrStatement =
        if (declaration.isInline) {
            inlining(true) { super.visitFunction(declaration, data) }
        } else {
            super.visitFunction(declaration, data)
        }

    override fun visitValueParameter(declaration: IrValueParameter, data: Owner?): IrStatement =
        if (declaration.isNoinline) {
            inlining(false) { super.visitValueParameter(declaration, data) }
        } else {
            super.visitValueParameter(declaration, data)
        }

    override fun visitCall(expression: IrCall, data: Owner?): IrElement {
        expression.transformChildren(this, data)
        if (inlined || data == null || !symbols.isCallSite(expression.symbol.owner)) return expression
        val field = data.field ?: data.container.createLoggerField().also { data.field = it }
        return IrGetFieldImpl(expression.startOffset, expression.endOffset, field.symbol, field.type)
    }

    private inline fun inlining(value: Boolean, transform: () -> IrStatement): IrStatement {
        val enclosing = inlined
        inlined = value
        val result = transform()
        inlined = enclosing
        return result
    }

    private fun IrDeclarationContainer.lowerInto(owner: Owner) {
        transformChildren(this@LoggerFieldLowering, owner)
        owner.field?.let { declarations.add(0, it) }
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
            origin = GENERATED_LOGGER_FIELD
        }
        field.parent = this
        val builder = DeclarationIrBuilder(context, field.symbol, SYNTHETIC_OFFSET, SYNTHETIC_OFFSET)
        field.initializer = context.irFactory.createExpressionBody(
            builder.startOffset,
            builder.endOffset,
            builder.irCall(symbols.forCaller).apply { arguments[0] = builder.irGetObject(symbols.logRegistry) },
        )
        generated.getOrPut(file) { mutableSetOf() } += field
        return field
    }

    private fun IrDeclarationContainer.fieldVisibility(): DescriptorVisibility = when {
        this is IrClass && isInterface -> DescriptorVisibilities.PUBLIC
        isJvm -> JavaDescriptorVisibilities.PACKAGE_VISIBILITY
        else -> DescriptorVisibilities.PRIVATE
    }
}
