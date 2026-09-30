package akki.buildlogic

import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.w3c.dom.Element

@CacheableTask
abstract class KoverMarkdownReport : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val xmlReport: RegularFileProperty

    @get:OutputFile
    abstract val markdownReport: RegularFileProperty

    @TaskAction
    fun generate() {
        val document = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }.newDocumentBuilder().parse(xmlReport.get().asFile)
        val xpath = XPathFactory.newInstance().newXPath()
        val markdown = buildString {
            appendLine("## Coverage (Kover)")
            appendLine()
            appendLine("| Metric | Coverage | Covered / Total |")
            appendLine("| --- | ---: | ---: |")
            for ((type, label) in listOf("LINE" to "Lines", "BRANCH" to "Branches", "INSTRUCTION" to "Instructions")) {
                val counter = requireNotNull(
                    xpath.evaluate("/report/counter[@type='$type']", document, XPathConstants.NODE) as? Element,
                ) { "akki: Kover XML report has no $type total counter" }
                val covered = counter.getAttribute("covered").toLong()
                val missed = counter.getAttribute("missed").toLong()
                require(covered >= 0 && missed >= 0) { "akki: Kover $type counters must be non-negative" }
                val total = covered + missed
                val coverage = if (total == 0L) "N/A" else "%.2f%%".format(Locale.ROOT, 100.0 * covered / total)
                appendLine("| $label | $coverage | $covered / $total |")
            }
            appendLine()
            appendLine("Aggregated coverage for modules included in the root Kover report.")
        }
        markdownReport.get().asFile.apply {
            parentFile.mkdirs()
            writeText(markdown)
        }
    }
}
