package ir.beigirad.junitbaselineextension

import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.Test

@ExtendWith(BaselineExtension::class)
class SamplePassedTest {

    @Test
    fun `passed test should not have any baseline`() {
    }

    @Test
    fun `passed test should not have any baseline 2`() {
    }
}


