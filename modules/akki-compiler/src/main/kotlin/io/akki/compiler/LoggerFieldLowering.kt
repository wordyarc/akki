@file:OptIn(UnsafeDuringIrConstructionAPI::class)

package io.akki.compiler

import org.jetbrains.kotlin.backend.common.FileLoweringPass
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.builtins.jvm.JavaToKotlinClassMap
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.DescriptorVisibility
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.builders.declarations.buildClass
import org.jetbrains.kotlin.ir.builders.declarations.buildField
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irExprBody
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationContainer
import org.jetbrains.kotlin.ir.declarations.IrDeclarationParent
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrClassReference
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetObjectValue
import org.jetbrains.kotlin.ir.expressions.impl.IrBlockImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetFieldImpl
import org.jetbrains.kotlin.ir.expressions.isUnchanging
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.SYNTHETIC_OFFSET
import org.jetbrains.kotlin.ir.util.classId
import org.jetbrains.kotlin.ir.util.createThisReceiverParameter
import org.jetbrains.kotlin.ir.util.isAnnotationClass
import org.jetbrains.kotlin.ir.util.isArrayOrPrimitiveArray
import org.jetbrains.kotlin.ir.util.isEnumEntry
import org.jetbrains.kotlin.ir.util.isInterface
import org.jetbrains.kotlin.ir.util.isSuspendFunction
import org.jetbrains.kotlin.ir.util.parents
import org.jetbrains.kotlin.load.java.JavaDescriptorVisibilities
import org.jetbrains.kotlin.platform.jvm.isJvm

private enum class Entry {
    CONTEXTUAL,
    NAMED,
    TYPE,
}

