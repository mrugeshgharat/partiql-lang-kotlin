package org.partiql.examples.util

import org.partiql.lang.Ident
import org.partiql.lang.eval.ExprValue
import org.partiql.lang.util.ConfigurableExprValueFormatter
import java.io.PrintStream

abstract class Example(val out: PrintStream) {
    abstract fun run()

    private val formatter = ConfigurableExprValueFormatter.pretty

    fun print(label: String, value: ExprValue) {
        print(label, formatter.format(value))
    }

    fun print(label: String, data: String) {
        out.println(label)
        out.println("    ${data.replace("\n", "\n    ")}")
    }

    fun print(label: String, bindings: Map<Ident, ExprValue>) {
        val data = buildString {
            bindings.forEach { (k, v) ->
                val kk = k.toDisplayString()
                append(kk).append(" => ").append(v).append('\n')
            }
        }.trimEnd()
        print(label, data)
    }
}
