package ir.beigirad.junitbaselineextension

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.junit.platform.engine.discovery.DiscoverySelectors
import org.junit.platform.testkit.engine.EngineTestKit
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.absolutePathString
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import kotlin.io.path.writeText

class BaselineExtensionTest {

    @TempDir
    lateinit var rootPath: Path

    @TempDir
    lateinit var baselinePath: Path

    @OptIn(ExperimentalPathApi::class)
    @AfterEach
    fun setup() {
        System.setProperty("baseline.root", rootPath.absolutePathString())
        System.setProperty("baseline.output", baselinePath.absolutePathString())
    }

    @OptIn(ExperimentalPathApi::class)
    @AfterEach
    fun tearDown() {
        rootPath.listDirectoryEntries().forEach { it.deleteRecursively() }
        baselinePath.listDirectoryEntries().forEach { it.deleteRecursively() }

        System.clearProperty("baseline.root")
        System.clearProperty("baseline.output")
    }

    @Test
    fun `should record baseline when recordBaseline is true for class-level extension`() {
        val events = EngineTestKit.engine("junit-jupiter")
            .selectors(DiscoverySelectors.selectClass(ClassLevelTest::class.java))
            .configurationParameter("baseline.root", rootPath.absolutePathString())
            .configurationParameter("baseline.output", baselinePath.absolutePathString())
            .configurationParameter("baseline.record", "true")
            .execute()

        withClue(events.containerEvents().debug()) {
            events.containerEvents().failed().count() shouldBe 0
        }

        val baselineFile = baselinePath.resolve("baseline-ClassLevelTest.json")
        baselineFile.exists() shouldBe true

        baselineFile.readText() shouldBe ClassLevelTest.baselineContent
    }

    @Test
    fun `should assert against baseline when recordBaseline is false for class-level extension`() {
        val baselineFile = baselinePath.resolve("baseline-ClassLevelTest.json")
        baselineFile.writeText(ClassLevelTest.baselineContent)

        val events = EngineTestKit.engine("junit-jupiter")
            .selectors(DiscoverySelectors.selectClass(ClassLevelTest::class.java))
            .configurationParameter("baseline.root", rootPath.absolutePathString())
            .configurationParameter("baseline.output", baselinePath.absolutePathString())
            .execute()

        withClue(events.containerEvents().debug()) {
            events.containerEvents().failed().count() shouldBe 0
        }
    }

    @Test
    fun `should record baseline when recordBaseline is true for method-level extension`() {
        val events = EngineTestKit.engine("junit-jupiter")
            .selectors(DiscoverySelectors.selectClass(MethodLevelTest::class.java))
            .configurationParameter("baseline.root", rootPath.absolutePathString())
            .configurationParameter("baseline.output", baselinePath.absolutePathString())
            .configurationParameter("baseline.record", "true")
            .execute()

        withClue(events.containerEvents().debug()) {
            events.containerEvents().failed().count() shouldBe 0
        }

        val baselineFile = baselinePath.resolve(MethodLevelTest.baselineFileName)
        baselineFile.exists() shouldBe true

        baselineFile.readText() shouldBe MethodLevelTest.baselineContent
    }

    @Test
    fun `should assert against baseline when recordBaseline is false for method-level extension`() {
        val baselineFile = baselinePath.resolve(MethodLevelTest.baselineFileName)
        baselineFile.writeText(MethodLevelTest.baselineContent)

        val events = EngineTestKit.engine("junit-jupiter")
            .selectors(DiscoverySelectors.selectClass(MethodLevelTest::class.java))
            .configurationParameter("baseline.root", rootPath.absolutePathString())
            .configurationParameter("baseline.output", baselinePath.absolutePathString())
            .execute()

        withClue(events.containerEvents().debug()) {
            events.containerEvents().failed().count() shouldBe 0
        }
    }
}

class MethodLevelTest {
    companion object {
        val baselineFileName = "baseline-MethodLevelTest-${"first test".hashCode()}.json"
        val baselineContent =
            """{"[engine:junit-jupiter]/[class:ir.beigirad.junitbaselineextension.MethodLevelTest]/[method:first test()]":"First failure"}"""
    }

    @Test
    @ExtendWith(BaselineExtension::class)
    fun `first test`() {
        throw AssertionError("First failure")
    }

    @Test
    fun `second test`() {
        throw AssertionError("Second failure")
    }
}

@ExtendWith(BaselineExtension::class)
class ClassLevelTest {
    companion object {
        val baselineContent =
            """{"[engine:junit-jupiter]/[class:ir.beigirad.junitbaselineextension.ClassLevelTest]/[method:first test()]":"First failure","[engine:junit-jupiter]/[class:ir.beigirad.junitbaselineextension.ClassLevelTest]/[method:second test()]":"Second failure"}"""
    }

    @Test
    fun `first test`() {
        throw AssertionError("First failure")
    }

    @Test
    fun `second test`() {
        throw AssertionError("Second failure")
    }
}
