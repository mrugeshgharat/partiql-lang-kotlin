package com.amazon.ionsql.errors

import com.amazon.ionsql.*
import com.amazon.ionsql.syntax.*
import com.amazon.ionsql.util.*
import org.junit.Test

class LexerErrorsTest : Base() {

    val lexer = IonSqlLexer(ion)


    private fun representation(codePoint: Int): String =
        when {
            codePoint == -1 -> "<EOF>"
            codePoint < -1 -> "<$codePoint>"
            else -> "'${String(Character.toChars(codePoint))}' [U+${Integer.toHexString(codePoint)}]"
        }

    private fun checkInputThrowingLexerException(input: String,
                                                errorCode: ErrorCode,
                                                expectErrorContextValues: Map<Property, Any>) {
        try {
            lexer.tokenize(input)
            fail("Expected LexerException but there was no Exception")
        } catch (lex: LexerException) {
            softAssert {
                checkErrorAndErrorContext(errorCode, lex, expectErrorContextValues)
            }
        } catch (ex: Exception) {
            fail("Expected LexerException but a different exception was thrown \n\t  $ex")
        }

    }

    @Test
    fun testInvalidChar() {
        checkInputThrowingLexerException("?",
            ErrorCode.LEXER_INVALID_CHAR,
            mapOf(
                Property.LINE_NUMBER to 1L,
                Property.COLUMN_NUMBER to 1L,
                Property.TOKEN_STRING to representation("?".codePointAt(0))))
    }

    @Test
    fun testInvalidOperator() {
        checkInputThrowingLexerException("10 ^ 4",
            ErrorCode.LEXER_INVALID_OPERATOR,
            mapOf(
                Property.LINE_NUMBER to 1L,
                Property.COLUMN_NUMBER to 5L,
                Property.TOKEN_STRING to "^"))
    }

 }