internal class LoggerFieldLowering(
    private val context: IrPluginContext,
    private val symbols: AkkiSymbols,
) : FileLoweringPass, IrElementTransformerVoidWithContext() {
    private val isJvm: Boolean = context.platform.isJvm()

    private val fields = mutableMapOf<IrDeclarationContainer, MutableMap<String, IrField>>()

    private val holders = mutableMapOf<IrClass, IrClass>()

    override fun lower(irFile: IrFile) {
        irFile.transform(this, null)
        fields.forEach { (container, created) -> container.declarations.addAll(0, created.values.toList()) }
        holders.forEach { (owner, holder) -> owner.declarations.add(holder) }
        fields.clear()
        holders.clear()
    }

    override fun visitCall(expression: IrCall): IrExpression {
        expression.transformChildrenVoid()
        val entry = expression.entry() ?: return expression
        if (isInlined()) return if (entry == Entry.CONTEXTUAL) expression.lookup() else expression
        val field = expression.loggerField(entry) ?: return expression
        return expression.reading(field)
    }

    private fun IrCall.entry(): Entry? = when {
        symbol == symbols.named -> Entry.NAMED
        symbol == symbols.ofType || symbol == symbols.ofReifiedType -> Entry.TYPE
        symbols.isCallSite(symbol.owner) -> Entry.CONTEXTUAL
        else -> null
    }

    private fun IrCall.loggerField(entry: Entry): IrField? = when (entry) {
        Entry.CONTEXTUAL -> contextualField()
        Entry.NAMED -> namedField()
        Entry.TYPE -> typeField()
    }

    private fun contextualField(): IrField =
        fieldOwner().declarationField(namingOwner().declarationName(isJvm), contextual = true)

    private fun IrCall.lookup(): IrExpression =
        DeclarationIrBuilder(context, currentScope!!.scope.scopeOwnerSymbol, startOffset, endOffset)
            .declarationLogger(namingOwner().declarationName(isJvm))

    private fun IrCall.namedField(): IrField? {
        val name = arguments.lastOrNull()?.stringConstant()?.takeIf { it.isNotBlank() } ?: return null
        return fieldOwner().loggerField("named:$name", contextual = false) {
            irCall(symbols.named).apply {
                arguments[0] = irGetObject(symbols.log)
                arguments[1] = irString(name)
            }
        }
    }

    private fun IrCall.typeField(): IrField? {
        val referenced = referencedClass() ?: return null
        if (isJvm && referenced.isJvmMapped()) return null
        val named = referenced.namingDeclaration() ?: return null
        return fieldOwner().declarationField(named.declarationName(isJvm), contextual = false)
    }

    private fun IrDeclarationContainer.declarationField(name: DeclarationName, contextual: Boolean): IrField =
        loggerField("declaration:${name.sourceName}|${name.jvmClassName}", contextual) { declarationLogger(name) }

    private fun IrCall.referencedClass(): IrClass? {
        val type = typeArguments.firstOrNull() ?: (arguments.lastOrNull() as? IrClassReference)?.classType
        return type?.classOrNull?.owner
    }

    private fun IrClass.isJvmMapped(): Boolean =
        symbol.isArrayOrPrimitiveArray(context.irBuiltIns) ||
            symbol.isSuspendFunction() ||
            classId?.asSingleFqName()?.let {
                JavaToKotlinClassMap.mapKotlinToJava(it.toUnsafe()) != null ||
                    JavaToKotlinClassMap.mapJavaToKotlin(it) != null
            } == true

    private fun IrClass.namingDeclaration(): IrDeclarationContainer? =
        (sequenceOf<IrDeclarationParent>(this) + parents)
            .filterIsInstance<IrDeclarationContainer>()
            .firstOrNull { it !is IrClass || !it.isHoisted() }

    private fun DeclarationIrBuilder.declarationLogger(name: DeclarationName): IrExpression =
        irCall(symbols.declarationLogger).apply {
            arguments[0] = irString(name.sourceName)
            arguments[1] = irString(name.jvmClassName)
        }

    private fun IrCall.reading(field: IrField): IrExpression {
        val read = IrGetFieldImpl(startOffset, endOffset, field.symbol, field.type)
        val effects = arguments.filterNotNull().filter { !it.isUnchanging() && it !is IrGetObjectValue }
        if (effects.isEmpty()) return read
        return IrBlockImpl(startOffset, endOffset, field.type, null, effects + read)
    }

    private fun isInlined(): Boolean = allScopes.any { (it.irElement as? IrFunction)?.isInline == true }

    private fun namingOwner(): IrDeclarationContainer =
        allScopes.asReversed().firstNotNullOfOrNull { scope ->
            (scope.irElement as? IrClass)?.takeUnless { it.isHoisted() }
        } ?: currentFile

    private fun fieldOwner(): IrDeclarationContainer {
        val owner = namingOwner() as? IrClass ?: return currentFile
        return when {
            isJvm -> holders.getOrPut(owner) { owner.createLoggerHolder() }
            owner.isJvmInterface -> currentFile
            else -> owner
        }
    }

    private val IrClass.isJvmInterface: Boolean
        get() = isInterface || isAnnotationClass

    private fun IrClass.createLoggerHolder(): IrClass = context.irFactory.buildClass {
        startOffset = SYNTHETIC_OFFSET
        endOffset = SYNTHETIC_OFFSET
        name = AkkiNames.LOGGER_HOLDER
        kind = ClassKind.CLASS
        visibility = JavaDescriptorVisibilities.PACKAGE_VISIBILITY
        modality = Modality.FINAL
        origin = GENERATED_LOGGER_HOLDER
    }.apply {
        parent = this@createLoggerHolder
        superTypes = listOf(context.irBuiltIns.anyType)
        createThisReceiverParameter()
    }

    private fun IrClass.isHoisted(): Boolean = classId == null || isCompanion || isEnumEntry

    private fun IrDeclarationContainer.loggerField(
        key: String,
        contextual: Boolean,
        initializer: DeclarationIrBuilder.() -> IrExpression,
    ): IrField {
        val created = fields.getOrPut(this) { linkedMapOf() }
        val field = created.getOrPut(key) { createLoggerField(created.size, initializer) }
        if (contextual && created.values.none { it.name == AkkiNames.LOGGER_FIELD }) field.name = AkkiNames.LOGGER_FIELD
        return field
    }

    private fun IrDeclarationContainer.createLoggerField(
        index: Int,
        initializer: DeclarationIrBuilder.() -> IrExpression,
    ): IrField {
        val field = context.irFactory.buildField {
            startOffset = SYNTHETIC_OFFSET
            endOffset = SYNTHETIC_OFFSET
            name = AkkiNames.loggerField(index)
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

    private fun fieldVisibility(): DescriptorVisibility =
        if (isJvm) JavaDescriptorVisibilities.PACKAGE_VISIBILITY else DescriptorVisibilities.PRIVATE
}
