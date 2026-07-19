package ir.beigirad.junitbaselineextension

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertTrue

class BaselineTest {

    @TempDir
    lateinit var rootDir: Path

    @TempDir
    lateinit var baselineOutput: Path

    @Test
    fun `exceptions should be sanitized from rootDir`() {
        val errorMessage = "${rootDir.toAbsolutePath()}/some/file.kt:10 - Test failed"

        val exception = Baseline.ExceptionWrapper(Exception(errorMessage))
        val sanitizedMessage = exception.sanitizedMessages(rootDir).single()

        sanitizedMessage shouldNotContain rootDir.toAbsolutePath().toString()
        sanitizedMessage shouldBe "/some/file.kt:10 - Test failed"
    }

    @Test
    fun `write should create baseline file with recorded failures`() {
        val baseline = Baseline(identifier = "my-test", projectRoot = rootDir, baselineOutputParent = baselineOutput)
        baseline.recordFailure("test-1", Throwable("Error 1\nError 1 line2"))
        baseline.recordFailure("test-2", Throwable("Error 2"))

        baseline.write()

        val file = baselineOutput.resolve("baseline-my-test.json")
        file.exists() shouldBe true

        val content = file.readText()
        content shouldBeEqual """
            {
              "test-2": [
                "Error 2"
              ],
              "test-1": [
                "Error 1",
                "Error 1 line2"
              ]
            }
        """.trimIndent()
    }

    @Test
    fun `write should skipped with empty failures`() {
        val before = Baseline(identifier = "empty-test", projectRoot = rootDir, baselineOutputParent = baselineOutput)

        val file = baselineOutput.resolve("baseline-empty-test.json")

        before.write()
        file.exists() shouldBe false
    }

    @Test
    fun `write should delete baseline file with empty failures`() {
        val testIdentifier = "my-test"

        val before = Baseline(identifier = testIdentifier, projectRoot = rootDir, baselineOutputParent = baselineOutput)
        before.recordFailure("test-1", Throwable("Error 1\nError 1 line2"))
        before.recordFailure("test-2", Throwable("Error 2"))

        val file = baselineOutput.resolve("baseline-my-test.json")

        // create and verify baseline existence
        before.write()
        file.exists() shouldBe true

        // verify its deletion
        val after = Baseline(identifier = testIdentifier, projectRoot = rootDir, baselineOutputParent = baselineOutput)
        after.write()
        file.exists() shouldBe false
    }

    @Test
    fun `write should replace spaces with dashes in identifier`() {
        val baseline =
            Baseline(identifier = "my test identifier", projectRoot = rootDir, baselineOutputParent = baselineOutput)
        baseline.recordFailure("test-1", Throwable("Error 1"))

        baseline.write()

        val file = baselineOutput.resolve("baseline-my-test-identifier.json")
        file.exists() shouldBe true
    }

    @Test
    fun `read should return empty map when baseline file does not exist`() {
        val baseline =
            Baseline(identifier = "sample-identifier", projectRoot = rootDir, baselineOutputParent = baselineOutput)

        val result = baseline.read()

        result.shouldBeEmpty()
    }

    @Test
    fun `read should load failures from existing baseline file`() {
        baselineOutput.resolve("baseline-test-identifier.json").apply {
            writeText(
                """
                {
                    "test-2":[
                        "Error 2"
                    ],
                    "test-1":[
                        "Error 1"
                    ]
                }
            """.trimIndent()
            )
        }

        val baseline =
            Baseline(identifier = "test-identifier", projectRoot = rootDir, baselineOutputParent = baselineOutput)
        val result = baseline.read()

        result shouldContainExactly mapOf("test-1" to listOf("Error 1"), "test-2" to listOf("Error 2"))
    }

    @Test
    fun `read should throw exception for corrupted baseline file`() {
        val file = baselineOutput.resolve("baseline-corrupted.json")
        file.writeText("invalid json {")

        val baseline = Baseline(identifier = "corrupted", projectRoot = rootDir, baselineOutputParent = baselineOutput)

        val exception = shouldThrow<IllegalStateException> {
            baseline.read()
        }

        exception.message shouldContain "Corrupted baseline"
        exception.message shouldContain "Re-record with -Dbaseline.record=true"
    }

