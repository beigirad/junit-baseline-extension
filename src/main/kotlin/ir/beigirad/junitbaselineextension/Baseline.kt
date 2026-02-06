package ir.beigirad.junitbaselineextension

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.matchers.maps.shouldContainExactly
import ir.beigirad.junitbaselineextension.BaselineExtension.Companion.ARG_RECORD
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

data class Baseline(
    private val projectRoot: Path? = null,
    private val baselineOutputParent: Path,
) {
    private val failures = ConcurrentHashMap<String, ExceptionWrapper>()

    private val adapter = Moshi.Builder().build().adapter<Map<String, List<String>>>(
        Types.newParameterizedType(
            Map::class.java,
            String::class.java,
            Types.newParameterizedType(List::class.java, String::class.java)
        )
    ).indent("  ")

    fun recordFailure(testId: String, throwable: Throwable) {
        failures[testId] = ExceptionWrapper(throwable)
    }

    @TestOnly
    internal fun write(identifier: String) {
        val file = getBaselineFile(identifier)
        try {
            baselineOutputParent.createDirectories() // make sure the parent exists

            val json = adapter.toJson(failures.mapValues { it.value.sanitizedMessages(projectRoot) })
            file.writeText(json)
        } catch (e: Exception) {
            throw IllegalStateException("Failed to write baseline: ${file.absolutePathString()}", e)
        }
    }

    @TestOnly
    internal fun read(identifier: String): Map<String, List<String>> {
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
    internal fun compare(identifier: String, expected: Map<String, List<String>>) {
        val file = getBaselineFile(identifier)

        val actual: Map<String, List<String>> = failures.mapValues { it.value.sanitizedMessages(projectRoot) }

        assertSoftly {
            withClue(buildString {
                appendLine("Baseline mismatch. Update with: ./gradlew test -D$ARG_RECORD=true")
                appendLine("Baseline: file://${file.absolutePathString()}")
            }) {
                actual shouldContainExactly expected
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
        return baselineOutputParent.resolve(fileName)
    }

    internal class ExceptionWrapper(val throwable: Throwable) {

        fun sanitizedMessages(projectRoot: Path? = null): List<String> {
            val messageLines = throwable.message?.lines().orEmpty()
            return if (projectRoot == null)
                messageLines
            else
                messageLines.map { it.replace(projectRoot.absolutePathString(), "") }
        }
    }
}
