package ir.beigirad.junitbaselineextension

import io.kotest.assertions.asClue
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.engine.discovery.DiscoverySelectors
import org.junit.platform.testkit.engine.EngineTestKit
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.readText

class BaselineExtensionTest {
    private val sampleBaselinePath = Paths.get("sample-baseline")

    @TempDir
    lateinit var rootPath: Path

    @TempDir
    lateinit var baselinePath: Path

    @Test
    fun `should record baseline when record mode is true for class-level extension`() {
        val events = EngineTestKit.engine("junit-jupiter")
            .selectors(DiscoverySelectors.selectClass(SampleClassLevelTest::class.java))
            .configurationParameter("baseline.root", rootPath.absolutePathString())
            .configurationParameter("baseline.output", baselinePath.absolutePathString())
            .configurationParameter("baseline.record", "true")
            .execute()

        withClue(events.containerEvents().debug()) {
            events.containerEvents().failed().count() shouldBe 0
        }

        val baselineFileName = "baseline-SampleClassLevelTest.json"

        baselinePath.resolve(baselineFileName).asClue {
            it.exists() shouldBe true
            it.readText() shouldBe sampleBaselinePath.resolve(baselineFileName).readText()
        }
    }

    @Test
    fun `should assert against baseline when record mode is false for class-level extension`() {
        val events = EngineTestKit.engine("junit-jupiter")
            .selectors(DiscoverySelectors.selectClass(SampleClassLevelTest::class.java))
            .configurationParameter("baseline.root", rootPath.absolutePathString())
            .configurationParameter("baseline.output", sampleBaselinePath.absolutePathString())
            .execute()

        withClue(events.containerEvents().debug()) {
            events.containerEvents().failed().count() shouldBe 0
        }
    }

    @Test
    fun `should record baseline when record mode is true for method-level extension`() {
        val events = EngineTestKit.engine("junit-jupiter")
            .selectors(DiscoverySelectors.selectClass(SampleMethodLevelTest::class.java))
            .configurationParameter("baseline.root", rootPath.absolutePathString())
            .configurationParameter("baseline.output", baselinePath.absolutePathString())
            .configurationParameter("baseline.record", "true")
            .execute()

        withClue(events.containerEvents().debug()) {
            events.containerEvents().failed().count() shouldBe 0
        }

        val baselineFileName = "baseline-SampleMethodLevelTest-first.test.-218984446.json"
        baselinePath.resolve(baselineFileName).asClue {
            it.exists() shouldBe true
            it.readText() shouldBe sampleBaselinePath.resolve(baselineFileName).readText()
        }
    }

    @Test
    fun `should assert against baseline when record mode is false for method-level extension`() {
        val events = EngineTestKit.engine("junit-jupiter")
            .selectors(DiscoverySelectors.selectClass(SampleMethodLevelTest::class.java))
            .configurationParameter("baseline.root", rootPath.absolutePathString())
            .configurationParameter("baseline.output", sampleBaselinePath.absolutePathString())
            .execute()

        withClue(events.containerEvents().debug()) {
            events.containerEvents().failed().count() shouldBe 0
        }
    }

    @Test
    fun `should propagate real exceptions and skip comparison when no baseline exists for class-level extension`() {
        val events = EngineTestKit.engine("junit-jupiter")
            .selectors(DiscoverySelectors.selectClass(SampleClassLevelTest::class.java))
            .configurationParameter("baseline.root", rootPath.absolutePathString())
            .configurationParameter("baseline.output", baselinePath.absolutePathString())
            .execute()

        withClue(events.containerEvents().debug()) {
            events.containerEvents().failed().count() shouldBe 0
        }

        val failedTests = events.testEvents().failed().list()
        failedTests.size shouldBe 2
        failedTests.forEach { event ->
            val throwable = event.getRequiredPayload(TestExecutionResult::class.java).throwable.get()
            throwable.shouldBeInstanceOf<AssertionError>()
            throwable.message.orEmpty() shouldContain "failure"
        }

        baselinePath.resolve("baseline-SampleClassLevelTest.json").exists() shouldBe false
    }

    @Test
    fun `should propagate real exceptions and skip comparison when no baseline exists for method-level extension`() {
        val events = EngineTestKit.engine("junit-jupiter")
            .selectors(DiscoverySelectors.selectClass(SampleMethodLevelTest::class.java))
            .configurationParameter("baseline.root", rootPath.absolutePathString())
            .configurationParameter("baseline.output", baselinePath.absolutePathString())
            .execute()

        withClue(events.containerEvents().debug()) {
            events.containerEvents().failed().count() shouldBe 0
        }

        val failedTests = events.testEvents().failed().list()
        failedTests.size shouldBe 1
        val throwable = failedTests.single().getRequiredPayload(TestExecutionResult::class.java).throwable.get()
        throwable.shouldBeInstanceOf<AssertionError>()
        throwable.message shouldBe "First failure"

        val baselineFileName = "baseline-SampleMethodLevelTest-first.test.-218984446.json"
        baselinePath.resolve(baselineFileName).exists() shouldBe false
    }
}
