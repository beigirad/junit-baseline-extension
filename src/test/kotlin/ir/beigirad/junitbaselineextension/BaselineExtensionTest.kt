package ir.beigirad.junitbaselineextension

import io.kotest.assertions.asClue
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
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

        baselinePath.resolve(SampleClassLevelTest.baselineFileName).asClue {
            it.exists() shouldBe true
            it.readText() shouldBe sampleBaselinePath.resolve(SampleClassLevelTest.baselineFileName).readText()
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

        baselinePath.resolve(SampleMethodLevelTest.baselineFileName).asClue {
            it.exists() shouldBe true
            it.readText() shouldBe sampleBaselinePath.resolve(SampleMethodLevelTest.baselineFileName).readText()
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
}
