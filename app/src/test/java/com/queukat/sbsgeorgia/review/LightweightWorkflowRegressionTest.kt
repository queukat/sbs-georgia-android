package com.queukat.sbsgeorgia.review

import java.io.File
import org.junit.Test

class LightweightWorkflowRegressionTest {
    @Test
    fun lightweightWorkflowRegressionChecks() {
        val fixtures = File(requireNotNull(javaClass.classLoader?.getResource("fixtures")).toURI())
        main(arrayOf(fixtures.absolutePath))
    }
}
