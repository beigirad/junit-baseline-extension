package ir.beigirad.junitbaselineextension

import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.Test

@ExtendWith(BaselineExtension::class)
class SampleClassLevelTest {
    companion object {
        val baselineFileName = "baseline-SampleClassLevelTest.json"
    }

    @Test
    fun `first test`() {
        throw AssertionError("First failure")
    }

    @Test
    fun `second test`() {
        throw AssertionError("Second failure\nSecond failure line2\nSecond failure line3")
    }
}
