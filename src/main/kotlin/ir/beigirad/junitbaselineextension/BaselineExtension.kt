package ir.beigirad.junitbaselineextension

import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler
import kotlin.io.path.Path
import kotlin.text.filter

class BaselineExtension : TestExecutionExceptionHandler,
    AfterEachCallback,
    AfterAllCallback,
    BeforeAllCallback,
    BeforeEachCallback {
    private lateinit var baseline: Baseline
    private var isRecording: Boolean = false

    override fun beforeAll(context: ExtensionContext) {
        if (context.isNestedTestClass()) return

        initialize(context)
    }

    override fun beforeEach(context: ExtensionContext) {
        if (context.isAppliedByClass()) return

        initialize(context)
    }

    override fun handleTestExecutionException(context: ExtensionContext, throwable: Throwable) {
        // record test failures without throwing, for baseline comparison instead of `throw throwable`
        baseline.recordFailure(context.displayName, throwable.message)
    }

    override fun afterEach(context: ExtensionContext) {
        // ignore printing baseline when it applied by class (not by method!)
        if (context.isAppliedByClass()) return

        baseline.assertOrWrite(
            identifier = context.requiredTestClass.simpleName + "-" + shortenMethodName(context.requiredTestMethod.name),
            recordMode = isRecording
        )
    }

    override fun afterAll(context: ExtensionContext) {
        // avoid writing baselines for nested classes (they are handled in parent)
        if (context.isNestedTestClass()) return

        baseline.assertOrWrite(
            identifier = context.requiredTestClass.simpleName,
            recordMode = isRecording
        )
    }

    private fun initialize(context: ExtensionContext) {
        fun getProp(key: String) =
            context.getConfigurationParameter(key).orElse(System.getProperty(key))

        isRecording = getProp(ARG_RECORD) == "true"
        baseline = Baseline(
            projectRoot = Path(getProp("baseline.root")),
            baselineOutput = Path(getProp("baseline.output"))
        )
    }

    private fun shortenMethodName(name: String) =
        name.split(" ")
            .take(4)
            .joinToString(".") { word -> word.lowercase().filter { it.isLetter() || it.isDigit() } }
            .plus(".")
            .plus(name.hashCode())

    private fun ExtensionContext.isNestedTestClass(): Boolean =
        parent.flatMap { it.testClass }
            .map { parentClass ->
                val currentClass = this.requiredTestClass
                currentClass.enclosingClass != null && currentClass.enclosingClass == parentClass
            }
            .orElse(false)

    private fun ExtensionContext.isAppliedByClass(): Boolean =
        testClass.orElse(null)
            .getAnnotationsByType(ExtendWith::class.java)
            .any { it.value.contains(BaselineExtension::class) }

    companion object {
        internal const val ARG_RECORD = "baseline.record"
    }
}
