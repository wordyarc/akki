package io.akki.compiler.test

import org.jetbrains.kotlin.generators.dsl.junit5.generateTestGroupSuiteWithJUnit5

fun main(args: Array<String>) {
    generateTestGroupSuiteWithJUnit5(args) {
        testGroup(testsRoot = args[0], testDataRoot = args[1]) {
            testClass<AbstractAkkiDiagnosticsTest> {
                model("diagnostics")
            }
            testClass<AbstractAkkiBoxTest> {
                model("box")
                model("names")
                model("transparent")
                model("semantics")
            }
            testClass<AbstractAkkiPluginlessBoxTest> {
                model("transparent")
                model("pluginless")
                model("semantics")
            }
            testClass<AbstractAkkiClippedBoxTest> {
                model("semantics")
            }
        }
    }
}
