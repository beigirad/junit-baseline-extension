package ir.beigirad.junitbaselineextension

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import ir.beigirad.junitbaselineextension.BaselineExtension.Companion.ARG_RECORD
import org.jetbrains.annotations.TestOnly
import java.io.PrintWriter
import java.io.Writer
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

data class Baseline(
    private val identifier: String,
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
    internal fun write() {
        val file = getBaselineFile()
        try {
            if (failures.isEmpty()) {
                file.deleteIfExists()
                return
            }
            baselineOutputParent.createDirectories() // make sure the parent exists

            val json = adapter.toJson(failures.mapValues { it.value.sanitizedMessages(projectRoot) })
            file.writeText(json)
        } catch (e: Exception) {
            throw IllegalStateException("Failed to write baseline: ${file.absolutePathString()}", e)
        }
    }

    @TestOnly
    internal fun read(): Map<String, List<String>> {
        val file = getBaselineFile()
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
    internal fun compare(expected: Map<String, List<String>>) {
        val file = getBaselineFile()

        val actual: Map<String, List<String>> = failures.mapValues { it.value.sanitizedMessages(projectRoot) }

        if (actual.any { (key, value) -> expected[key] != value } || expected.any { (key, value) -> actual[key] != value })
            throw BaselineException(file, failures.map { it.value.throwable })
    }

    fun assertOrWrite(recordMode: Boolean) {
        if (recordMode) {
            write()
            return
        }

        val expected = read()
        compare(expected)
    }

    private fun getBaselineFile(): Path {
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

private class BaselineException(
    private val baselinePath: Path,
    private val children: List<Throwable>
) : Throwable() {
    // omit the stacktrace to hide baseline stacktrace
    override fun getStackTrace(): Array<out StackTraceElement?>? = emptyArray()

    override val message: String
        get() = buildString {
            appendLine("Baseline mismatch")
            appendLine("Baseline: file://${baselinePath.absolutePathString()}")
            appendLine("Update with : ./ gradlew test - D$ARG_RECORD=true")
            appendLine()
            appendLine("Test cases: ${children.size}")
            children.forEachIndexed { index, throwable ->
                appendLine("$index) ${throwable.message}")
                throwable.printStackTrace(StackTraceWriterWrapper(this))
            }
        }

    override fun toString() = message
    private class StackTraceWriterWrapper(
        builder: StringBuilder
    ) : PrintWriter(
        object : Writer() {
            override fun write(cbuf: CharArray, off: Int, len: Int) {
                builder.append(cbuf, off, len)
            }

            override fun flush() = Unit
            override fun close() = Unit
        })
}

