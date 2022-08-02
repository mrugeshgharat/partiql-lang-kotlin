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

import com.amazon.ion.IonSexp
import com.amazon.ion.IonSystem
import org.antlr.v4.runtime.BaseErrorListener
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.Lexer
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer
import org.antlr.v4.runtime.misc.ParseCancellationException
import org.antlr.v4.runtime.tree.ParseTree
import org.partiql.lang.ast.ExprNode
import org.partiql.lang.ast.toExprNode
import org.partiql.lang.domains.PartiqlAst
import org.partiql.lang.syntax.PartiQLParser.ParseErrorListener.Companion.INSTANCE
import org.partiql.lang.types.CustomType
import org.partiql.lang.visitors.PartiQLVisitor
import java.nio.charset.StandardCharsets
import org.partiql.lang.generated.PartiQLParser as GeneratedParser
import org.partiql.lang.generated.PartiQLTokens as GeneratedLexer

/**
 * Extends [Parser] to provide a mechanism to parse an input query string. It internally uses ANTLR's generated parser,
 * [GeneratedParser] to create an ANTLR [ParseTree] from the input query. Then, it uses the configured [PartiQLVisitor]
 * to convert the [ParseTree] into a [PartiqlAst.Statement].
 */
class PartiQLParser(
    private val ion: IonSystem,
    val customTypes: List<CustomType> = listOf()
) : Parser {

    override fun parseAstStatement(source: String): PartiqlAst.Statement {
        val parameterIndexes = getNumberOfParameters(source)
        val lexer = getLexer(source)
        val tree = parseQuery(lexer)
        val visitor = PartiQLVisitor(ion, customTypes, parameterIndexes)
        return visitor.visit(tree) as PartiqlAst.Statement
    }

    fun parseQuery(lexer: Lexer): ParseTree {
        val parser = getParser(lexer)
        return parser.topQuery()
    }

    internal fun getLexer(source: String): Lexer {
        val inputStream = CharStreams.fromStream(source.byteInputStream(StandardCharsets.UTF_8), StandardCharsets.UTF_8)
        val lexer = GeneratedLexer(inputStream)
        lexer.removeErrorListeners()
        lexer.addErrorListener(PartiQLLexer.TokenizeErrorListener.INSTANCE)
        return lexer
    }

    /**
     * Create a map where the key is the index of a '?' token relative to all tokens, and the value is the index of a
     * '?' token relative to all other '?' tokens (starting at index 1). This is used for visiting.
     * NOTE: This needs to create its own lexer. Cannot share with others due to consumption of token stream.
     */
    private fun getNumberOfParameters(query: String): Map<Int, Int> {
        val lexer = getLexer(query)
        val tokenIndexToParameterIndex = mutableMapOf<Int, Int>()
        var parametersFound = 0
        val tokens = CommonTokenStream(lexer)
        for (i in 0 until tokens.numberOfOnChannelTokens) {
            if (tokens[i].type == GeneratedParser.QUESTION_MARK) {
                tokenIndexToParameterIndex[tokens[i].tokenIndex] = ++parametersFound
            }
        }
        return tokenIndexToParameterIndex
    }

    fun getParser(lexer: Lexer): GeneratedParser {
        val tokens = CommonTokenStream(lexer)
        val parser = GeneratedParser(tokens)
        parser.removeErrorListeners()
        parser.addErrorListener(INSTANCE)
        return parser
    }

    @Deprecated("Please use parseAstStatement() instead--ExprNode is deprecated.")
    override fun parseExprNode(source: String): @Suppress("DEPRECATION") ExprNode {
        return parseAstStatement(source).toExprNode(ion)
    }

    @Deprecated("Please use parseAstStatement() instead--the return value can be deserialized to backward-compatible IonSexp.")
    override fun parse(source: String): IonSexp =
        @Suppress("DEPRECATION")
        org.partiql.lang.ast.AstSerializer.serialize(
            parseExprNode(source),
            org.partiql.lang.ast.AstVersion.V0, ion
        )

    class ParseErrorListener : BaseErrorListener() {
        @Throws(ParseCancellationException::class)
        override fun syntaxError(
            recognizer: Recognizer<*, *>?,
            offendingSymbol: Any,
            line: Int,
            charPositionInLine: Int,
            msg: String,
            e: RecognitionException?
        ) {
            throw ParseException(msg, e)
        }

        class ParseException(msg: String, cause: Throwable? = null) : RuntimeException(msg, cause)
        companion object {
            val INSTANCE = ParseErrorListener()
        }
    }
}