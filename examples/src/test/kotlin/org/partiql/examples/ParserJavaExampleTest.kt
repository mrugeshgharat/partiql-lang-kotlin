package org.partiql.examples

import org.partiql.examples.util.JavaExample
import java.io.PrintStream

class ParserJavaExampleTest : BaseJavaExampleTest() {
    override fun example(out: PrintStream): JavaExample = ParserJavaExample(out)

    override val expected =
"""PartiQL query
    SELECT exampleField FROM exampleTable WHERE anotherField > 10
Serialized AST
    
    (
      query
      (
        select
        (
          project
          (
            project_list
            (
              project_expr
              (
                id
                exampleField
                (
                  case_insensitive
                )
                (
                  unqualified
                )
              )
              null
            )
          )
        )
        (
          from
          (
            scan
            (
              id
              exampleTable
              (
                case_insensitive
              )
              (
                unqualified
              )
            )
            null
            null
            null
          )
        )
        (
          where
          (
            gt
            (
              id
              anotherField
              (
                case_insensitive
              )
              (
                unqualified
              )
            )
            (
              lit
              10
            )
          )
        )
      )
    )
"""
}
