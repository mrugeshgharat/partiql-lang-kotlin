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
import com.amazon.ion.IonValue
import org.antlr.v4.runtime.BaseErrorListener
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer
import org.partiql.lang.errors.ErrorCode
import org.partiql.lang.errors.Property.COLUMN_NUMBER
import org.partiql.lang.errors.Property.LINE_NUMBER
import org.partiql.lang.errors.Property.TOKEN_STRING
import org.partiql.lang.errors.PropertyValueMap
import org.partiql.lang.util.bigDecimalOf
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import org.antlr.v4.runtime.Token as AntlrToken
import org.partiql.lang.generated.PartiQLTokens as GeneratedLexer

/**
 * [PartiQLLexer] is a [Lexer] that uses the [GeneratedLexer] generated by ANTLR. This class is only used to assert the
 * same functionality across [SqlLexer] and ANTLR's generated lexer.
 */
class PartiQLLexer(private val ion: IonSystem) : Lexer {

    /**
     * Converts a query into a list of PartiQL Tokens by utilizing the [GeneratedLexer] and LexerConstants
     */
    override fun tokenize(source: String): List<Token> {
        val inputStream = CharStreams.fromStream(source.byteInputStream(StandardCharsets.UTF_8), StandardCharsets.UTF_8)
        val lexer = GeneratedLexer(inputStream)
        lexer.removeErrorListeners()
        lexer.addErrorListener(TokenizeErrorListener.INSTANCE)
        val tokenStream = CommonTokenStream(lexer)
        tokenStream.fill()
        return tokenStream.tokens.map { it.toPartiQLToken() }
    }

    /**
     * Converts ANTLR Tokens into PartiQL Tokens
     */
    private fun AntlrToken.toPartiQLToken(): Token {
        val length = stopIndex - startIndex + 1
        val type = getPartiQLTokenType()
        val value = getIonValue()
        val span = SourceSpan(line.toLong(), charPositionInLine.toLong() + 1, length.toLong())
        val sourceText = getSourceText()
        return Token(type, value, sourceText, span)
    }

    private fun AntlrToken.getSourceText(): String {
        return when {
            type == GeneratedLexer.LITERAL_STRING -> text.trim('\'').replace("''", "'")
            type == GeneratedLexer.IDENTIFIER_QUOTED -> text.trim('\"').replace("\"\"", "\"")
            type == GeneratedLexer.ION_CLOSURE -> text.trim('`')
            else -> text
        }
    }

    /**
     * Returns the corresponding PartiQL Token Type for the ANTLR Token
     */
    private fun AntlrToken.getPartiQLTokenType(): TokenType {
        return when {
            type == GeneratedLexer.PAREN_LEFT -> TokenType.LEFT_PAREN
            type == GeneratedLexer.PAREN_RIGHT -> TokenType.RIGHT_PAREN
            type == GeneratedLexer.ASTERISK -> TokenType.STAR
            type == GeneratedLexer.BRACKET_LEFT -> TokenType.LEFT_BRACKET
            type == GeneratedLexer.BRACKET_RIGHT -> TokenType.RIGHT_BRACKET
            type == GeneratedLexer.ANGLE_DOUBLE_LEFT -> TokenType.LEFT_DOUBLE_ANGLE_BRACKET
            type == GeneratedLexer.ANGLE_DOUBLE_RIGHT -> TokenType.RIGHT_DOUBLE_ANGLE_BRACKET
            type == GeneratedLexer.BRACE_LEFT -> TokenType.LEFT_CURLY
            type == GeneratedLexer.BRACE_RIGHT -> TokenType.RIGHT_CURLY
            type == GeneratedLexer.COLON -> TokenType.COLON
            type == GeneratedLexer.COLON_SEMI -> TokenType.SEMICOLON
            type == GeneratedLexer.LAST -> TokenType.LAST
            type == GeneratedLexer.FIRST -> TokenType.FIRST
            type == GeneratedLexer.AS -> TokenType.AS
            type == GeneratedLexer.AT -> TokenType.AT
            type == GeneratedLexer.ASC -> TokenType.ASC
            type == GeneratedLexer.DESC -> TokenType.DESC
            type == GeneratedLexer.NULL -> TokenType.NULL
            type == GeneratedLexer.NULLS -> TokenType.NULLS
            type == GeneratedLexer.MISSING -> TokenType.MISSING
            type == GeneratedLexer.COMMA -> TokenType.COMMA
            type == GeneratedLexer.PERIOD -> TokenType.DOT
            type == GeneratedLexer.QUESTION_MARK -> TokenType.QUESTION_MARK
            type == GeneratedLexer.EOF -> TokenType.EOF
            type == GeneratedLexer.FOR -> TokenType.FOR
            type == GeneratedLexer.BY -> TokenType.BY
            type == GeneratedLexer.ION_CLOSURE -> TokenType.ION_LITERAL
            type == GeneratedLexer.LITERAL_STRING -> TokenType.LITERAL
            type == GeneratedLexer.LITERAL_INTEGER -> TokenType.LITERAL
            type == GeneratedLexer.LITERAL_DECIMAL -> TokenType.LITERAL
            type == GeneratedLexer.IDENTIFIER_QUOTED -> TokenType.QUOTED_IDENTIFIER
            type == GeneratedLexer.TRUE -> TokenType.LITERAL
            type == GeneratedLexer.FALSE -> TokenType.LITERAL
            ALL_SINGLE_LEXEME_OPERATORS.contains(text.toLowerCase()) -> TokenType.OPERATOR
            type == GeneratedLexer.IDENTIFIER -> TokenType.IDENTIFIER
            type == GeneratedLexer.IDENTIFIER_QUOTED -> TokenType.QUOTED_IDENTIFIER
            ALL_OPERATORS.contains(text.toLowerCase()) -> TokenType.OPERATOR
            MULTI_LEXEME_TOKEN_MAP.containsKey(text.toLowerCase().split("\\s+".toRegex())) -> {
                val pair = MULTI_LEXEME_TOKEN_MAP[text.toLowerCase().split("\\s+".toRegex())]!!
                pair.second
            }
            KEYWORDS.contains(text.toLowerCase()) -> TokenType.KEYWORD
            else -> TokenType.IDENTIFIER
        }
    }

