package org.partiql.examples

import org.partiql.examples.util.JavaExample
import java.io.PrintStream

class PartiQLCompilerPipelineJavaExampleTest : BaseJavaExampleTest() {
    override fun example(out: PrintStream): JavaExample = PartiQLCompilerPipelineJavaExample(out)

    override val expected = """
        |PartiQL query:
        |    SELECT t.name FROM myTable AS t WHERE t.age > 20
        |result
        |    <<
        |      {
        |        'name': 'tim'
        |      }
        |    >>
        |
    """.trimMargin()
}
