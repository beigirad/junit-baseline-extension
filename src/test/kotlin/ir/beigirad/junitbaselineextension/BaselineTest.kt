package ir.beigirad.junitbaselineextension

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class BaselineTest {

    @TempDir
    lateinit var rootDir: Path

    @TempDir
    lateinit var baselineOutput: Path

    @Test
    fun `sanitizeMessage should remove project path from error message`() {
        val baseline = Baseline(projectRoot = rootDir, baselineOutputParent = baselineOutput)
        val errorMessage = "${rootDir.toAbsolutePath()}/some/file.kt:10 - Test failed"

        val result = baseline.sanitizeMessage(errorMessage)

        result shouldNotContain rootDir.toAbsolutePath().toString()
        result shouldBe "/some/file.kt:10 - Test failed"
    }

    @Test
    fun `sanitizeMessage should handle null, empty and whitespace messages`() {
        val baseline = Baseline(projectRoot = rootDir, baselineOutputParent = baselineOutput)

        assertSoftly {
            withClue("with null message") {
                baseline.sanitizeMessage(null) shouldBe "No message"
            }
            withClue("with empty message") {
                baseline.sanitizeMessage("") shouldBe "No message"
            }
            withClue("with whitespace message") {
                baseline.sanitizeMessage("    ") shouldBe "No message"
            }
        }
    }

    @Test
    fun `write should create baseline file with recorded failures`() {
        val baseline = Baseline(projectRoot = rootDir, baselineOutputParent = baselineOutput)
        baseline.recordFailure("test-1", "Error 1")
        baseline.recordFailure("test-2", "Error 2")

        baseline.write("my-test")

        val file = baselineOutput.resolve("baseline-my-test.json")
        file.exists() shouldBe true

        val content = file.readText()
        content shouldBe """{"test-2":"Error 2","test-1":"Error 1"}"""
    }

    @Test
    fun `write should replace spaces with dashes in identifier`() {
        val baseline = Baseline(projectRoot = rootDir, baselineOutputParent = baselineOutput)
        baseline.recordFailure("test-1", "Error 1")

        baseline.write("my test identifier")

        val file = baselineOutput.resolve("baseline-my-test-identifier.json")
        file.exists() shouldBe true
    }

    @Test
    fun `read should return empty map when baseline file does not exist`() {
        val baseline = Baseline(projectRoot = rootDir, baselineOutputParent = baselineOutput)

        val result = baseline.read("non-existent")

        result.shouldBeEmpty()
    }

    @Test
    fun `read should load failures from existing baseline file`() {
        baselineOutput.resolve("baseline-test-identifier.json").apply {
            writeText("""{"test-2":"Error 2","test-1":"Error 1"}""")
        }

        val baseline = Baseline(projectRoot = rootDir, baselineOutputParent = baselineOutput)
        val result = baseline.read("test-identifier")

        result shouldContainExactly mapOf("test-1" to "Error 1", "test-2" to "Error 2")
    }

    @Test
    fun `read should throw exception for corrupted baseline file`() {
        val file = baselineOutput.resolve("baseline-corrupted.json")
        file.writeText("invalid json {")

        val baseline = Baseline(projectRoot = rootDir, baselineOutputParent = baselineOutput)

        val exception = shouldThrow<IllegalStateException> {
            baseline.read("corrupted")
        }

        exception.message shouldContain "Corrupted baseline"
        exception.message shouldContain "Re-record with -Dbaseline.record=true"
    }

    @Test
    fun `compare should pass when failures match expected`() {
        val baseline = Baseline(projectRoot = rootDir, baselineOutputParent = baselineOutput)
        baseline.recordFailure("test-1", "Error 1")
        baseline.recordFailure("test-2", "Error 2")

        val expected = mapOf("test-1" to "Error 1", "test-2" to "Error 2")

        baseline.compare("test-identifier", expected)
    }

    @Test
    fun `compare should fail when new failures appear`() {
        val baseline = Baseline(projectRoot = rootDir, baselineOutputParent = baselineOutput)
        baseline.recordFailure("test-1", "Error 1")
        baseline.recordFailure("test-2", "Error 2")

        val expected = mapOf("test-1" to "Error 1")

        val exception = shouldThrow<AssertionError> {
            baseline.compare("test-identifier", expected)
        }

        exception.message shouldContain "New failures not in baseline"
        exception.message shouldContain "test-2"
    }

    @Test
    fun `compare should fail when failures are missing`() {
        val baseline = Baseline(projectRoot = rootDir, baselineOutputParent = baselineOutput)
        baseline.recordFailure("test-1", "Error 1")

        val expected = mapOf("test-1" to "Error 1", "test-2" to "Error 2")

        val exception = shouldThrow<AssertionError> {
            baseline.compare("test-identifier", expected)
        }

        exception.message shouldContain "Baseline mismatch"
    }

    @Test
    fun `assertOrWrite should write when recordMode is true`() {
        val baseline = Baseline(projectRoot = rootDir, baselineOutputParent = baselineOutput)
        baseline.recordFailure("test-1", "Error 1")

        baseline.assertOrWrite("test-identifier", recordMode = true)

        val file = baselineOutput.resolve("baseline-test-identifier.json")
        file.exists() shouldBe true
    }

    @Test
    fun `assertOrWrite should compare when recordMode is false`() {
        val baseline = Baseline(projectRoot = rootDir, baselineOutputParent = baselineOutput)
        baseline.recordFailure("test-1", "Error 1")
        baseline.write("test-identifier")

        val newBaseline = Baseline(projectRoot = rootDir, baselineOutputParent = baselineOutput)
        newBaseline.recordFailure("test-1", "Error 1")

        newBaseline.assertOrWrite("test-identifier", recordMode = false)
    }

    @Test
    fun `assertOrWrite should fail comparison when failures mismatch`() {
        val baseline = Baseline(projectRoot = rootDir, baselineOutputParent = baselineOutput)
        baseline.recordFailure("test-1", "Error 1")
        baseline.write("test-identifier")

        val newBaseline = Baseline(projectRoot = rootDir, baselineOutputParent = baselineOutput)
        newBaseline.recordFailure("test-1", "Different Error")

        shouldThrow<AssertionError> {
            newBaseline.assertOrWrite("test-identifier", recordMode = false)
        }
    }
}