    /**
     * Returns the corresponding [IonValue] for a particular ANTLR Token
     */
    private fun AntlrToken.getIonValue(): IonValue {
        return when {
            ALL_OPERATORS.contains(text.toLowerCase()) -> ion.newSymbol(text.toLowerCase())
            type == GeneratedLexer.ION_CLOSURE -> ion.singleValue(text.trimStart('`').trimEnd('`'))
            type == GeneratedLexer.TRUE -> ion.newBool(true)
            type == GeneratedLexer.FALSE -> ion.newBool(false)
            type == GeneratedLexer.NULL -> ion.newNull()
            type == GeneratedLexer.MISSING -> ion.newNull()
            type == GeneratedLexer.LITERAL_STRING -> ion.newString(text.trim('\'').replace("''", "'"))
            type == GeneratedLexer.LITERAL_INTEGER -> ion.newInt(BigInteger(text, 10))
            type == GeneratedLexer.LITERAL_DECIMAL -> {
                try {
                    ion.newDecimal(bigDecimalOf(text))
                } catch (e: NumberFormatException) {
                    throw getTokenException(e)
                }
            }
            type == GeneratedLexer.IDENTIFIER_QUOTED -> ion.newSymbol(text.trim('\"').replace("\"\"", "\""))
            MULTI_LEXEME_TOKEN_MAP.containsKey(text.toLowerCase().split("\\s+".toRegex())) -> {
                val pair = MULTI_LEXEME_TOKEN_MAP[text.toLowerCase().split("\\s+".toRegex())]!!
                ion.newSymbol(pair.first)
            }
            KEYWORDS.contains(text.toLowerCase()) -> ion.newSymbol(TYPE_ALIASES[text.toLowerCase()] ?: text.toLowerCase())
            else -> ion.newSymbol(text)
        }
    }

    /**
     * Returns the [LexerException] with metadata regarding location of the lexing error
     */
    private fun AntlrToken.getTokenException(cause: Exception): LexerException {
        val exceptionMessage = cause.message ?: "Message not found."
        val pvmap = PropertyValueMap()
        pvmap[LINE_NUMBER] = line.toLong()
        pvmap[COLUMN_NUMBER] = charPositionInLine.toLong() + 1
        pvmap[TOKEN_STRING] = exceptionMessage
        return LexerException(exceptionMessage, errorCode = ErrorCode.LEXER_INVALID_TOKEN, errorContext = pvmap, cause = cause)
    }

    /**
     * A child class to aid in throwing a [LexerException] whenever a query cannot be tokenized
     */
    class TokenizeErrorListener : BaseErrorListener() {
        @Throws(LexerException::class)
        override fun syntaxError(
            recognizer: Recognizer<*, *>?,
            offendingSymbol: Any?,
            line: Int,
            charPositionInLine: Int,
            msg: String,
            e: RecognitionException
        ) {
            val pvmap = PropertyValueMap()
            pvmap[LINE_NUMBER] = line.toLong()
            pvmap[COLUMN_NUMBER] = charPositionInLine.toLong() + 1
            pvmap[TOKEN_STRING] = msg
            throw LexerException(msg, errorContext = pvmap, errorCode = ErrorCode.LEXER_INVALID_TOKEN, cause = e)
        }

        companion object {
            @JvmField
            val INSTANCE = TokenizeErrorListener()
        }
    }
}