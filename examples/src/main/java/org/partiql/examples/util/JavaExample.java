package org.partiql.examples.util;

import java.io.PrintStream;
import java.util.Map;
import org.partiql.lang.eval.ExprValue;
import org.partiql.lang.util.ConfigurableExprValueFormatter;

public abstract class JavaExample {
    public JavaExample(PrintStream s) {
        out = s;
    }

    public abstract void run();

    private final ConfigurableExprValueFormatter formatter = ConfigurableExprValueFormatter.getPretty();
    private final PrintStream out;

    public void print(String label, ExprValue value) {
        print(label, formatter.format(value));
    }

    public void print(String label, String data) {
        out.println(label);
        out.printf("    %s\n", data.replace("\n", "\n    "));
    }

    public void print(String label, Map<String, ExprValue> bindings) {
        StringBuilder data = new StringBuilder();
        for (Map.Entry<String, ExprValue> entry : bindings.entrySet()) {
            data.append(entry.getKey()).append(" => ").append(entry.getValue()).append("\n");
        }
        print(label, data.toString().trim());
    }
}

/*

abstract class JavaExample(val out: PrintStream) {
    // Notice no `suspend` here
    abstract fun run()

    private val formatter = ConfigurableExprValueFormatter.pretty

    fun print(label: String, value: ExprValue) {
        print(label, formatter.format(value))
    }

    fun print(label: String, data: String) {
        out.println(label)
        out.println("    ${data.replace("\n", "\n    ")}")
    }

    fun print(label: String, bindings: Map<String, ExprValue>) {
        val data = buildString {
            bindings.forEach { (k, v) ->
                append(k).append(" => ").append(v).append('\n')
            }
        }.trimEnd()
        print(label, data)
    }
}
 */