package ir.beigirad.junitbaselineextension

import io.kotest.assertions.throwables.shouldThrowUnit
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

class SampleMethodLevelTest {

    @Test
    @ExtendWith(BaselineExtension::class)
    fun `first test`() {
        throw AssertionError("First failure")
    }

    @Test
    fun `second test`() {
        shouldThrowUnit<Throwable> {
            throw AssertionError("Second failure")
        }
    }
}
