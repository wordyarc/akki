@file:OptIn(UnsafeDuringIrConstructionAPI::class)

package dev.ashenarx.akki.compiler

import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrDeclarationContainer
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetField
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.IrPropertyReference
import org.jetbrains.kotlin.ir.expressions.IrRichPropertyReference
import org.jetbrains.kotlin.ir.expressions.impl.IrBlockImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetFieldImpl
import org.jetbrains.kotlin.ir.symbols.IrFieldSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid

internal class LoggerAliasLowering : IrElementTransformerVoid() {
    override fun visitFile(declaration: IrFile): IrFile {
        val aliases = declaration.loggerAliases()
        if (aliases.isEmpty()) return declaration
        declaration.transformChildren(AliasReader(aliases), null)
        aliases.keys.forEach { (it.parent as? IrDeclarationContainer)?.declarations?.remove(it) }
        return declaration
    }

    private fun IrFile.loggerAliases(): Map<IrProperty, IrField> {
        val aliases = mutableMapOf<IrProperty, IrField>()
        val referenced = mutableSetOf<IrProperty>()
        acceptChildrenVoid(object : IrVisitorVoid() {
            override fun visitElement(element: IrElement): Unit = element.acceptChildrenVoid(this)

            override fun visitProperty(declaration: IrProperty) {
                declaration.aliasedLoggerField()?.let { aliases[declaration] = it }
                declaration.acceptChildrenVoid(this)
            }

            override fun visitPropertyReference(expression: IrPropertyReference) {
                referenced += expression.symbol.owner
                expression.acceptChildrenVoid(this)
            }

            override fun visitRichPropertyReference(expression: IrRichPropertyReference) {
                (expression.reflectionTargetSymbol?.owner as? IrProperty)?.let { referenced += it }
                expression.acceptChildrenVoid(this)
            }
        })
        return aliases - referenced
    }

    private fun IrProperty.aliasedLoggerField(): IrField? {
        val getter = getter
        if (isVar || isDelegated || isExpect || setter != null) return null
        if (!DescriptorVisibilities.isPrivate(visibility)) return null
        if (getter != null && getter.origin != IrDeclarationOrigin.DEFAULT_PROPERTY_ACCESSOR) return null
        val alias = backingField?.initializer?.expression as? IrGetField ?: return null
        return alias.symbol.owner.takeIf { it.origin == LOGGER_FIELD }
    }

    private class AliasReader(aliases: Map<IrProperty, IrField>) : IrElementTransformerVoid() {
        private val byField: Map<IrFieldSymbol, IrField> =
            aliases.entries.mapNotNull { (alias, field) -> alias.backingField?.symbol?.to(field) }.toMap()

        private val byGetter: Map<IrSimpleFunctionSymbol, IrField> =
            aliases.entries.mapNotNull { (alias, field) -> alias.getter?.symbol?.to(field) }.toMap()

        override fun visitGetField(expression: IrGetField): IrExpression {
            val field = byField[expression.symbol] ?: return super.visitGetField(expression)
            return read(expression, field, expression.receiver?.transform(this, null))
        }

        override fun visitCall(expression: IrCall): IrExpression {
            val field = byGetter[expression.symbol] ?: return super.visitCall(expression)
            return read(expression, field, expression.arguments.singleOrNull()?.transform(this, null))
        }

        private fun read(at: IrExpression, field: IrField, receiver: IrExpression?): IrExpression {
            val read = IrGetFieldImpl(at.startOffset, at.endOffset, field.symbol, field.type)
            if (receiver == null || receiver is IrGetValue) return read
            return IrBlockImpl(at.startOffset, at.endOffset, field.type, null, listOf(receiver, read))
        }
    }
}
