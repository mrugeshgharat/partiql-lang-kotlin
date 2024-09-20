/*
 * Copyright Amazon.com, Inc. or its affiliates.  All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at:
 *
 *      http://aws.amazon.com/apache2.0/
 *
 * or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 */

package org.partiql.cli.shell

import org.partiql.cli.format.ExplainFormatter
import org.partiql.lang.eval.EvaluationException
import org.partiql.lang.eval.ExprValue
import org.partiql.lang.eval.PartiQLResult
import org.partiql.lang.util.ExprValueFormatter
import java.io.PrintStream
import java.util.concurrent.BlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A wrapper over [ExprValueFormatter]. It constantly grabs [ExprValue]s from [values] and writes them to [out].
 * When it is done printing a single item, it sets [donePrinting] to true.
 */
internal class RunnableWriter(
    private val out: PrintStream,
    private val formatter: ExprValueFormatter,
    private val values: BlockingQueue<RunnablePipeline.Output>,
    private val donePrinting: AtomicBoolean
) : Runnable {

    /**
     * When the Thread running this [Runnable] is interrupted, the underlying formatter should check the interruption
     * flag and fail with some exception. The formatter itself doesn't do this, but, since [ExprValue]s are lazily created,
     * the creation of the [ExprValue] (by means of the thunks produced by the EvaluatingCompiler) should throw an exception
     * when the thread is interrupted. Then, this will break out of [run].
     */
    override fun run() {
        while (true) {
            val value = values.poll(3, TimeUnit.SECONDS)
            if (value != null) {
                when (value) {
                    is RunnablePipeline.Output.Result -> try {
                        printResult(value.result)
                    } catch (t: Throwable) { printThrowable(t) }
                    is RunnablePipeline.Output.Error -> printThrowable(value.throwable)
                }
                out.flush()
                donePrinting.set(true)
            }
        }
    }

    @Throws(Throwable::class)
    private fun printResult(result: PartiQLResult) {
        when (result) {
            is PartiQLResult.Value -> {
                out.info(BAR_1)
                formatter.formatTo(result.value, out)
                out.println()
                out.info(BAR_2)
                out.success("OK!")
            }
            is PartiQLResult.Explain.Domain -> {
                val explain = ExplainFormatter.format(result)
                out.println(explain)
                out.success("OK!")
            }
        }
    }

    private fun printThrowable(t: Throwable) {
        out.println()
        when (t) {
            is EvaluationException -> {
                out.error(t.generateMessage())
                out.error(t.message)
            }
            is InterruptedException -> {
                out.info("This has been interrupted!")
            }
            else -> {
                out.error(t.message ?: "ERROR encountered. However, no message was attached.")
            }
        }
    }
}
