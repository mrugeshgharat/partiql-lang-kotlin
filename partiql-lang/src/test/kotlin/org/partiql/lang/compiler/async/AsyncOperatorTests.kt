package org.partiql.lang.compiler.async

import com.amazon.ionelement.api.ionInt
import com.amazon.ionelement.api.ionString
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.partiql.annotations.ExperimentalPartiQLCompilerPipeline
import org.partiql.lang.compiler.PartiQLCompilerPipeline
import org.partiql.lang.domains.PartiqlPhysical
import org.partiql.lang.eval.EvaluationSession
import org.partiql.lang.eval.PartiQLResult
import org.partiql.lang.eval.booleanValue
import org.partiql.lang.eval.isNotUnknown
import org.partiql.lang.eval.physical.operators.FilterRelationalOperatorFactory
import org.partiql.lang.eval.physical.operators.RelationExpression
import org.partiql.lang.eval.physical.operators.ValueExpression
import org.partiql.lang.eval.relation.RelationType
import org.partiql.lang.eval.relation.relation
import org.partiql.lang.planner.litTrue
import org.partiql.lang.planner.transforms.DEFAULT_IMPL
import org.partiql.lang.planner.transforms.PLAN_VERSION_NUMBER

private const val FAKE_IMPL_NAME = "test_async_fake"
private val FAKE_IMPL_NODE = PartiqlPhysical.build { impl(FAKE_IMPL_NAME) }

@OptIn(ExperimentalPartiQLCompilerPipeline::class)
class AsyncOperatorTests {
    private val fakeOperatorFactories = listOf(
        object : FilterRelationalOperatorFactory(FAKE_IMPL_NAME) {
            override fun create(
                impl: PartiqlPhysical.Impl,
                predicate: ValueExpression,
                sourceBexpr: RelationExpression
            ): RelationExpression = RelationExpression { state ->
                // `runBlocking` required since need some coroutine scope to run async fun
                // goal is to make this call not `runBlocking`
                runBlocking {
                    println("Calling")
                    someAsyncOp()
                }
                val input = sourceBexpr.evaluate(state)

                relation(RelationType.BAG) {
                    while (true) {
                        if (!input.nextRow()) {
                            break
                        } else {
                            val matches = predicate.invoke(state)
                            if (matches.isNotUnknown() && matches.booleanValue()) {
                                yield()
                            }
                        }
                    }
                }
            }
        }
    )

    private suspend fun someAsyncOp() {
        println("sleeping")
        delay(2000L)
        println("done sleeping")
    }

    @Test
    fun compilePlan() {
        val pipeline = PartiQLCompilerPipeline.build {
            compiler
                .customOperatorFactories(
                    fakeOperatorFactories.map { it }
                )
        }
        val plan = PartiqlPhysical.build {
            plan(
                stmt = query(
                    bindingsToValues(
                        exp = lit(ionInt(42)),
                        query = filter(
                            i = FAKE_IMPL_NODE,
                            predicate = litTrue(),
                            source = scan(
                                i = DEFAULT_IMPL,
                                expr = bag(struct(listOf(structField(fieldName = lit(ionString("a")), value = lit(ionInt(1)))))),
                                asDecl = varDecl(0)
                            )
                        )
                    )
                ),
                version = PLAN_VERSION_NUMBER,
                locals = listOf(localVariable("_1", 0))
            )
        }
        runBlocking {
            val statement = pipeline.compileAsync(plan)
            repeat(10) { index ->
                async {
                    print("Compiling $index. ")
                    val result = statement.eval(EvaluationSession.standard()) as PartiQLResult.Value
                    println("About to print value; $index")
                    println(result.value)
                }
            }
        }
    }
}
