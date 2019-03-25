import com.oneeyedmen.okeydoke.Approver
import com.oneeyedmen.okeydoke.ApproverFactories
import com.oneeyedmen.okeydoke.Name
import org.junit.jupiter.api.extension.AfterTestExecutionCallback
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver
import java.io.File
import java.lang.reflect.Method

// Thank you jshiell!
// https://gist.github.com/jshiell/f8f0c985733bf9a437e964f9d66dd66d

class OkeyDokeExtension : BeforeTestExecutionCallback, AfterTestExecutionCallback, ParameterResolver {

    companion object {
        private const val STORE_KEY = "approver"
    }

    private val factory = ApproverFactories.fileSystemApproverFactory(File("src/test/resources"))
    private val testNamer = TestNamer()

    override fun beforeTestExecution(context: ExtensionContext) {
        store(context).put(STORE_KEY, factory.createApprover(
            testNamer.nameFor(context.requiredTestClass, context.requiredTestMethod),
            context.requiredTestClass))
    }

    override fun afterTestExecution(context: ExtensionContext) {
        context.executionException.ifPresentOrElse({}, {
            val approver = store(context).get(STORE_KEY) as Approver
            if (!approver.satisfactionChecked()) {
                approver.assertSatisfied()
            }
        })
    }

    override fun supportsParameter(parameterContext: ParameterContext, context: ExtensionContext): Boolean =
        parameterContext.parameter.type == Approver::class.java

    override fun resolveParameter(parameterContext: ParameterContext, context: ExtensionContext): Any? =
        if (parameterContext.parameter.type == Approver::class.java) {
            store(context).get(STORE_KEY)
        } else {
            null
        }

    private fun store(context: ExtensionContext) =
        context.getStore(ExtensionContext.Namespace.create(context.requiredTestClass.name, context.requiredTestMethod.name))

}

internal class TestNamer {

    fun nameFor(testClass: Class<*>, testMethod: Method): String =
        nameFromClass(testClass) + "." + nameFromMethod(testMethod)

    private fun nameFromMethod(testMethod: Method): String =
        testMethod.getAnnotation(Name::class.java)?.value ?: testMethod.name

    private fun nameFromClass(testClass: Class<*>): String =
        testClass.getAnnotation(Name::class.java)?.value ?: testClass.simpleName
}
