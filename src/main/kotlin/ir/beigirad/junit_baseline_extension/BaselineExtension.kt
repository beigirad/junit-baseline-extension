package ir.beigirad.junit_baseline_extension

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.maps.shouldContainExactly
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler
import java.io.File
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap

class BaselineExtension : TestExecutionExceptionHandler, AfterEachCallback, AfterAllCallback {

    private val adapter = Moshi.Builder().build().adapter<Map<String, String>>(
        Types.newParameterizedType(Map::class.java, String::class.java, String::class.java)
    )

    // used to remove environment-specific details from error messages
    private val projectRootPath: String = System.getProperty("projectDir").orEmpty()

    private val failedTests = ConcurrentHashMap<String, String>()

    override fun handleTestExecutionException(context: ExtensionContext, throwable: Throwable) {
        // record test failures without throwing, for baseline comparison instead of `throw throwable`
        val sanitizedMessage = sanitizeErrorMessage(throwable.message)
        failedTests[context.uniqueId] = sanitizedMessage
    }

    override fun afterEach(context: ExtensionContext) {
        // ignore printing baseline when it applied by class (not by method!)
        if (context.isAppliedByClass()) return

        assertOrWriteOnDisk(context.requiredTestClass.simpleName + "-" + context.requiredTestMethod.name.hashCode())
    }

    override fun afterAll(context: ExtensionContext) {
        // avoid writing baselines for nested classes (they are handled in parent)
        if (context.isNestedTestClass()) return

        assertOrWriteOnDisk(context.requiredTestClass.simpleName)
    }

    private fun sanitizeErrorMessage(message: String?): String =
        message.orEmpty()
            .replace(projectRootPath, "")
            .trim()
            .ifEmpty { "No message" }

    private fun assertOrWriteOnDisk(reportName: String) {
        val reportFileName = "baseline-${reportName.replace(" ", "-")}.json"
        val baselineFile = Paths.get(reportFileName).toFile()

        if (isRecordingBaseline()) {
            writeBaseline(baselineFile)
            return
        }

        val expectedFailures = readBaseline(baselineFile)
        compareWithBaseline(baselineFile, expectedFailures)
    }

    private fun isRecordingBaseline(): Boolean =
        System.getProperty(PROPERTY_RECORD_BASELINE) == "true"

    private fun writeBaseline(baselineFile: File) {
        try {
            val json = adapter.toJson(failedTests)
            baselineFile.writeText(json)
        } catch (e: Exception) {
            throw IllegalStateException("Failed to write baseline: ${baselineFile.absolutePath}", e)
        }
    }

    private fun readBaseline(baselineFile: File): Map<String, String> {
        if (!baselineFile.exists()) {
            return emptyMap()
        }

        return try {
            adapter.fromJson(baselineFile.readText()) ?: emptyMap()
        } catch (e: Exception) {
            throw IllegalStateException(
                "Corrupted baseline: ${baselineFile.absolutePath}. Re-record with -D$PROPERTY_RECORD_BASELINE=true",
                e
            )
        }
    }

    private fun compareWithBaseline(baselineFile: File, expectedFailures: Map<String, String>) {
        assertSoftly {
            withClue(buildString {
                val newFailures = failedTests.filterKeys { it !in expectedFailures.keys }
                appendLine("New failures not in baseline (${newFailures.size}):")
                newFailures.forEach { (testId, message) ->
                    appendLine("  $testId: $message")
                }
            }) {
                failedTests.filterKeys { it !in expectedFailures.keys }.shouldBeEmpty()
            }
            withClue(buildString {
                appendLine("Baseline mismatch. Update with: ./gradlew test -D$PROPERTY_RECORD_BASELINE=true")
                appendLine("Baseline: file://${baselineFile.absolutePath}")
            }) {
                failedTests shouldContainExactly expectedFailures
            }
        }
    }

    private fun ExtensionContext.isNestedTestClass(): Boolean =
        parent.flatMap { it.testClass }
            .map { parentClass ->
                val currentClass = this.requiredTestClass
                currentClass.enclosingClass != null && currentClass.enclosingClass == parentClass
            }
            .orElse(false)

    private fun ExtensionContext.isAppliedByClass(): Boolean =
        testClass.orElse(null)
            .getAnnotationsByType(ExtendWith::class.java)
            .any { it.value.contains(BaselineExtension::class) }

    companion object {
        private const val PROPERTY_RECORD_BASELINE = "recordBaseline"
    }
}
