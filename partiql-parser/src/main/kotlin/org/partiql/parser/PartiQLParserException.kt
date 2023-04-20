package org.partiql.parser

class PartiQLParserException(
    override val message: String = "",
    override val cause: Throwable? = null,
    val context: Map<String, Any> = emptyMap(),
) : Exception() {

    companion object {

        internal fun wrap(cause: Throwable) = when (cause) {
            is PartiQLParserException -> cause
            is StackOverflowError -> PartiQLParserException(
                message = "Input query too large. This error typically occurs when there are several nested " +
                    "expressions/predicates and can usually be fixed by simplifying expressions.",
                cause = cause,
            )
            is InterruptedException -> cause
            else -> PartiQLParserException("Unhandled exception.", cause)
        }
    }
}

class PartiQLLexerException(
    override val message: String = "",
    override val cause: Throwable? = null,
    val context: Map<String, Any> = emptyMap(),
) : Exception()