package ir.beigirad.junit_baseline_extension

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.maps.shouldContainExactly
import ir.beigirad.junit_baseline_extension.BaselineExtension.Companion.ARG_RECORD
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

data class Baseline(
    private val projectRoot: Path,
) {
    private val failures = ConcurrentHashMap<String, String>()

    private val adapter = Moshi.Builder().build().adapter<Map<String, String>>(
        Types.newParameterizedType(Map::class.java, String::class.java, String::class.java)
    )

    fun recordFailure(testId: String, errorMessage: String?) {
        failures[testId] = sanitizeMessage(errorMessage)
    }

    @TestOnly
    internal fun sanitizeMessage(message: String?): String =
        message.orEmpty()
            .replace(projectRoot.absolutePathString(), "")
            .trim()
            .ifEmpty { "No message" }

    @TestOnly
    internal fun write(identifier: String) {
        val file = getBaselineFile(identifier)
        try {
            val json = adapter.toJson(failures)
            file.writeText(json)
        } catch (e: Exception) {
            throw IllegalStateException("Failed to write baseline: ${file.absolutePathString()}", e)
        }
    }

    @TestOnly
    internal fun read(identifier: String): Map<String, String> {
        val file = getBaselineFile(identifier)
        if (!file.exists()) {
            return emptyMap()
        }

        return try {
            adapter.fromJson(file.readText()) ?: emptyMap()
        } catch (e: Exception) {
            throw IllegalStateException(
                "Corrupted baseline: ${file.absolutePathString()}. Re-record with -D$ARG_RECORD=true",
                e
            )
        }
    }

    @TestOnly
    internal fun compare(identifier: String, expected: Map<String, String>) {
        val file = getBaselineFile(identifier)
        assertSoftly {
            withClue(buildString {
                val newFailures = failures.filterKeys { it !in expected.keys }
                appendLine("New failures not in baseline (${newFailures.size}):")
                newFailures.forEach { (testId, message) ->
                    appendLine("  $testId: $message")
                }
            }) {
                failures.filterKeys { it !in expected.keys }.shouldBeEmpty()
            }
            withClue(buildString {
                appendLine("Baseline mismatch. Update with: ./gradlew test -D$ARG_RECORD=true")
                appendLine("Baseline: file://${file.absolutePathString()}")
            }) {
                failures shouldContainExactly expected
            }
        }
    }

    fun assertOrWrite(identifier: String, recordMode: Boolean) {
        if (recordMode) {
            write(identifier)
            return
        }

        val expected = read(identifier)
        compare(identifier, expected)
    }

    private fun getBaselineFile(identifier: String): Path {
        val fileName = "baseline-${identifier.replace(" ", "-")}.json"
        return projectRoot.resolve(fileName)
    }
}
