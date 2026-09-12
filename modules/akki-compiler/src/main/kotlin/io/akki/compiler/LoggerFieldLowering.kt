@file:OptIn(UnsafeDuringIrConstructionAPI::class)

package io.akki.compiler

import org.jetbrains.kotlin.backend.common.FileLoweringPass
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.builtins.jvm.JavaToKotlinClassMap
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.DescriptorVisibility
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.builders.declarations.buildField
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irExprBody
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationContainer
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrClassReference
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetObjectValue
import org.jetbrains.kotlin.ir.expressions.impl.IrBlockImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetFieldImpl
import org.jetbrains.kotlin.ir.expressions.isUnchanging
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.SYNTHETIC_OFFSET
import org.jetbrains.kotlin.ir.util.classId
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.isEnumClass
import org.jetbrains.kotlin.ir.util.isInterface
import org.jetbrains.kotlin.ir.util.isLocal
import org.jetbrains.kotlin.ir.util.superClass
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.load.java.JavaDescriptorVisibilities
import org.jetbrains.kotlin.platform.jvm.isJvm

private const val CONTEXTUAL_KEY: String = "contextual"

internal class LoggerFieldLowering(
    private val context: IrPluginContext,
    private val symbols: AkkiSymbols,
) : FileLoweringPass, IrElementTransformerVoidWithContext() {
    private val isJvm: Boolean = context.platform.isJvm()

    private val fields = mutableMapOf<IrDeclarationContainer, MutableMap<String, IrField>>()

    override fun lower(irFile: IrFile) {
        irFile.transform(this, null)
        fields.forEach { (container, created) -> container.declarations.addAll(0, created.values.toList()) }
        fields.clear()
    }

    override fun visitCall(expression: IrCall): IrExpression {
        expression.transformChildrenVoid()
        if (isInlined()) return expression
        val field = expression.loggerField() ?: return expression
        return expression.reading(field)
    }

    private fun IrCall.loggerField(): IrField? {
        val function = symbol.owner
        return when {
            symbols.isCallSite(function) -> contextualField()
            function.symbol == symbols.named -> namedField()
            function.symbol == symbols.ofType || function.symbol == symbols.ofReifiedType -> typeField()
            else -> null
        }
    }

    private fun contextualField(): IrField? {
        val owner = fieldOwner() ?: return null
        val name = owner.declarationName(isJvm)
        return owner.loggerField(CONTEXTUAL_KEY) { declarationLogger(name) }
    }

    private fun IrCall.namedField(): IrField? {
        val name = (arguments.lastOrNull() as? IrConst)?.value as? String ?: return null
        val owner = fieldOwner() ?: return null
        return owner.loggerField("named:$name") {
            irCall(symbols.registryOf).apply {
                arguments[0] = irGetObject(symbols.logRegistry)
                arguments[1] = irString(name)
            }
        }
    }

    private fun IrCall.typeField(): IrField? {
        val referenced = referencedClass() ?: return null
        if (isJvm && referenced.isMappedToJava()) return null
        val named = referenced.namingDeclaration() ?: return null
        val name = named.declarationName(isJvm)
        val owner = fieldOwner() ?: return null
        return owner.loggerField("type:${name.source}|${name.platform}") { declarationLogger(name) }
    }

    private fun IrCall.referencedClass(): IrClass? {
        val type = typeArguments.firstOrNull() ?: (arguments.lastOrNull() as? IrClassReference)?.classType
        return type?.classOrNull?.owner
    }

    private fun IrClass.isMappedToJava(): Boolean =
        classId?.let { JavaToKotlinClassMap.mapKotlinToJava(it.asSingleFqName().toUnsafe()) } != null

    private fun IrClass.namingDeclaration(): IrDeclarationContainer? =
        generateSequence<IrElement>(this) { (it as? IrDeclaration)?.parent }
            .filterIsInstance<IrDeclarationContainer>()
            .firstOrNull { it !is IrClass || !it.isHoisted() }
            ?.takeIf { it !is IrClass || it.classId != null || it.hasAnnotation(AkkiNames.LOG_NAME_ID) }

    private fun DeclarationIrBuilder.declarationLogger(name: DeclarationName): IrExpression =
        irCall(symbols.forDeclaration).apply {
            arguments[0] = irGetObject(symbols.logRegistry)
            arguments[1] = irString(name.source)
            arguments[2] = irString(name.platform)
        }

    private fun IrCall.reading(field: IrField): IrExpression {
        val read = IrGetFieldImpl(startOffset, endOffset, field.symbol, field.type)
        val effects = arguments.filterNotNull().filter { !it.isUnchanging() && it !is IrGetObjectValue }
        if (effects.isEmpty()) return read
        return IrBlockImpl(startOffset, endOffset, field.type, null, effects + read)
    }

    private fun isInlined(): Boolean = allScopes.any { (it.irElement as? IrFunction)?.isInline == true }

    private fun fieldOwner(): IrDeclarationContainer? {
        for (scope in allScopes.asReversed()) {
            val enclosing = scope.irElement as? IrClass ?: continue
            if (enclosing.isHoisted()) continue
            return enclosing.takeIf { isJvm || !it.isInterface }
        }
        return currentFile
    }

    private fun IrClass.isHoisted(): Boolean =
        !hasAnnotation(AkkiNames.LOG_NAME_ID) && (isLocal || isCompanion || isEnumEntryBody())

    private fun IrClass.isEnumEntryBody(): Boolean = superClass?.isEnumClass == true

    private fun IrDeclarationContainer.loggerField(
        key: String,
        initializer: DeclarationIrBuilder.() -> IrExpression,
    ): IrField {
        val created = fields.getOrPut(this) { linkedMapOf() }
        return created.getOrPut(key) { createLoggerField(key, created.size, initializer) }
    }

    private fun IrDeclarationContainer.createLoggerField(
        key: String,
        index: Int,
        initializer: DeclarationIrBuilder.() -> IrExpression,
    ): IrField {
        val field = context.irFactory.buildField {
            startOffset = SYNTHETIC_OFFSET
            endOffset = SYNTHETIC_OFFSET
            name = if (key == CONTEXTUAL_KEY) AkkiNames.LOGGER_FIELD else AkkiNames.loggerField(index)
            type = symbols.loggerType
            visibility = fieldVisibility()
            isStatic = true
            isFinal = true
            origin = GENERATED_LOGGER_FIELD
        }
        field.parent = this
        field.initializer = DeclarationIrBuilder(context, field.symbol, SYNTHETIC_OFFSET, SYNTHETIC_OFFSET).run {
            irExprBody(initializer())
        }
        return field
    }

    private fun IrDeclarationContainer.fieldVisibility(): DescriptorVisibility = when {
        this is IrClass && isInterface -> DescriptorVisibilities.PUBLIC
        isJvm -> JavaDescriptorVisibilities.PACKAGE_VISIBILITY
        else -> DescriptorVisibilities.PRIVATE
    }
}
