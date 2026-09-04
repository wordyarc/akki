@file:OptIn(UnsafeDuringIrConstructionAPI::class)

package dev.ashenarx.akki.compiler

import org.jetbrains.kotlin.backend.common.FileLoweringPass
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationContainer
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.expressions.IrGetField
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.render
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.acceptVoid

internal class LoweringInvariants : FileLoweringPass, IrVisitorVoid() {
    private val visible = mutableSetOf<IrField>()

    override fun lower(irFile: IrFile) {
        irFile.acceptVoid(this)
    }

    override fun visitElement(element: IrElement): Unit = element.acceptChildrenVoid(this)

    override fun visitFile(declaration: IrFile): Unit = declaration.checkOwnLogger()

    override fun visitClass(declaration: IrClass): Unit = declaration.checkOwnLogger()

    override fun visitGetField(expression: IrGetField) {
        val field = expression.symbol.owner
        check(field.origin != GENERATED_LOGGER_FIELD || field in visible) {
            "${field.render()} is read but not declared in ${field.parent.render()}: " +
                "the generated logger is missing from the declaration it is named after"
        }
        expression.acceptChildrenVoid(this)
    }

    private fun IrDeclarationContainer.checkOwnLogger() {
        val loggers = declarations.filterIsInstance<IrField>().filter { it.origin == GENERATED_LOGGER_FIELD }
        check(loggers.size <= 1) {
            "${render()} declares ${loggers.size} generated loggers instead of one: " +
                loggers.joinToString { it.render() }
        }
        visible += loggers
        acceptChildrenVoid(this@LoweringInvariants)
        visible -= loggers.toSet()
    }
}
