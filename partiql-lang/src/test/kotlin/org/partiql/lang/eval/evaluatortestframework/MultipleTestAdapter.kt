package org.partiql.lang.eval.evaluatortestframework

import org.partiql.lang.eval.EvaluationSession

/** Runs each test with all adapters specified in [adapters]. */
class MultipleTestAdapter(
    private val adapters: List<EvaluatorTestAdapter>
) : EvaluatorTestAdapter {
    override suspend fun runEvaluatorTestCase(tc: EvaluatorTestCase, session: EvaluationSession) {
        adapters.forEach { it.runEvaluatorTestCase(tc, session) }
    }

    override suspend fun runEvaluatorErrorTestCase(tc: EvaluatorErrorTestCase, session: EvaluationSession) {
        adapters.forEach { it.runEvaluatorErrorTestCase(tc, session) }
    }
}
