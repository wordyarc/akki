package dev.ashenarx.akki.compiler.fir

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirDeclarationChecker
import org.jetbrains.kotlin.fir.analysis.checkers.directOverriddenFunctionsSafe
import org.jetbrains.kotlin.fir.declarations.FirNamedFunction
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

internal object LoggerOverrideChecker : FirDeclarationChecker<FirNamedFunction>(MppCheckerKind.Common) {
    private val LOGGER_ID = ClassId(FqName("dev.ashenarx.akki"), Name.identifier("Logger"))
    private val LEVELS = setOf("trace", "debug", "info", "warn", "error")

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirNamedFunction) {
        val name = declaration.name.asString()
        if (name !in LEVELS) return
        val overridesLogger = declaration.symbol
            .directOverriddenFunctionsSafe(context)
            .any { it.callableId.classId == LOGGER_ID }
        if (!overridesLogger) return
        reporter.reportOn(declaration.source, AkkiErrors.LOGGER_LEVEL_METHOD_OVERRIDDEN, name)
    }
}
