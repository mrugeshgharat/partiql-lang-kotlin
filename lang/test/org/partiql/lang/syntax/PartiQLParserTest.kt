/*
 * Copyright 2022 Amazon.com, Inc. or its affiliates.  All rights reserved.
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

package org.partiql.lang.syntax

import com.amazon.ion.IonSystem
import com.amazon.ion.system.IonSystemBuilder
import org.antlr.v4.gui.TreeViewer
import org.antlr.v4.runtime.tree.ParseTree
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ArgumentsSource
import org.partiql.lang.eval.evaluatortestframework.EvaluatorTestFailureReason
import org.partiql.lang.eval.evaluatortestframework.assertEquals
import org.partiql.lang.util.ArgumentsProviderBase
import java.lang.Thread.sleep
import javax.swing.JFrame
import javax.swing.JPanel

class PartiQLParserTest {

    val ion: IonSystem = IonSystemBuilder.standard().build()
    val parser = PartiQLParser(ion)
    val oldParser = SqlParser(ion)

    private fun parseQuery(query: String): ParseTree {
        val lexer = parser.getLexer(query)
        return parser.parseQuery(lexer)
    }

    private fun getParser(query: String): org.antlr.v4.runtime.Parser {
        val lexer = parser.getLexer(query)
        return parser.getParser(lexer)
    }

    @ParameterizedTest
    @ArgumentsSource(QueryCases::class)
    fun test(query: String) {
        // Act
        val expected = oldParser.parseAstStatement(query)
        val stmt = parser.parseAstStatement(query)
        val tree = parseQuery(query)

        // Build Message
        val b = StringBuilder()
        b.appendLine("QUERY              : \"$query\"")
        b.appendLine("ANTLR TREE         : ${tree.toStringTree(getParser(query))}")
        b.appendLine("ACTUAL STATEMENT   : $stmt")
        b.appendLine("EXPECTED STATEMENT : $expected")

        // Assert
        assertEquals(expected, stmt, EvaluatorTestFailureReason.FAILED_TO_EVALUATE_QUERY) {
            b.toString()
        }
    }

    @Test
    fun testOnlyPartiQL() {
        val query = "date_add(month, a, b)"

        // Act
        val stmt = parser.parseAstStatement(query)
        val tree = parseQuery(query)

        // Build Message
        val b = StringBuilder()
        b.appendLine("QUERY              : \"$query\"")
        b.appendLine("ANTLR TREE         : ${tree.toStringTree(getParser(query))}")
        b.appendLine("ACTUAL STATEMENT   : $stmt")

        // Assert
        println(b.toString())
    }

    @Test
    fun testVisual() {
        val query = "SELECT * FROM ( <<1>> UNION <<2>> )"
        val tree = parseQuery(query)
        val b = StringBuilder()
        b.appendLine("ANTLR TREE         : ${tree.toStringTree(getParser(query))}")

        val frame = JFrame("AST")
        val panel = JPanel()
        val view = TreeViewer(getParser(query).ruleNames.toMutableList(), tree)
        view.scale = 1.5
        panel.add(view)
        frame.add(panel)
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.pack()
        frame.isVisible = true
        sleep(30_000)
    }

    class QueryCases : ArgumentsProviderBase() {
        override fun getParameters(): List<Any> {
            val queries = listOf(
                "SELECT a, \"b\", @c, @\"d\" FROM <<true, false, null, missing, `hello`, 'this is a string', 4, 4e2, 4.2, .4, DATE '2022-02-01', TIME '23:11:59.123456789' >>",
                "SELECT 5 + 5 / 2 FROM <<>>",
                "SELECT 1 + 2 / 3 - 4 % 5 AND true + 6 OR 7 + 8 FROM <<>>",
                "SELECT false OR 1 * 2 / 3 % 4 = 5 AND true + 6 OR 7 + 8 FROM <<>>",
                "SELECT true OR false FROM <<>>",
                "SELECT 5 <= 2 FROM <<>>",
                "SELECT 5 BETWEEN 1 AND 2 AND 3 LIKE 4 FROM <<>>",
                "SELECT * FROM <<>> ORDER BY 1 ASC NULLS FIRST",
                "SELECT * FROM <<>> ORDER BY 1 ASC NULLS LAST",
                "SELECT * FROM <<>> ORDER BY 1 ASC",
                "SELECT * FROM <<>> ORDER BY 1 DESC",
                "SELECT * FROM <<>> ORDER BY 1",
                "SELECT * FROM <<>> ORDER BY 1 NULLS FIRST",
                "SELECT * FROM <<>> GROUP PARTIAL BY 1 + 1",
                "SELECT * FROM <<>> GROUP BY 1 + 1, 2 + 2",
                "SELECT * FROM <<>> GROUP BY 1 + 1 AS a, 2 + 2 AS b",
                "SELECT * FROM <<>> GROUP BY 1 + 1 AS a, 2 + 2 AS b GROUP AS c",
                "SELECT * FROM <<>> GROUP BY 1 + 1",
                "SELECT * FROM <<>> LIMIT 5",
                "SELECT * FROM <<>> LIMIT 5 + 5",
                "SELECT * FROM <<1, 2, 3>> OFFSET 2",
                "SELECT * FROM <<>> WHERE 1 = 1",
                "SELECT * FROM <<>> HAVING true",
                "SELECT * FROM <<>> LET 2 AS a",
                "SELECT * FROM a, (SELECT * FROM <<>>)",
                "SELECT * FROM a, b, c",
                "SELECT * FROM a LEFT JOIN c ON id",
                "SELECT * FROM a INNER JOIN c ON id",
                "SELECT * FROM a LEFT OUTER JOIN c ON id",
                "SELECT * FROM a RIGHT OUTER JOIN c ON id",
                "SELECT * FROM a FULL OUTER JOIN c ON id",
                "SELECT * FROM a FULL JOIN c ON id",
                "SELECT * FROM a FULL JOIN (b FULL JOIN c ON d) ON e",
                "SELECT x FROM A INNER JOIN (B INNER JOIN (C INNER JOIN D ON C = D) ON B = C) ON A = B",
                "SELECT x FROM A INNER JOIN (B RIGHT OUTER JOIN (C INNER JOIN D ON C = D) ON B = C) ON A = B",
                "<< { 'a': 1 }>>",
                "<< { a: 1 }>>",
                "<< { 2: 1 }>>",
                "SELECT x FROM a, b CROSS JOIN c LEFT JOIN d ON e RIGHT OUTER CROSS JOIN f OUTER JOIN g ON h",
                "SELECT x FROM stuff s RIGHT CROSS JOIN foo f",
                "SELECT x FROM a INNER CROSS JOIN b CROSS JOIN c LEFT JOIN d ON e RIGHT OUTER CROSS JOIN f OUTER JOIN g ON h",
                "CAST( 1 AS int)",
                "CAST( 3 AS INTEGER)",
                "CAST( 3 AS STRING)",
                "a intersect b intersect all c",
                "d intersect e intersect f",
                "g intersect all h intersect all i",
                "a like b like c",
                "a like b like c not like d",
                "a like b not like c like d",
                "5 <= 5 < 2",
                "a <= b >= c < d > e != f = g <> h",
                "a not like b + c",
                "a between foo and bar and bat",
                "SELECT * FROM VALUES (1)"
            )
            return queries
        }
    }
}