    @Test
    fun `compare should pass when failures match expected`() {
        val baseline =
            Baseline(identifier = "test-identifier", projectRoot = rootDir, baselineOutputParent = baselineOutput)
        baseline.recordFailure("test-1", Throwable("Error 1"))
        baseline.recordFailure("test-2", Throwable("Error 2"))

        val expected = mapOf("test-1" to listOf("Error 1"), "test-2" to listOf("Error 2"))

        baseline.compare(expected)
    }

    @Test
    fun `compare should fail when new failures appear`() {
        val baseline =
            Baseline(identifier = "test-identifier", projectRoot = rootDir, baselineOutputParent = baselineOutput)
        baseline.recordFailure("test-1", Throwable("Error 1"))

        val expected = mapOf("test-1" to listOf("Error 1"), "test-2" to listOf("Error 2"))

        val exception = shouldThrow<Throwable> {
            baseline.compare(expected)
        }

        exception.message shouldContain "Baseline mismatch"
    }

    @Test
    fun `assertOrWrite should write when recordMode is true`() {
        val baseline =
            Baseline(identifier = "test-identifier", projectRoot = rootDir, baselineOutputParent = baselineOutput)
        baseline.recordFailure("test-1", Throwable("Error 1"))

        baseline.assertOrWrite(recordMode = true)

        val file = baselineOutput.resolve("baseline-test-identifier.json")
        file.exists() shouldBe true
    }

    @Test
    fun `assertOrWrite should compare when recordMode is false`() {
        val baseline =
            Baseline(identifier = "test-identifier", projectRoot = rootDir, baselineOutputParent = baselineOutput)
        baseline.recordFailure("test-1", Throwable("Error 1"))
        baseline.write()

        val newBaseline =
            Baseline(identifier = "test-identifier", projectRoot = rootDir, baselineOutputParent = baselineOutput)
        newBaseline.recordFailure("test-1", Throwable("Error 1"))

        newBaseline.assertOrWrite(recordMode = false)
    }

    @Test
    fun `assertOrWrite should fail comparison when failures mismatch`() {
        val baseline =
            Baseline(identifier = "test-identifier", projectRoot = rootDir, baselineOutputParent = baselineOutput)
        baseline.recordFailure("test-1", Throwable("Error 1"))
        baseline.write()

        val newBaseline =
            Baseline(identifier = "test-identifier", projectRoot = rootDir, baselineOutputParent = baselineOutput)
        newBaseline.recordFailure("test-1", Throwable("Different Error"))

        shouldThrow<Throwable> {
            newBaseline.assertOrWrite(recordMode = false)
        }
    }

    @Test
    fun `baseline exceptions mentions actual exception`() {
        val baseline = Baseline("test-identifier", projectRoot = rootDir, baselineOutputParent = baselineOutput)
        baseline.recordFailure(
            "test-me",
            Exception(
                "Normal exception",
                RuntimeException(
                    "Runtime of exception",
                    IllegalStateException(
                        "Root Cause"
                    )
                )
            )
        )

        val throwableLines = shouldThrow<Throwable> {
            baseline.compare(emptyMap())
        }.message.orEmpty().lines()

        val expectedLines = listOf(
            "Baseline mismatch",
            "Baseline: file://${baselineOutput.absolutePathString()}/baseline-test-identifier.json",
            "Update with : ./ gradlew test - Dbaseline.record=true",
            "Test cases: 1",
            "0) Normal exception",
            "java.lang.Exception: Normal exception",
            "at ir.beigirad.junitbaselineextension.BaselineTest.baseline exceptions mentions actual exception(BaselineTest.kt",
            "Caused by: java.lang.RuntimeException: Runtime of exception",
            "at ir.beigirad.junitbaselineextension.BaselineTest.baseline exceptions mentions actual exception(BaselineTest.kt",
            "Caused by: java.lang.IllegalStateException: Root Cause",
            "at ir.beigirad.junitbaselineextension.BaselineTest.baseline exceptions mentions actual exception(BaselineTest.kt",
        )

        var searchFrom = 0
        for ((index, expectedLine) in expectedLines.withIndex()) {
            var matchIndex = -1
            for (i in searchFrom until throwableLines.size) {
                if (throwableLines[i].trim().startsWith(expectedLine)) {
                    matchIndex = i
                    break
                }
            }
            println("[${index + 1}/${expectedLines.size}] ${if (matchIndex != -1) "✓" else "✗"} \"$expectedLine\" -> line $matchIndex")
            assertTrue("No match found for \"$expectedLine\"") { matchIndex != -1 }
            searchFrom = matchIndex + 1
        }
    }
}
