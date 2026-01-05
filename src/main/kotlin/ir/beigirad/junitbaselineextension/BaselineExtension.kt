package ir.beigirad.junitbaselineextension

import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler
import kotlin.io.path.Path

class BaselineExtension : TestExecutionExceptionHandler, AfterEachCallback, AfterAllCallback {
    private val baseline = Baseline(
        // used to remove environment-specific details from error messages
        projectRoot = Path(System.getProperty("baseline.root")),
        baselineOutput = Path(System.getProperty("baseline.output"))
    )

    override fun handleTestExecutionException(context: ExtensionContext, throwable: Throwable) {
        // record test failures without throwing, for baseline comparison instead of `throw throwable`
        baseline.recordFailure(context.uniqueId, throwable.message)
    }

    override fun afterEach(context: ExtensionContext) {
        // ignore printing baseline when it applied by class (not by method!)
        if (context.isAppliedByClass()) return

        baseline.assertOrWrite(
            identifier = context.requiredTestClass.simpleName + "-" + context.requiredTestMethod.name.hashCode(),
            recordMode = isRecordingBaseline()
        )
    }

    override fun afterAll(context: ExtensionContext) {
        // avoid writing baselines for nested classes (they are handled in parent)
        if (context.isNestedTestClass()) return

        baseline.assertOrWrite(
            identifier = context.requiredTestClass.simpleName,
            recordMode = isRecordingBaseline()
        )
    }

    private fun isRecordingBaseline(): Boolean =
        System.getProperty(ARG_RECORD) == "true"

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
