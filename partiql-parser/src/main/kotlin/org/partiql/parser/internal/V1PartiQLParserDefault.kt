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

package org.partiql.parser.internal

import com.amazon.ionelement.api.IntElement
import com.amazon.ionelement.api.IntElementSize
import com.amazon.ionelement.api.IonElement
import org.antlr.v4.runtime.BailErrorStrategy
import org.antlr.v4.runtime.BaseErrorListener
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer
import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.TokenSource
import org.antlr.v4.runtime.TokenStream
import org.antlr.v4.runtime.atn.PredictionMode
import org.antlr.v4.runtime.misc.ParseCancellationException
import org.antlr.v4.runtime.tree.TerminalNode
import org.partiql.ast.v1.Ast
import org.partiql.ast.v1.Ast.exclude
import org.partiql.ast.v1.Ast.excludePath
import org.partiql.ast.v1.Ast.excludeStepCollIndex
import org.partiql.ast.v1.Ast.excludeStepCollWildcard
import org.partiql.ast.v1.Ast.excludeStepStructField
import org.partiql.ast.v1.Ast.excludeStepStructWildcard
import org.partiql.ast.v1.Ast.explain
import org.partiql.ast.v1.Ast.exprAnd
import org.partiql.ast.v1.Ast.exprArray
import org.partiql.ast.v1.Ast.exprBag
import org.partiql.ast.v1.Ast.exprBetween
import org.partiql.ast.v1.Ast.exprCall
import org.partiql.ast.v1.Ast.exprCase
import org.partiql.ast.v1.Ast.exprCaseBranch
import org.partiql.ast.v1.Ast.exprCast
import org.partiql.ast.v1.Ast.exprCoalesce
import org.partiql.ast.v1.Ast.exprExtract
import org.partiql.ast.v1.Ast.exprInCollection
import org.partiql.ast.v1.Ast.exprIsType
import org.partiql.ast.v1.Ast.exprLike
import org.partiql.ast.v1.Ast.exprLit
import org.partiql.ast.v1.Ast.exprMatch
import org.partiql.ast.v1.Ast.exprNot
import org.partiql.ast.v1.Ast.exprNullIf
import org.partiql.ast.v1.Ast.exprOperator
import org.partiql.ast.v1.Ast.exprOr
import org.partiql.ast.v1.Ast.exprOverlay
import org.partiql.ast.v1.Ast.exprParameter
import org.partiql.ast.v1.Ast.exprPath
import org.partiql.ast.v1.Ast.exprPathStepAllElements
import org.partiql.ast.v1.Ast.exprPathStepAllFields
import org.partiql.ast.v1.Ast.exprPathStepElement
import org.partiql.ast.v1.Ast.exprPathStepField
import org.partiql.ast.v1.Ast.exprPosition
import org.partiql.ast.v1.Ast.exprQuerySet
import org.partiql.ast.v1.Ast.exprSessionAttribute
import org.partiql.ast.v1.Ast.exprStruct
import org.partiql.ast.v1.Ast.exprStructField
import org.partiql.ast.v1.Ast.exprSubstring
import org.partiql.ast.v1.Ast.exprTrim
import org.partiql.ast.v1.Ast.exprVarRef
import org.partiql.ast.v1.Ast.exprVariant
import org.partiql.ast.v1.Ast.exprWindow
import org.partiql.ast.v1.Ast.exprWindowOver
import org.partiql.ast.v1.Ast.from
import org.partiql.ast.v1.Ast.fromExpr
import org.partiql.ast.v1.Ast.fromJoin
import org.partiql.ast.v1.Ast.graphLabelConj
import org.partiql.ast.v1.Ast.graphLabelDisj
import org.partiql.ast.v1.Ast.graphLabelName
import org.partiql.ast.v1.Ast.graphLabelNegation
import org.partiql.ast.v1.Ast.graphLabelWildcard
import org.partiql.ast.v1.Ast.graphMatch
import org.partiql.ast.v1.Ast.graphMatchEdge
import org.partiql.ast.v1.Ast.graphMatchNode
import org.partiql.ast.v1.Ast.graphMatchPattern
import org.partiql.ast.v1.Ast.graphPattern
import org.partiql.ast.v1.Ast.graphQuantifier
import org.partiql.ast.v1.Ast.graphSelectorAllShortest
import org.partiql.ast.v1.Ast.graphSelectorAny
import org.partiql.ast.v1.Ast.graphSelectorAnyK
import org.partiql.ast.v1.Ast.graphSelectorAnyShortest
import org.partiql.ast.v1.Ast.graphSelectorShortestK
import org.partiql.ast.v1.Ast.graphSelectorShortestKGroup
import org.partiql.ast.v1.Ast.groupBy
import org.partiql.ast.v1.Ast.groupByKey
import org.partiql.ast.v1.Ast.identifier
import org.partiql.ast.v1.Ast.identifierChain
import org.partiql.ast.v1.Ast.letBinding
import org.partiql.ast.v1.Ast.orderBy
import org.partiql.ast.v1.Ast.query
import org.partiql.ast.v1.Ast.queryBodySFW
import org.partiql.ast.v1.Ast.queryBodySetOp
import org.partiql.ast.v1.Ast.selectItemExpr
import org.partiql.ast.v1.Ast.selectItemStar
import org.partiql.ast.v1.Ast.selectList
import org.partiql.ast.v1.Ast.selectPivot
import org.partiql.ast.v1.Ast.selectStar
import org.partiql.ast.v1.Ast.selectValue
import org.partiql.ast.v1.Ast.setOp
import org.partiql.ast.v1.Ast.sort
import org.partiql.ast.v1.AstNode
import org.partiql.ast.v1.DataType
import org.partiql.ast.v1.DatetimeField
import org.partiql.ast.v1.Exclude
import org.partiql.ast.v1.ExcludeStep
import org.partiql.ast.v1.From
import org.partiql.ast.v1.FromTableRef
import org.partiql.ast.v1.FromType
import org.partiql.ast.v1.GroupBy
import org.partiql.ast.v1.GroupByStrategy
import org.partiql.ast.v1.Identifier
import org.partiql.ast.v1.IdentifierChain
import org.partiql.ast.v1.JoinType
import org.partiql.ast.v1.Let
import org.partiql.ast.v1.Nulls
import org.partiql.ast.v1.Order
import org.partiql.ast.v1.Select
import org.partiql.ast.v1.SelectItem
import org.partiql.ast.v1.SetOpType
import org.partiql.ast.v1.SetQuantifier
import org.partiql.ast.v1.Sort
import org.partiql.ast.v1.Statement
import org.partiql.ast.v1.expr.Expr
import org.partiql.ast.v1.expr.ExprArray
import org.partiql.ast.v1.expr.ExprBag
import org.partiql.ast.v1.expr.ExprCall
import org.partiql.ast.v1.expr.ExprPath
import org.partiql.ast.v1.expr.ExprQuerySet
import org.partiql.ast.v1.expr.PathStep
import org.partiql.ast.v1.expr.Scope
import org.partiql.ast.v1.expr.SessionAttribute
import org.partiql.ast.v1.expr.TrimSpec
import org.partiql.ast.v1.expr.WindowFunction
import org.partiql.ast.v1.graph.GraphDirection
import org.partiql.ast.v1.graph.GraphLabel
import org.partiql.ast.v1.graph.GraphPart
import org.partiql.ast.v1.graph.GraphPattern
import org.partiql.ast.v1.graph.GraphQuantifier
import org.partiql.ast.v1.graph.GraphRestrictor
import org.partiql.ast.v1.graph.GraphSelector
import org.partiql.parser.PartiQLLexerException
import org.partiql.parser.PartiQLParserException
import org.partiql.parser.PartiQLSyntaxException
import org.partiql.parser.SourceLocation
import org.partiql.parser.SourceLocations
import org.partiql.parser.V1PartiQLParser
import org.partiql.parser.internal.antlr.PartiQLParser
import org.partiql.parser.internal.antlr.PartiQLParserBaseVisitor
import org.partiql.parser.internal.util.DateTimeUtils
import org.partiql.value.PartiQLValueExperimental
import org.partiql.value.boolValue
import org.partiql.value.dateValue
import org.partiql.value.datetime.DateTimeException
import org.partiql.value.datetime.DateTimeValue
import org.partiql.value.decimalValue
import org.partiql.value.int32Value
import org.partiql.value.int64Value
import org.partiql.value.intValue
import org.partiql.value.missingValue
import org.partiql.value.nullValue
import org.partiql.value.stringValue
import org.partiql.value.timeValue
import org.partiql.value.timestampValue
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode
import java.nio.channels.ClosedByInterruptException
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import org.partiql.parser.internal.antlr.PartiQLParser as GeneratedParser
import org.partiql.parser.internal.antlr.PartiQLTokens as GeneratedLexer

/**
 * ANTLR Based Implementation of a PartiQLParser
 *
 * SLL Prediction Mode
 * -------------------
 * The [PredictionMode.SLL] mode uses the [BailErrorStrategy]. The [GeneratedParser], upon seeing a syntax error,
 * will throw a [ParseCancellationException] due to the [GeneratedParser.getErrorHandler]
 * being a [BailErrorStrategy]. The purpose of this is to throw syntax errors as quickly as possible once encountered.
 * As noted by the [PredictionMode.SLL] documentation, to guarantee results, it is useful to follow up a failed parse
 * by parsing with [PredictionMode.LL]. See the JavaDocs for [PredictionMode.SLL] and [BailErrorStrategy] for more.
 *
 * LL Prediction Mode
 * ------------------
 * The [PredictionMode.LL] mode is capable of parsing all valid inputs for a grammar,
 * but is slower than [PredictionMode.SLL]. Upon seeing a syntax error, this parser throws a [PartiQLParserException].
 */
internal class V1PartiQLParserDefault : V1PartiQLParser {

    @Throws(PartiQLSyntaxException::class, InterruptedException::class)
    override fun parse(source: String): V1PartiQLParser.Result {
        try {
            return V1PartiQLParserDefault.parse(source)
        } catch (throwable: Throwable) {
            throw PartiQLSyntaxException.wrap(throwable)
        }
    }

    companion object {

        /**
         * To reduce latency costs, the [V1PartiQLParserDefault] attempts to use [PredictionMode.SLL] and falls back to
         * [PredictionMode.LL] if a [ParseCancellationException] is thrown by the [BailErrorStrategy].
         */
        private fun parse(source: String): V1PartiQLParser.Result = try {
            parse(source, PredictionMode.SLL)
        } catch (ex: ParseCancellationException) {
            parse(source, PredictionMode.LL)
        }

        /**
         * Parses an input string [source] using the given prediction mode.
         */
        private fun parse(source: String, mode: PredictionMode): V1PartiQLParser.Result {
            val tokens = createTokenStream(source)
            val parser = InterruptibleParser(tokens)
            parser.reset()
            parser.removeErrorListeners()
            parser.interpreter.predictionMode = mode
            when (mode) {
                PredictionMode.SLL -> parser.errorHandler = BailErrorStrategy()
                PredictionMode.LL -> parser.addErrorListener(ParseErrorListener())
                else -> throw IllegalArgumentException("Unsupported parser mode: $mode")
            }
            val tree = parser.root()
            return Visitor.translate(source, tokens, tree)
        }

        private fun createTokenStream(source: String): CountingTokenStream {
            val queryStream = source.byteInputStream(StandardCharsets.UTF_8)
            val inputStream = try {
                CharStreams.fromStream(queryStream)
            } catch (ex: ClosedByInterruptException) {
                throw InterruptedException()
            }
            val handler = TokenizeErrorListener()
            val lexer = GeneratedLexer(inputStream)
            lexer.removeErrorListeners()
            lexer.addErrorListener(handler)
            return CountingTokenStream(lexer)
        }
    }

    /**
     * Catches Lexical errors (unidentified tokens) and throws a [PartiQLParserException]
     */
    private class TokenizeErrorListener : BaseErrorListener() {
        @Throws(PartiQLParserException::class)
        override fun syntaxError(
            recognizer: Recognizer<*, *>?,
            offendingSymbol: Any?,
            line: Int,
            charPositionInLine: Int,
            msg: String,
            e: RecognitionException?,
        ) {
            if (offendingSymbol is Token) {
                val token = offendingSymbol.text
                val tokenType = GeneratedParser.VOCABULARY.getSymbolicName(offendingSymbol.type)
                throw PartiQLLexerException(
                    token = token,
                    tokenType = tokenType,
                    message = msg,
                    cause = e,
                    location = SourceLocation(
                        line = line,
                        offset = charPositionInLine + 1,
                        length = token.length,
                        lengthLegacy = token.length,
                    ),
                )
            } else {
                throw IllegalArgumentException("Offending symbol is not a Token.")
            }
        }
    }

    /**
     * Catches Parser errors (malformed syntax) and throws a [PartiQLParserException]
     */
    private class ParseErrorListener : BaseErrorListener() {

        private val rules = GeneratedParser.ruleNames.asList()

        @Throws(PartiQLParserException::class)
        override fun syntaxError(
            recognizer: Recognizer<*, *>?,
            offendingSymbol: Any,
            line: Int,
            charPositionInLine: Int,
            msg: String,
            e: RecognitionException?,
        ) {
            if (offendingSymbol is Token) {
                val rule = e?.ctx?.toString(rules) ?: "UNKNOWN"
                val token = offendingSymbol.text
                val tokenType = GeneratedParser.VOCABULARY.getSymbolicName(offendingSymbol.type)
                throw PartiQLParserException(
                    rule = rule,
                    token = token,
                    tokenType = tokenType,
                    message = msg,
                    cause = e,
                    location = SourceLocation(
                        line = line,
                        offset = charPositionInLine + 1,
                        length = msg.length,
                        lengthLegacy = offendingSymbol.text.length,
                    ),
                )
            } else {
                throw IllegalArgumentException("Offending symbol is not a Token.")
            }
        }
    }

    /**
     * A wrapped [GeneratedParser] to allow thread interruption during parse.
     */
    internal class InterruptibleParser(input: TokenStream) : GeneratedParser(input) {
        override fun enterRule(localctx: ParserRuleContext?, state: Int, ruleIndex: Int) {
            if (Thread.interrupted()) {
                throw InterruptedException()
            }
            super.enterRule(localctx, state, ruleIndex)
        }
    }

    /**
     * This token stream creates [parameterIndexes], which is a map, where the keys represent the
     * indexes of all [GeneratedLexer.QUESTION_MARK]'s and the values represent their relative index amongst all other
     * [GeneratedLexer.QUESTION_MARK]'s.
     */
    internal open class CountingTokenStream(tokenSource: TokenSource) : CommonTokenStream(tokenSource) {
        // TODO: Research use-case of parameters and implementation -- see https://github.com/partiql/partiql-docs/issues/23
        val parameterIndexes = mutableMapOf<Int, Int>()
        private var parametersFound = 0
        override fun LT(k: Int): Token? {
            val token = super.LT(k)
            token?.let {
                if (it.type == GeneratedLexer.QUESTION_MARK && parameterIndexes.containsKey(token.tokenIndex).not()) {
                    parameterIndexes[token.tokenIndex] = ++parametersFound
                }
            }
            return token
        }
    }

    /**
     * Translate an ANTLR ParseTree to a PartiQL
     */
    @OptIn(PartiQLValueExperimental::class)
    private class Visitor(
        private val tokens: CommonTokenStream,
        private val locations: SourceLocations.Mutable,
        private val parameters: Map<Int, Int> = mapOf(),
    ) : PartiQLParserBaseVisitor<AstNode>() {

        companion object {

            private val rules = GeneratedParser.ruleNames.asList()

            /**
             * Expose an (internal) friendly entry point into the traversal; mostly for keeping mutable state contained.
             */
            fun translate(
                source: String,
                tokens: CountingTokenStream,
                tree: GeneratedParser.RootContext,
            ): V1PartiQLParser.Result {
                val locations = SourceLocations.Mutable()
                val visitor = Visitor(tokens, locations, tokens.parameterIndexes)
                val root = visitor.visitAs<AstNode>(tree) as Statement
                return V1PartiQLParser.Result(
                    source = source,
                    root = root,
                    locations = locations.toMap(),
                )
            }

            fun error(
                ctx: ParserRuleContext,
                message: String,
                cause: Throwable? = null,
            ) = PartiQLParserException(
                rule = ctx.toStringTree(rules),
                token = ctx.start.text,
                tokenType = GeneratedParser.VOCABULARY.getSymbolicName(ctx.start.type),
                message = message,
                cause = cause,
                location = SourceLocation(
                    line = ctx.start.line,
                    offset = ctx.start.charPositionInLine + 1,
                    length = ctx.stop.stopIndex - ctx.start.startIndex,
                    lengthLegacy = ctx.start.text.length,
                ),
            )

            fun error(
                token: Token,
                message: String,
                cause: Throwable? = null,
            ) = PartiQLLexerException(
                token = token.text,
                tokenType = GeneratedParser.VOCABULARY.getSymbolicName(token.type),
                message = message,
                cause = cause,
                location = SourceLocation(
                    line = token.line,
                    offset = token.charPositionInLine + 1,
                    length = token.stopIndex - token.startIndex,
                    lengthLegacy = token.text.length,
                ),
            )

            internal val DATE_PATTERN_REGEX = Regex("\\d\\d\\d\\d-\\d\\d-\\d\\d")

            internal val GENERIC_TIME_REGEX = Regex("\\d\\d:\\d\\d:\\d\\d(\\.\\d*)?([+|-]\\d\\d:\\d\\d)?")
        }

        /**
         * Each visit attaches source locations from the given parse tree node; constructs nodes via the factory.
         */
        private inline fun <T : AstNode> translate(ctx: ParserRuleContext, block: () -> T): T {
            val node = block()
            if (ctx.start != null) {
                locations[node.tag] = SourceLocation(
                    line = ctx.start.line,
                    offset = ctx.start.charPositionInLine + 1,
                    length = (ctx.stop?.stopIndex ?: ctx.start.stopIndex) - ctx.start.startIndex + 1,
                    lengthLegacy = ctx.start.text.length, // LEGACY LENGTH
                )
            }
            return node
        }

        /**
         *
         * TOP LEVEL
         *
         */

        override fun visitQueryDql(ctx: GeneratedParser.QueryDqlContext): AstNode = visitDql(ctx.dql())

        override fun visitQueryDml(ctx: GeneratedParser.QueryDmlContext): AstNode = translate(ctx) {
            throw error(ctx, "DML no longer supported in the default PartiQLParser.")
        }

        override fun visitRoot(ctx: GeneratedParser.RootContext) = translate(ctx) {
            when (ctx.EXPLAIN()) {
                null -> visit(ctx.statement()) as Statement
                else -> {
                    var type: String? = null
                    var format: String? = null
                    ctx.explainOption().forEach { option ->
                        val parameter = try {
                            ExplainParameters.valueOf(option.param.text.uppercase())
                        } catch (ex: java.lang.IllegalArgumentException) {
                            throw error(option.param, "Unknown EXPLAIN parameter.", ex)
                        }
                        when (parameter) {
                            ExplainParameters.TYPE -> {
                                type = parameter.getCompliantString(type, option.value)
                            }
                            ExplainParameters.FORMAT -> {
                                format = parameter.getCompliantString(format, option.value)
                            }
                        }
                    }
                    explain(
                        // TODO get rid of usage of PartiQLValue https://github.com/partiql/partiql-lang-kotlin/issues/1589
                        options = mapOf(
                            "type" to stringValue(type),
                            "format" to stringValue(format)
                        ),
                        statement = visit(ctx.statement()) as Statement,
                    )
                }
            }
        }

        /**
         *
         * COMMON USAGES
         *
         */

        override fun visitAsIdent(ctx: GeneratedParser.AsIdentContext) = visitSymbolPrimitive(ctx.symbolPrimitive())

        override fun visitAtIdent(ctx: GeneratedParser.AtIdentContext) = visitSymbolPrimitive(ctx.symbolPrimitive())

        override fun visitByIdent(ctx: GeneratedParser.ByIdentContext) = visitSymbolPrimitive(ctx.symbolPrimitive())

        private fun visitSymbolPrimitive(ctx: GeneratedParser.SymbolPrimitiveContext): Identifier =
            when (ctx) {
                is GeneratedParser.IdentifierQuotedContext -> visitIdentifierQuoted(ctx)
                is GeneratedParser.IdentifierUnquotedContext -> visitIdentifierUnquoted(ctx)
                else -> throw error(ctx, "Invalid symbol reference.")
            }

        override fun visitIdentifierQuoted(ctx: GeneratedParser.IdentifierQuotedContext): Identifier = translate(ctx) {
            identifier(
                ctx.IDENTIFIER_QUOTED().getStringValue(),
                true
            )
        }

        override fun visitIdentifierUnquoted(ctx: GeneratedParser.IdentifierUnquotedContext): Identifier = translate(ctx) {
            identifier(
                ctx.text,
                false
            )
        }

        override fun visitQualifiedName(ctx: GeneratedParser.QualifiedNameContext) = translate(ctx) {
            val qualifier = ctx.qualifier.map { visitSymbolPrimitive(it) }
            val name = identifierChain(visitSymbolPrimitive(ctx.name), null)
            if (qualifier.isEmpty()) {
                name
            } else {
                qualifier.reversed().fold(name) { acc, id ->
                    identifierChain(root = id, next = acc)
                }
            }
        }

        /**
         *
         * DATA DEFINITION LANGUAGE (DDL) -- deleted in v1; will be added before final v1 release
         *
         */

//        override fun visitQueryDdl(ctx: GeneratedParser.QueryDdlContext): AstNode = visitDdl(ctx.ddl())
//
//        override fun visitDropTable(ctx: GeneratedParser.DropTableContext) = translate(ctx) {
//            val table = visitQualifiedName(ctx.qualifiedName())
//            statementDDLDropTable(table)
//        }
//
//        override fun visitDropIndex(ctx: GeneratedParser.DropIndexContext) = translate(ctx) {
//            val table = visitSymbolPrimitive(ctx.on)
//            val index = visitSymbolPrimitive(ctx.target)
//            statementDDLDropIndex(index, table)
//        }
//
//        override fun visitCreateTable(ctx: GeneratedParser.CreateTableContext) = translate(ctx) {
//            val table = visitQualifiedName(ctx.qualifiedName())
//            val definition = ctx.tableDef()?.let { visitTableDef(it) }
//            statementDDLCreateTable(table, definition)
//        }
//
//        override fun visitCreateIndex(ctx: GeneratedParser.CreateIndexContext) = translate(ctx) {
//            // TODO add index name to ANTLR grammar
//            val name: Identifier? = null
//            val table = visitSymbolPrimitive(ctx.symbolPrimitive())
//            val fields = ctx.pathSimple().map { path -> visitPathSimple(path) }
//            statementDDLCreateIndex(name, table, fields)
//        }
//
//        override fun visitTableDef(ctx: GeneratedParser.TableDefContext) = translate(ctx) {
//            // Column Definitions are the only thing we currently allow as table definition parts
//            val columns = ctx.tableDefPart().filterIsInstance<GeneratedParser.ColumnDeclarationContext>().map {
//                visitColumnDeclaration(it)
//            }
//            tableDefinition(columns)
//        }
//
//        override fun visitColumnDeclaration(ctx: GeneratedParser.ColumnDeclarationContext) = translate(ctx) {
//            val name = visitSymbolPrimitive(ctx.columnName().symbolPrimitive()).symbol
//            val type = visit(ctx.type()) as Type
//            val constraints = ctx.columnConstraint().map {
//                visitColumnConstraint(it)
//            }
//            tableDefinitionColumn(name, type, constraints)
//        }
//
//        override fun visitColumnConstraint(ctx: GeneratedParser.ColumnConstraintContext) = translate(ctx) {
//            val identifier = ctx.columnConstraintName()?.let { symbolToString(it.symbolPrimitive()) }
//            val body = visit(ctx.columnConstraintDef()) as TableDefinition.Column.Constraint.Body
//            tableDefinitionColumnConstraint(identifier, body)
//        }
//
//        override fun visitColConstrNotNull(ctx: GeneratedParser.ColConstrNotNullContext) = translate(ctx) {
//            tableDefinitionColumnConstraintBodyNotNull()
//        }
//
//        override fun visitColConstrNull(ctx: GeneratedParser.ColConstrNullContext) = translate(ctx) {
//            tableDefinitionColumnConstraintBodyNullable()
//        }

        /**
         *
         * EXECUTE
         *
         */

        override fun visitQueryExec(ctx: GeneratedParser.QueryExecContext) = translate(ctx) {
            throw error(ctx, "EXEC no longer supported in the default PartiQLParser.")
        }

        /**
         * TODO EXEC accepts an `expr` as the procedure name so we have to unpack the string.
         *  - https://github.com/partiql/partiql-lang-kotlin/issues/707
         */
        override fun visitExecCommand(ctx: GeneratedParser.ExecCommandContext) = translate(ctx) {
            throw error(ctx, "EXEC no longer supported in the default PartiQLParser.")
        }

        /**
         *
         * DATA MANIPULATION LANGUAGE (DML)
         *
         */

        /**
         * The PartiQL grammars allows for multiple DML commands in one UPDATE statement.
         * This function unwraps DML commands to the more limited DML.BatchLegacy.Op commands.
         */
        override fun visitDmlBaseWrapper(ctx: GeneratedParser.DmlBaseWrapperContext) = translate(ctx) {
            throw error(ctx, "DML no longer supported in the default PartiQLParser.")
        }

        override fun visitDmlDelete(ctx: GeneratedParser.DmlDeleteContext) = translate(ctx) {
            throw error(ctx, "DML no longer supported in the default PartiQLParser.")
        }

        override fun visitDmlInsertReturning(ctx: GeneratedParser.DmlInsertReturningContext) = translate(ctx) {
            throw error(ctx, "DML no longer supported in the default PartiQLParser.")
        }

        override fun visitDmlBase(ctx: GeneratedParser.DmlBaseContext) = translate(ctx) {
            throw error(ctx, "DML no longer supported in the default PartiQLParser.")
        }

        override fun visitDmlBaseCommand(ctx: GeneratedParser.DmlBaseCommandContext) = translate(ctx) {
            throw error(ctx, "DML no longer supported in the default PartiQLParser.")
        }

        override fun visitRemoveCommand(ctx: GeneratedParser.RemoveCommandContext) = translate(ctx) {
            throw error(ctx, "DML no longer supported in the default PartiQLParser.")
        }

        override fun visitDeleteCommand(ctx: GeneratedParser.DeleteCommandContext) = translate(ctx) {
            throw error(ctx, "DML no longer supported in the default PartiQLParser.")
        }

        /**
         * Legacy INSERT with RETURNING clause is not represented in the AST as this grammar ..
         * .. only exists for backwards compatibility. The RETURNING clause is ignored.
         *
         * TODO remove insertCommandReturning grammar rule
         *  - https://github.com/partiql/partiql-lang-kotlin/issues/698
         *  - https://github.com/partiql/partiql-lang-kotlin/issues/708
         */
        override fun visitInsertCommandReturning(ctx: GeneratedParser.InsertCommandReturningContext) = translate(ctx) {
            throw error(ctx, "DML no longer supported in the default PartiQLParser.")
        }

        override fun visitInsertStatementLegacy(ctx: GeneratedParser.InsertStatementLegacyContext) = translate(ctx) {
            throw error(ctx, "DML no longer supported in the default PartiQLParser.")
        }

        override fun visitInsertStatement(ctx: GeneratedParser.InsertStatementContext) = translate(ctx) {
            throw error(ctx, "DML no longer supported in the default PartiQLParser.")
        }

        override fun visitReplaceCommand(ctx: GeneratedParser.ReplaceCommandContext) = translate(ctx) {
            throw error(ctx, "DML no longer supported in the default PartiQLParser.")
        }

        override fun visitUpsertCommand(ctx: GeneratedParser.UpsertCommandContext) = translate(ctx) {
            throw error(ctx, "DML no longer supported in the default PartiQLParser.")
        }

        override fun visitReturningClause(ctx: GeneratedParser.ReturningClauseContext) = translate(ctx) {
            throw error(ctx, "DML no longer supported in the default PartiQLParser.")
        }

        override fun visitReturningColumn(ctx: GeneratedParser.ReturningColumnContext) = translate(ctx) {
            throw error(ctx, "DML no longer supported in the default PartiQLParser.")
        }

        override fun visitOnConflict(ctx: GeneratedParser.OnConflictContext) = translate(ctx) {
            throw error(ctx, "DML no longer supported in the default PartiQLParser.")
        }

        /**
         * TODO Remove this when we remove INSERT LEGACY as no other conflict actions are allowed in PartiQL.g4.
         */
        override fun visitOnConflictLegacy(ctx: GeneratedParser.OnConflictLegacyContext) = translate(ctx) {
            throw error(ctx, "DML no longer supported in the default PartiQLParser.")
        }

        override fun visitConflictTarget(ctx: GeneratedParser.ConflictTargetContext) = translate(ctx) {
            throw error(ctx, "DML no longer supported in the default PartiQLParser.")
        }

        override fun visitConflictAction(ctx: GeneratedParser.ConflictActionContext) = translate(ctx) {
            throw error(ctx, "DML no longer supported in the default PartiQLParser.")
        }

        override fun visitDoReplace(ctx: GeneratedParser.DoReplaceContext) = translate(ctx) {
            throw error(ctx, "DML no longer supported in the default PartiQLParser.")
        }

        override fun visitDoUpdate(ctx: GeneratedParser.DoUpdateContext) = translate(ctx) {
            throw error(ctx, "DML no longer supported in the default PartiQLParser.")
        }

        // "simple paths" used by previous DDL's CREATE INDEX
        override fun visitPathSimple(ctx: GeneratedParser.PathSimpleContext) = translate(ctx) {
            throw error(ctx, "DDL no longer supported in the default PartiQLParser.")
        }

        // "simple paths" used by previous DDL's CREATE INDEX
        override fun visitPathSimpleLiteral(ctx: GeneratedParser.PathSimpleLiteralContext) = translate(ctx) {
            throw error(ctx, "DDL no longer supported in the default PartiQLParser.")
        }

        // "simple paths" used by previous DDL's CREATE INDEX
        override fun visitPathSimpleSymbol(ctx: GeneratedParser.PathSimpleSymbolContext) = translate(ctx) {
            throw error(ctx, "DDL no longer supported in the default PartiQLParser.")
        }

        // "simple paths" used by previous DDL's CREATE INDEX
        override fun visitPathSimpleDotSymbol(ctx: GeneratedParser.PathSimpleDotSymbolContext) = translate(ctx) {
            throw error(ctx, "DDL no longer supported in the default PartiQLParser.")
        }

        /**
         * TODO current PartiQL.g4 grammar models a SET with no UPDATE target as valid DML command.
         */
        override fun visitSetCommand(ctx: GeneratedParser.SetCommandContext) = translate(ctx) {
            throw error(ctx, "DML no longer supported in the default PartiQLParser.")
        }

        override fun visitSetAssignment(ctx: GeneratedParser.SetAssignmentContext) = translate(ctx) {
            throw error(ctx, "DML no longer supported in the default PartiQLParser.")
        }

        /**
         *
         * DATA QUERY LANGUAGE (DQL)
         *
         */

        override fun visitDql(ctx: GeneratedParser.DqlContext) = translate(ctx) {
            val expr = visitAs<Expr>(ctx.expr())
            query(expr)
        }

        override fun visitQueryBase(ctx: GeneratedParser.QueryBaseContext): AstNode = visit(ctx.exprSelect())

        override fun visitSfwQuery(ctx: GeneratedParser.SfwQueryContext) = translate(ctx) {
            val select = visit(ctx.select) as Select
            val from = visitFromClause(ctx.from)
            val exclude = visitOrNull<Exclude>(ctx.exclude)
            val let = visitOrNull<Let>(ctx.let)
            val where = visitOrNull<Expr>(ctx.where)
            val groupBy = ctx.group?.let { visitGroupClause(it) }
            val having = visitOrNull<Expr>(ctx.having?.arg)
            val orderBy = ctx.order?.let { visitOrderByClause(it) }
            val limit = visitOrNull<Expr>(ctx.limit?.arg)
            val offset = visitOrNull<Expr>(ctx.offset?.arg)
            exprQuerySet(
                body = queryBodySFW(
                    select, exclude, from, let, where, groupBy, having
                ),
                orderBy = orderBy,
                limit = limit,
                offset = offset
            )
        }

        /**
         *
         * SELECT & PROJECTIONS
         *
         */

        override fun visitSelectAll(ctx: GeneratedParser.SelectAllContext) = translate(ctx) {
            val quantifier = convertSetQuantifier(ctx.setQuantifierStrategy())
            selectStar(quantifier)
        }

        override fun visitSelectItems(ctx: GeneratedParser.SelectItemsContext) = translate(ctx) {
            val items = visitOrEmpty<SelectItem>(ctx.projectionItems().projectionItem())
            val setq = convertSetQuantifier(ctx.setQuantifierStrategy())
            selectList(items, setq)
        }

        override fun visitSelectPivot(ctx: GeneratedParser.SelectPivotContext) = translate(ctx) {
            val key = visitExpr(ctx.at)
            val value = visitExpr(ctx.pivot)
            selectPivot(key, value)
        }

        override fun visitSelectValue(ctx: GeneratedParser.SelectValueContext) = translate(ctx) {
            val constructor = visitExpr(ctx.expr())
            val setq = convertSetQuantifier(ctx.setQuantifierStrategy())
            selectValue(constructor, setq)
        }

        override fun visitProjectionItem(ctx: GeneratedParser.ProjectionItemContext) = translate(ctx) {
            val expr = visitExpr(ctx.expr())
            val alias = ctx.symbolPrimitive()?.let { visitSymbolPrimitive(it) }
            if (expr is ExprPath) {
                convertPathToProjectionItem(ctx, expr, alias)
            } else {
                selectItemExpr(expr, alias)
            }
        }

        /**
         *
         * SIMPLE CLAUSES
         *
         */

        override fun visitLimitClause(ctx: GeneratedParser.LimitClauseContext): Expr = visitAs<Expr>(ctx.arg)

        override fun visitExpr(ctx: GeneratedParser.ExprContext): Expr {
            if (Thread.interrupted()) {
                throw InterruptedException()
            }
            return visitAs<Expr>(ctx.exprBagOp())
        }

        override fun visitOffsetByClause(ctx: GeneratedParser.OffsetByClauseContext) = visitAs<Expr>(ctx.arg)

        override fun visitWhereClause(ctx: GeneratedParser.WhereClauseContext) = visitExpr(ctx.arg)

        override fun visitWhereClauseSelect(ctx: GeneratedParser.WhereClauseSelectContext) = visitAs<Expr>(ctx.arg)

        override fun visitHavingClause(ctx: GeneratedParser.HavingClauseContext) = visitAs<Expr>(ctx.arg)

        /**
         *
         * LET CLAUSE
         *
         */

        override fun visitLetClause(ctx: GeneratedParser.LetClauseContext) = translate(ctx) {
            val bindings = visitOrEmpty<Let.Binding>(ctx.letBinding())
            Ast.let(bindings)
        }

        override fun visitLetBinding(ctx: GeneratedParser.LetBindingContext) = translate(ctx) {
            val expr = visitAs<Expr>(ctx.expr())
            val alias = visitSymbolPrimitive(ctx.symbolPrimitive())
            letBinding(expr, alias)
        }

        /**
         *
         * ORDER BY CLAUSE
         *
         */

        override fun visitOrderByClause(ctx: GeneratedParser.OrderByClauseContext) = translate(ctx) {
            val sorts = visitOrEmpty<Sort>(ctx.orderSortSpec())
            orderBy(sorts)
        }

        override fun visitOrderSortSpec(ctx: GeneratedParser.OrderSortSpecContext) = translate(ctx) {
            val expr = visitAs<Expr>(ctx.expr())
            val dir = when {
                ctx.dir == null -> null
                ctx.dir.type == GeneratedParser.ASC -> Order.ASC()
                ctx.dir.type == GeneratedParser.DESC -> Order.DESC()
                else -> throw error(ctx.dir, "Invalid ORDER BY direction; expected ASC or DESC")
            }
            val nulls = when {
                ctx.nulls == null -> null
                ctx.nulls.type == GeneratedParser.FIRST -> Nulls.FIRST()
                ctx.nulls.type == GeneratedParser.LAST -> Nulls.LAST()
                else -> throw error(ctx.nulls, "Invalid ORDER BY null ordering; expected FIRST or LAST")
            }
            sort(expr, dir, nulls)
        }

        /**
         *
         * GROUP BY CLAUSE
         *
         */

        override fun visitGroupClause(ctx: GeneratedParser.GroupClauseContext) = translate(ctx) {
            val strategy = if (ctx.PARTIAL() != null) GroupByStrategy.PARTIAL() else GroupByStrategy.FULL()
            val keys = visitOrEmpty<GroupBy.Key>(ctx.groupKey())
            val alias = ctx.groupAlias()?.symbolPrimitive()?.let { visitSymbolPrimitive(it) }
            groupBy(strategy, keys, alias)
        }

        override fun visitGroupKey(ctx: GeneratedParser.GroupKeyContext) = translate(ctx) {
            val expr = visitAs<Expr>(ctx.key)
            val alias = ctx.symbolPrimitive()?.let { visitSymbolPrimitive(it) }
            groupByKey(expr, alias)
        }

        /**
         * EXCLUDE CLAUSE
         */
        override fun visitExcludeClause(ctx: GeneratedParser.ExcludeClauseContext) = translate(ctx) {
            val excludeExprs = ctx.excludeExpr().map { expr ->
                visitExcludeExpr(expr)
            }
            exclude(excludeExprs)
        }

        override fun visitExcludeExpr(ctx: GeneratedParser.ExcludeExprContext) = translate(ctx) {
            val rootId = visitSymbolPrimitive(ctx.symbolPrimitive())
            val root = exprVarRef(identifierChain(rootId, null), Scope.DEFAULT())
            val steps = visitOrEmpty<ExcludeStep>(ctx.excludeExprSteps())
            excludePath(root, steps)
        }

        override fun visitExcludeExprTupleAttr(ctx: GeneratedParser.ExcludeExprTupleAttrContext) = translate(ctx) {
            val identifier = visitSymbolPrimitive(ctx.symbolPrimitive())
            excludeStepStructField(identifier)
        }

        override fun visitExcludeExprCollectionIndex(ctx: GeneratedParser.ExcludeExprCollectionIndexContext) =
            translate(ctx) {
                val index = ctx.index.text.toInt()
                excludeStepCollIndex(index)
            }

        override fun visitExcludeExprCollectionAttr(ctx: GeneratedParser.ExcludeExprCollectionAttrContext) =
            translate(ctx) {
                val attr = ctx.attr.getStringValue()
                val identifier = identifier(attr, true)
                excludeStepStructField(identifier)
            }

        override fun visitExcludeExprCollectionWildcard(ctx: GeneratedParser.ExcludeExprCollectionWildcardContext) =
            translate(ctx) {
                excludeStepCollWildcard()
            }

        override fun visitExcludeExprTupleWildcard(ctx: GeneratedParser.ExcludeExprTupleWildcardContext) =
            translate(ctx) {
                excludeStepStructWildcard()
            }

        /**
         *
         * BAG OPERATIONS
         *
         */
        override fun visitBagOp(ctx: GeneratedParser.BagOpContext) = translate(ctx) {
            val setq = when {
                ctx.ALL() != null -> SetQuantifier.ALL()
                ctx.DISTINCT() != null -> SetQuantifier.DISTINCT()
                else -> null
            }
            val op = when (ctx.op.type) {
                GeneratedParser.UNION -> setOp(SetOpType.UNION(), setq)
                GeneratedParser.INTERSECT -> setOp(SetOpType.INTERSECT(), setq)
                GeneratedParser.EXCEPT -> setOp(SetOpType.EXCEPT(), setq)
                else -> error("Unsupported bag op token ${ctx.op}")
            }
            val lhs = visitAs<Expr>(ctx.lhs)
            val rhs = visitAs<Expr>(ctx.rhs)
            val outer = ctx.OUTER() != null
            val orderBy = ctx.order?.let { visitOrderByClause(it) }
            val limit = ctx.limit?.let { visitAs<Expr>(it) }
            val offset = ctx.offset?.let { visitAs<Expr>(it) }
            exprQuerySet(
                queryBodySetOp(
                    op,
                    outer,
                    lhs,
                    rhs
                ),
                orderBy,
                limit,
                offset,
            )
        }

        /**
         *
         * GRAPH PATTERN MANIPULATION LANGUAGE (GPML)
         *
         */

        override fun visitGpmlPattern(ctx: GeneratedParser.GpmlPatternContext) = translate(ctx) {
            val pattern = visitMatchPattern(ctx.matchPattern())
            val selector = visitOrNull<GraphSelector>(ctx.matchSelector())
            graphMatch(listOf(pattern), selector)
        }

        override fun visitGpmlPatternList(ctx: GeneratedParser.GpmlPatternListContext) = translate(ctx) {
            val patterns = ctx.matchPattern().map { pattern -> visitMatchPattern(pattern) }
            val selector = visitOrNull<GraphSelector>(ctx.matchSelector())
            graphMatch(patterns, selector)
        }

        override fun visitMatchPattern(ctx: GeneratedParser.MatchPatternContext) = translate(ctx) {
            val parts = visitOrEmpty<GraphPart>(ctx.graphPart())
            val restrictor = ctx.restrictor?.let {
                when (ctx.restrictor.text.lowercase()) {
                    "trail" -> GraphRestrictor.TRAIL()
                    "acyclic" -> GraphRestrictor.ACYCLIC()
                    "simple" -> GraphRestrictor.SIMPLE()
                    else -> throw error(ctx.restrictor, "Unrecognized pattern restrictor")
                }
            }
            val variable = visitOrNull<Identifier>(ctx.variable)?.symbol
            graphPattern(restrictor, null, variable, null, parts)
        }

        override fun visitPatternPathVariable(ctx: GeneratedParser.PatternPathVariableContext) =
            visitSymbolPrimitive(ctx.symbolPrimitive())

        override fun visitSelectorBasic(ctx: GeneratedParser.SelectorBasicContext) = translate(ctx) {
            when (ctx.mod.type) {
                GeneratedParser.ANY -> graphSelectorAnyShortest()
                GeneratedParser.ALL -> graphSelectorAllShortest()
                else -> throw error(ctx, "Unsupported match selector.")
            }
        }

        override fun visitSelectorAny(ctx: GeneratedParser.SelectorAnyContext) = translate(ctx) {
            when (ctx.k) {
                null -> graphSelectorAny()
                else -> graphSelectorAnyK(ctx.k.text.toLong())
            }
        }

        override fun visitSelectorShortest(ctx: GeneratedParser.SelectorShortestContext) = translate(ctx) {
            val k = ctx.k.text.toLong()
            when (ctx.GROUP()) {
                null -> graphSelectorShortestK(k)
                else -> graphSelectorShortestKGroup(k)
            }
        }

        override fun visitLabelSpecOr(ctx: GeneratedParser.LabelSpecOrContext) = translate(ctx) {
            val lhs = visit(ctx.labelSpec()) as GraphLabel
            val rhs = visit(ctx.labelTerm()) as GraphLabel
            graphLabelDisj(lhs, rhs)
        }

        override fun visitLabelTermAnd(ctx: GeneratedParser.LabelTermAndContext) = translate(ctx) {
            val lhs = visit(ctx.labelTerm()) as GraphLabel
            val rhs = visit(ctx.labelFactor()) as GraphLabel
            graphLabelConj(lhs, rhs)
        }

        override fun visitLabelFactorNot(ctx: GeneratedParser.LabelFactorNotContext) = translate(ctx) {
            val arg = visit(ctx.labelPrimary()) as GraphLabel
            graphLabelNegation(arg)
        }

        override fun visitLabelPrimaryName(ctx: GeneratedParser.LabelPrimaryNameContext) = translate(ctx) {
            val x = visitSymbolPrimitive(ctx.symbolPrimitive())
            graphLabelName(x.symbol)
        }

        override fun visitLabelPrimaryWild(ctx: GeneratedParser.LabelPrimaryWildContext) = translate(ctx) {
            graphLabelWildcard()
        }

        override fun visitLabelPrimaryParen(ctx: GeneratedParser.LabelPrimaryParenContext) =
            visit(ctx.labelSpec()) as GraphLabel

        override fun visitPattern(ctx: GeneratedParser.PatternContext) = translate(ctx) {
            val restrictor = visitRestrictor(ctx.restrictor)
            val variable = visitOrNull<Identifier>(ctx.variable)?.symbol
            val prefilter = ctx.where?.let { visitExpr(it.expr()) }
            val quantifier = ctx.quantifier?.let { visitPatternQuantifier(it) }
            val parts = visitOrEmpty<GraphPart>(ctx.graphPart())
            graphPattern(restrictor, prefilter, variable, quantifier, parts)
        }

        override fun visitEdgeAbbreviated(ctx: GeneratedParser.EdgeAbbreviatedContext) = translate(ctx) {
            val direction = visitEdge(ctx.edgeAbbrev())
            val quantifier = visitOrNull<GraphQuantifier>(ctx.quantifier)
            graphMatchEdge(direction, quantifier, null, null, null)
        }

        private fun GraphPart.Edge.copy(
            direction: GraphDirection? = null,
            quantifier: GraphQuantifier? = null,
            prefilter: Expr? = null,
            variable: String? = null,
            label: GraphLabel? = null,
        ) = graphMatchEdge(
            direction = direction ?: this.direction,
            quantifier = quantifier ?: this.quantifier,
            prefilter = prefilter ?: this.prefilter,
            variable = variable ?: this.variable,
            label = label ?: this.label,
        )

        override fun visitEdgeWithSpec(ctx: GeneratedParser.EdgeWithSpecContext) = translate(ctx) {
            val quantifier = visitOrNull<GraphQuantifier>(ctx.quantifier)
            val edge = visitOrNull<GraphPart.Edge>(ctx.edgeWSpec())
            edge!!.copy(quantifier = quantifier)
        }

        override fun visitEdgeSpec(ctx: GeneratedParser.EdgeSpecContext) = translate(ctx) {
            val placeholderDirection = GraphDirection.RIGHT()
            val variable = visitOrNull<Identifier>(ctx.symbolPrimitive())?.symbol
            val prefilter = ctx.whereClause()?.let { visitExpr(it.expr()) }
            val label = visitOrNull<GraphLabel>(ctx.labelSpec())
            graphMatchEdge(placeholderDirection, null, prefilter, variable, label)
        }

        override fun visitEdgeSpecLeft(ctx: GeneratedParser.EdgeSpecLeftContext): AstNode {
            val edge = visitEdgeSpec(ctx.edgeSpec())
            return edge.copy(direction = GraphDirection.LEFT())
        }

        override fun visitEdgeSpecRight(ctx: GeneratedParser.EdgeSpecRightContext): AstNode {
            val edge = visitEdgeSpec(ctx.edgeSpec())
            return edge.copy(direction = GraphDirection.RIGHT())
        }

        override fun visitEdgeSpecBidirectional(ctx: GeneratedParser.EdgeSpecBidirectionalContext): AstNode {
            val edge = visitEdgeSpec(ctx.edgeSpec())
            return edge.copy(direction = GraphDirection.LEFT_OR_RIGHT())
        }

        override fun visitEdgeSpecUndirectedBidirectional(ctx: GeneratedParser.EdgeSpecUndirectedBidirectionalContext): AstNode {
            val edge = visitEdgeSpec(ctx.edgeSpec())
            return edge.copy(direction = GraphDirection.LEFT_UNDIRECTED_OR_RIGHT())
        }

        override fun visitEdgeSpecUndirected(ctx: GeneratedParser.EdgeSpecUndirectedContext): AstNode {
            val edge = visitEdgeSpec(ctx.edgeSpec())
            return edge.copy(direction = GraphDirection.UNDIRECTED())
        }

        override fun visitEdgeSpecUndirectedLeft(ctx: GeneratedParser.EdgeSpecUndirectedLeftContext): AstNode {
            val edge = visitEdgeSpec(ctx.edgeSpec())
            return edge.copy(direction = GraphDirection.LEFT_OR_UNDIRECTED())
        }

        override fun visitEdgeSpecUndirectedRight(ctx: GeneratedParser.EdgeSpecUndirectedRightContext): AstNode {
            val edge = visitEdgeSpec(ctx.edgeSpec())
            return edge.copy(direction = GraphDirection.UNDIRECTED_OR_RIGHT())
        }

        private fun visitEdge(ctx: GeneratedParser.EdgeAbbrevContext): GraphDirection = when {
            ctx.TILDE() != null && ctx.ANGLE_RIGHT() != null -> GraphDirection.UNDIRECTED_OR_RIGHT()
            ctx.TILDE() != null && ctx.ANGLE_LEFT() != null -> GraphDirection.LEFT_OR_UNDIRECTED()
            ctx.TILDE() != null -> GraphDirection.UNDIRECTED()
            ctx.MINUS() != null && ctx.ANGLE_LEFT() != null && ctx.ANGLE_RIGHT() != null -> GraphDirection.LEFT_OR_RIGHT()
            ctx.MINUS() != null && ctx.ANGLE_LEFT() != null -> GraphDirection.LEFT()
            ctx.MINUS() != null && ctx.ANGLE_RIGHT() != null -> GraphDirection.RIGHT()
            ctx.MINUS() != null -> GraphDirection.LEFT_UNDIRECTED_OR_RIGHT()
            else -> throw error(ctx, "Unsupported edge type")
        }

        override fun visitGraphPart(ctx: GeneratedParser.GraphPartContext): GraphPart {
            val part = super.visitGraphPart(ctx)
            if (part is GraphPattern) {
                return translate(ctx) { graphMatchPattern(part) }
            }
            return part as GraphPart
        }

        override fun visitPatternQuantifier(ctx: GeneratedParser.PatternQuantifierContext) = translate(ctx) {
            when {
                ctx.quant == null -> graphQuantifier(ctx.lower.text.toLong(), ctx.upper?.text?.toLong())
                ctx.quant.type == GeneratedParser.PLUS -> graphQuantifier(1L, null)
                ctx.quant.type == GeneratedParser.ASTERISK -> graphQuantifier(0L, null)
                else -> throw error(ctx, "Unsupported quantifier")
            }
        }

        override fun visitNode(ctx: GeneratedParser.NodeContext) = translate(ctx) {
            val variable = visitOrNull<Identifier>(ctx.symbolPrimitive())?.symbol
            val prefilter = ctx.whereClause()?.let { visitExpr(it.expr()) }
            val label = visitOrNull<GraphLabel>(ctx.labelSpec())
            graphMatchNode(prefilter, variable, label)
        }

        private fun visitRestrictor(ctx: GeneratedParser.PatternRestrictorContext?): GraphRestrictor? {
            if (ctx == null) return null
            return when (ctx.restrictor.text.lowercase()) {
                "trail" -> GraphRestrictor.TRAIL()
                "acyclic" -> GraphRestrictor.ACYCLIC()
                "simple" -> GraphRestrictor.SIMPLE()
                else -> throw error(ctx, "Unrecognized pattern restrictor")
            }
        }

        /**
         *
         * TABLE REFERENCES & JOINS & FROM CLAUSE
         *
         */
        override fun visitFromClause(ctx: GeneratedParser.FromClauseContext): From = translate(ctx) {
            val tableRefs = visitOrEmpty<FromTableRef>(ctx.tableReference())
            from(tableRefs)
        }

        override fun visitTableBaseRefSymbol(ctx: PartiQLParser.TableBaseRefSymbolContext): FromTableRef = translate(ctx) {
            val expr = visitAs<Expr>(ctx.source)
            val asAlias = visitSymbolPrimitive(ctx.symbolPrimitive())
            fromExpr(expr, FromType.SCAN(), asAlias, null)
        }

        override fun visitTableBaseRefClauses(ctx: PartiQLParser.TableBaseRefClausesContext): FromTableRef = translate(ctx) {
            val expr = visitAs<Expr>(ctx.source)
            val asAlias = ctx.asIdent()?.let { visitSymbolPrimitive(it.symbolPrimitive()) }
            val atAlias = ctx.atIdent()?.let { visitSymbolPrimitive(it.symbolPrimitive()) }
            fromExpr(expr, FromType.SCAN(), asAlias, atAlias)
        }

        override fun visitTableBaseRefMatch(ctx: PartiQLParser.TableBaseRefMatchContext): FromTableRef = translate(ctx) {
            val expr = visitAs<Expr>(ctx.source)
            val asAlias = ctx.asIdent()?.let { visitSymbolPrimitive(it.symbolPrimitive()) }
            val atAlias = ctx.atIdent()?.let { visitSymbolPrimitive(it.symbolPrimitive()) }
            fromExpr(expr, FromType.SCAN(), asAlias, atAlias)
        }

        override fun visitTableUnpivot(ctx: PartiQLParser.TableUnpivotContext): FromTableRef = translate(ctx) {
            val expr = visitAs<Expr>(ctx.expr())
            val asAlias = ctx.asIdent()?.let { visitSymbolPrimitive(it.symbolPrimitive()) }
            val atAlias = ctx.atIdent()?.let { visitSymbolPrimitive(it.symbolPrimitive()) }
            fromExpr(expr, FromType.UNPIVOT(), asAlias, atAlias)
        }

        override fun visitTableWrapped(ctx: PartiQLParser.TableWrappedContext): FromTableRef = translate(ctx) {
            visitAs<FromTableRef>(ctx.tableReference())
        }

        override fun visitTableCrossJoin(ctx: PartiQLParser.TableCrossJoinContext): FromTableRef = translate(ctx) {
            val lhs = visitAs<FromTableRef>(ctx.lhs)
            val rhs = visitAs<FromTableRef>(ctx.rhs)
            // TODO support other CROSS JOIN variants (e.g. LEFT CROSS JOIN)
            fromJoin(lhs, rhs, JoinType.CROSS(), null)
        }

        override fun visitTableQualifiedJoin(ctx: PartiQLParser.TableQualifiedJoinContext): FromTableRef = translate(ctx) {
            val lhs = visitAs<FromTableRef>(ctx.lhs)
            val rhs = visitAs<FromTableRef>(ctx.rhs)
            val type = convertJoinType(ctx.joinType())
            val condition = ctx.joinSpec()?.let { visitExpr(it.expr()) }
            fromJoin(lhs, rhs, type, condition)
        }

        private fun convertJoinType(ctx: GeneratedParser.JoinTypeContext?): JoinType? {
            if (ctx == null) return null
            return when (ctx.mod.type) {
                GeneratedParser.INNER -> JoinType.INNER()
                GeneratedParser.LEFT -> when (ctx.OUTER()) {
                    null -> JoinType.LEFT()
                    else -> JoinType.LEFT_OUTER()
                }
                GeneratedParser.RIGHT -> when (ctx.OUTER()) {
                    null -> JoinType.RIGHT()
                    else -> JoinType.RIGHT_OUTER()
                }
                GeneratedParser.FULL -> when (ctx.OUTER()) {
                    null -> JoinType.FULL()
                    else -> JoinType.FULL_OUTER()
                }
                GeneratedParser.OUTER -> {
                    // TODO https://github.com/partiql/partiql-spec/issues/41
                    // TODO https://github.com/partiql/partiql-lang-kotlin/issues/1013
                    JoinType.FULL_OUTER()
                }
                else -> null
            }
        }

        /**
         * TODO Remove as/at/by aliases from DELETE command grammar in PartiQL.g4
         */
        override fun visitFromClauseSimpleExplicit(ctx: GeneratedParser.FromClauseSimpleExplicitContext) = translate(ctx) {
            throw error(ctx, "DML no longer supported in the default PartiQLParser.")
        }

        /**
         * TODO Remove fromClauseSimple rule from DELETE command grammar in PartiQL.g4
         */
        override fun visitFromClauseSimpleImplicit(ctx: GeneratedParser.FromClauseSimpleImplicitContext) = translate(ctx) {
            throw error(ctx, "DML no longer supported in the default PartiQLParser.")
        }

        /**
         * SIMPLE EXPRESSIONS
         */

        override fun visitOr(ctx: GeneratedParser.OrContext) = translate(ctx) {
            val l = visit(ctx.lhs) as Expr
            val r = visit(ctx.rhs) as Expr
            exprOr(l, r)
        }

        override fun visitAnd(ctx: GeneratedParser.AndContext) = translate(ctx) {
            val l = visit(ctx.lhs) as Expr
            val r = visit(ctx.rhs) as Expr
            exprAnd(l, r)
        }

        override fun visitNot(ctx: GeneratedParser.NotContext) = translate(ctx) {
            val expr = visit(ctx.exprNot()) as Expr
            exprNot(expr)
        }

        private fun checkForInvalidTokens(op: ParserRuleContext) {
            val start = op.start.tokenIndex
            val stop = op.stop.tokenIndex
            val tokensInRange = tokens.get(start, stop)
            if (tokensInRange.any { it.channel == GeneratedLexer.HIDDEN }) {
                throw error(op, "Invalid whitespace or comment in operator")
            }
        }

        private fun convertToOperator(value: ParserRuleContext, op: ParserRuleContext): Expr {
            checkForInvalidTokens(op)
            return convertToOperator(value, op.text)
        }

        private fun convertToOperator(value: ParserRuleContext, op: String): Expr {
            val v = visit(value) as Expr
            return exprOperator(op, null, v)
        }

        private fun convertToOperator(lhs: ParserRuleContext, rhs: ParserRuleContext, op: ParserRuleContext): Expr {
            checkForInvalidTokens(op)
            return convertToOperator(lhs, rhs, op.text)
        }

        private fun convertToOperator(lhs: ParserRuleContext, rhs: ParserRuleContext, op: String): Expr {
            val l = visit(lhs) as Expr
            val r = visit(rhs) as Expr
            return exprOperator(op, l, r)
        }

        override fun visitMathOp00(ctx: GeneratedParser.MathOp00Context) = translate(ctx) {
            if (ctx.parent != null) return@translate visit(ctx.parent)
            convertToOperator(ctx.lhs, ctx.rhs, ctx.op)
        }

        override fun visitMathOp01(ctx: GeneratedParser.MathOp01Context) = translate(ctx) {
            if (ctx.parent != null) return@translate visit(ctx.parent)
            convertToOperator(ctx.rhs, ctx.op)
        }

        override fun visitMathOp02(ctx: GeneratedParser.MathOp02Context) = translate(ctx) {
            if (ctx.parent != null) return@translate visit(ctx.parent)
            convertToOperator(ctx.lhs, ctx.rhs, ctx.op.text)
        }

        override fun visitMathOp03(ctx: GeneratedParser.MathOp03Context) = translate(ctx) {
            if (ctx.parent != null) return@translate visit(ctx.parent)
            convertToOperator(ctx.lhs, ctx.rhs, ctx.op.text)
        }

        override fun visitValueExpr(ctx: GeneratedParser.ValueExprContext) = translate(ctx) {
            if (ctx.parent != null) return@translate visit(ctx.parent)
            convertToOperator(ctx.rhs, ctx.sign.text)
        }

        /**
         *
         * PREDICATES
         *
         */

        override fun visitPredicateComparison(ctx: GeneratedParser.PredicateComparisonContext) = translate(ctx) {
            convertToOperator(ctx.lhs, ctx.rhs, ctx.op)
        }

        /**
         * TODO Fix the IN collection grammar, also label alternative forms
         *  - https://github.com/partiql/partiql-lang-kotlin/issues/1115
         *   - https://github.com/partiql/partiql-lang-kotlin/issues/1113
         */
        override fun visitPredicateIn(ctx: GeneratedParser.PredicateInContext) = translate(ctx) {
            val lhs = visitAs<Expr>(ctx.lhs)
            val rhs = visitAs<Expr>(ctx.rhs ?: ctx.expr()).let {
                // Wrap rhs in an array unless it's a query or already a collection
                if (it is ExprQuerySet || it is ExprArray || it is ExprBag || ctx.PAREN_LEFT() == null) {
                    it
                } else {
                    // IN ( expr )
                    exprArray(listOf(it))
                }
            }
            val not = ctx.NOT() != null
            exprInCollection(lhs, rhs, not)
        }

        override fun visitPredicateIs(ctx: GeneratedParser.PredicateIsContext) = translate(ctx) {
            val value = visitAs<Expr>(ctx.lhs)
            val type = visitAs<DataType>(ctx.type())
            val not = ctx.NOT() != null
            exprIsType(value, type, not)
        }

        override fun visitPredicateBetween(ctx: GeneratedParser.PredicateBetweenContext) = translate(ctx) {
            val value = visitAs<Expr>(ctx.lhs)
            val lower = visitAs<Expr>(ctx.lower)
            val upper = visitAs<Expr>(ctx.upper)
            val not = ctx.NOT() != null
            exprBetween(value, lower, upper, not)
        }

        override fun visitPredicateLike(ctx: GeneratedParser.PredicateLikeContext) = translate(ctx) {
            val value = visitAs<Expr>(ctx.lhs)
            val pattern = visitAs<Expr>(ctx.rhs)
            val escape = visitOrNull<Expr>(ctx.escape)
            val not = ctx.NOT() != null
            exprLike(value, pattern, escape, not)
        }

        /**
         *
         * PRIMARY EXPRESSIONS
         *
         */

        override fun visitExprTermWrappedQuery(ctx: GeneratedParser.ExprTermWrappedQueryContext): AstNode =
            visit(ctx.expr())

        override fun visitVariableIdentifier(ctx: GeneratedParser.VariableIdentifierContext) = translate(ctx) {
            val symbol = ctx.ident.getStringValue()
            val isDelimited = when (ctx.ident.type) {
                GeneratedParser.IDENTIFIER -> false
                else -> true
            }
            val scope = when (ctx.qualifier) {
                null -> Scope.DEFAULT()
                else -> Scope.LOCAL()
            }
            exprVarRef(
                identifierChain(
                    root = identifier(symbol, isDelimited),
                    next = null
                ),
                scope
            )
        }

        override fun visitVariableKeyword(ctx: GeneratedParser.VariableKeywordContext) = translate(ctx) {
            val symbol = ctx.key.text
            val isDelimited = false
            val scope = when (ctx.qualifier) {
                null -> Scope.DEFAULT()
                else -> Scope.LOCAL()
            }
            exprVarRef(
                identifierChain(
                    root = identifier(symbol, isDelimited),
                    next = null
                ),
                scope
            )
        }

        override fun visitParameter(ctx: GeneratedParser.ParameterContext) = translate(ctx) {
            val index = parameters[ctx.QUESTION_MARK().symbol.tokenIndex] ?: throw error(
                ctx, "Unable to find index of parameter."
            )
            exprParameter(index)
        }

        override fun visitSequenceConstructor(ctx: GeneratedParser.SequenceConstructorContext) = translate(ctx) {
            error("Sequence constructor not supported")
        }

        private fun PathStep.copy(next: PathStep?) = when (this) {
            is PathStep.Element -> exprPathStepElement(this.element, next)
            is PathStep.Field -> exprPathStepField(this.field, next)
            is PathStep.AllElements -> exprPathStepAllElements(next)
            is PathStep.AllFields -> exprPathStepAllFields(next)
            else -> error("Unsupported PathStep: $this")
        }

        override fun visitExprPrimaryPath(ctx: GeneratedParser.ExprPrimaryPathContext) = translate(ctx) {
            val base = visitAs<Expr>(ctx.exprPrimary())
            val init: PathStep? = null
            val steps = ctx.pathStep().reversed().fold(init) { acc, step ->
                val stepExpr = visit(step) as PathStep
                stepExpr.copy(acc)
            }
            exprPath(base, steps)
        }

        override fun visitPathStepIndexExpr(ctx: GeneratedParser.PathStepIndexExprContext) = translate(ctx) {
            val key = visitAs<Expr>(ctx.key)
            exprPathStepElement(key, null)
        }

        override fun visitPathStepDotExpr(ctx: GeneratedParser.PathStepDotExprContext) = translate(ctx) {
            val symbol = visitSymbolPrimitive(ctx.symbolPrimitive())
            exprPathStepField(symbol, null)
        }

        override fun visitPathStepIndexAll(ctx: GeneratedParser.PathStepIndexAllContext) = translate(ctx) {
            exprPathStepAllElements(null)
        }

        override fun visitPathStepDotAll(ctx: GeneratedParser.PathStepDotAllContext) = translate(ctx) {
            exprPathStepAllFields(null)
        }

        override fun visitExprGraphMatchMany(ctx: GeneratedParser.ExprGraphMatchManyContext) = translate(ctx) {
            val graph = visit(ctx.exprPrimary()) as Expr
            val pattern = visitGpmlPatternList(ctx.gpmlPatternList())
            exprMatch(graph, pattern)
        }

        override fun visitExprGraphMatchOne(ctx: GeneratedParser.ExprGraphMatchOneContext) = translate(ctx) {
            val graph = visit(ctx.exprPrimary()) as Expr
            val pattern = visitGpmlPattern(ctx.gpmlPattern())
            exprMatch(graph, pattern)
        }

        override fun visitExprTermCurrentUser(ctx: GeneratedParser.ExprTermCurrentUserContext) = translate(ctx) {
            exprSessionAttribute(SessionAttribute.CURRENT_USER())
        }

        override fun visitExprTermCurrentDate(ctx: GeneratedParser.ExprTermCurrentDateContext) =
            translate(ctx) {
                exprSessionAttribute(SessionAttribute.CURRENT_DATE())
            }

        /**
         *
         * FUNCTIONS
         *
         */

        override fun visitNullIf(ctx: GeneratedParser.NullIfContext) = translate(ctx) {
            val value = visitExpr(ctx.expr(0))
            val nullifier = visitExpr(ctx.expr(1))
            exprNullIf(value, nullifier)
        }

        override fun visitCoalesce(ctx: GeneratedParser.CoalesceContext) = translate(ctx) {
            val expressions = visitOrEmpty<Expr>(ctx.expr())
            exprCoalesce(expressions)
        }

        override fun visitCaseExpr(ctx: GeneratedParser.CaseExprContext) = translate(ctx) {
            val expr = ctx.case_?.let { visitExpr(it) }
            val branches = ctx.whens.indices.map { i ->
                // consider adding locations
                val w = visitExpr(ctx.whens[i])
                val t = visitExpr(ctx.thens[i])
                exprCaseBranch(w, t)
            }
            val default = ctx.else_?.let { visitExpr(it) }
            exprCase(expr, branches, default)
        }

        override fun visitCast(ctx: GeneratedParser.CastContext) = translate(ctx) {
            val expr = visitExpr(ctx.expr())
            val type = visitAs<DataType>(ctx.type())
            exprCast(expr, type)
        }

        override fun visitCanCast(ctx: GeneratedParser.CanCastContext) = translate(ctx) {
            throw error(ctx, "CAN_CAST is no longer supported in the default PartiQLParser")
        }

        override fun visitCanLosslessCast(ctx: GeneratedParser.CanLosslessCastContext) = translate(ctx) {
            throw error(ctx, "CAN_LOSSLESS_CAST is no longer supported in the default PartiQLParser")
        }

        override fun visitFunctionCall(ctx: GeneratedParser.FunctionCallContext) = translate(ctx) {
            val args = visitOrEmpty<Expr>(ctx.expr())
            when (val funcName = ctx.qualifiedName()) {
                is GeneratedParser.QualifiedNameContext -> {
                    when (funcName.name.start.type) {
                        GeneratedParser.MOD -> exprOperator("%", args[0], args[1])
                        GeneratedParser.CHARACTER_LENGTH, GeneratedParser.CHAR_LENGTH -> {
                            val path = ctx.qualifiedName().qualifier.map { visitSymbolPrimitive(it) }
                            val name = identifierChain(identifier("char_length", false), null)
                            if (path.isEmpty()) {
                                exprCall(name, args, null) // setq = null for scalar fn
                            } else {
                                val function = path.reversed().fold(name) { acc, id ->
                                    identifierChain(root = id, next = acc)
                                }
                                exprCall(function, args, setq = null)
                            }
                        }
                        else -> visitNonReservedFunctionCall(ctx, args)
                    }
                }
                else -> visitNonReservedFunctionCall(ctx, args)
            }
        }
        private fun visitNonReservedFunctionCall(ctx: GeneratedParser.FunctionCallContext, args: List<Expr>): ExprCall {
            val function = visitQualifiedName(ctx.qualifiedName())
            return exprCall(function, args, convertSetQuantifier(ctx.setQuantifierStrategy()))
        }

        /**
         *
         * FUNCTIONS WITH SPECIAL FORMS
         *
         */

        override fun visitDateFunction(ctx: GeneratedParser.DateFunctionContext) = translate(ctx) {
            try {
                DatetimeField.valueOf(ctx.dt.text)
            } catch (ex: IllegalArgumentException) {
                throw error(ctx.dt, "Expected one of: ${DatetimeField.values().joinToString()}", ex)
            }
            val lhs = visitExpr(ctx.expr(0))
            val rhs = visitExpr(ctx.expr(1))
            // TODO change to not use PartiQLValue -- https://github.com/partiql/partiql-lang-kotlin/issues/1589
            val fieldLit = exprLit(stringValue(ctx.dt.text.uppercase()))
            when {
                ctx.DATE_ADD() != null -> exprCall(identifierChain(identifier("DATE_ADD", false), null), listOf(fieldLit, lhs, rhs), null)
                ctx.DATE_DIFF() != null -> exprCall(identifierChain(identifier("DATE_DIFF", false), null), listOf(fieldLit, lhs, rhs), null)
                else -> throw error(ctx, "Expected DATE_ADD or DATE_DIFF")
            }
        }

        /**
         * TODO Add labels to each alternative, https://github.com/partiql/partiql-lang-kotlin/issues/1113
         */
        override fun visitSubstring(ctx: GeneratedParser.SubstringContext) = translate(ctx) {
            if (ctx.FROM() == null) {
                // normal form
                val function = "SUBSTRING".toIdentifierChain()
                val args = visitOrEmpty<Expr>(ctx.expr())
                exprCall(function, args, setq = null) // setq = null for scalar fn
            } else {
                // special form
                val value = visitExpr(ctx.expr(0))
                val start = visitOrNull<Expr>(ctx.expr(1))
                val length = visitOrNull<Expr>(ctx.expr(2))
                exprSubstring(value, start, length)
            }
        }

        /**
         * TODO Add labels to each alternative, https://github.com/partiql/partiql-lang-kotlin/issues/1113
         */
        override fun visitPosition(ctx: GeneratedParser.PositionContext) = translate(ctx) {
            if (ctx.IN() == null) {
                // normal form
                val function = "POSITION".toIdentifierChain()
                val args = visitOrEmpty<Expr>(ctx.expr())
                exprCall(function, args, setq = null) // setq = null for scalar fn
            } else {
                // special form
                val lhs = visitExpr(ctx.expr(0))
                val rhs = visitExpr(ctx.expr(1))
                exprPosition(lhs, rhs)
            }
        }

        /**
         * TODO Add labels to each alternative, https://github.com/partiql/partiql-lang-kotlin/issues/1113
         */
        override fun visitOverlay(ctx: GeneratedParser.OverlayContext) = translate(ctx) {
            // TODO: figure out why do we have a normalized form for overlay?
            if (ctx.PLACING() == null) {
                // normal form
                val function = "OVERLAY".toIdentifierChain()
                val args = arrayOfNulls<Expr>(4).also {
                    visitOrEmpty<Expr>(ctx.expr()).forEachIndexed { index, expr ->
                        it[index] = expr
                    }
                }
                val e = error(ctx, "overlay function requires at least three args")

                exprOverlay(args[0] ?: throw e, args[1] ?: throw e, args[2] ?: throw e, args[3])
            } else {
                // special form
                val value = visitExpr(ctx.expr(0))
                val overlay = visitExpr(ctx.expr(1))
                val start = visitExpr(ctx.expr(2))
                val length = visitOrNull<Expr>(ctx.expr(3))
                exprOverlay(value, overlay, start, length)
            }
        }

        override fun visitExtract(ctx: GeneratedParser.ExtractContext) = translate(ctx) {
            val field = try {
                DatetimeField.valueOf(ctx.IDENTIFIER().text.uppercase())
            } catch (ex: IllegalArgumentException) {
                throw error(ctx.IDENTIFIER().symbol, "Expected one of: ${DatetimeField.values().joinToString()}", ex)
            }
            val source = visitExpr(ctx.expr())
            exprExtract(field, source)
        }

        override fun visitTrimFunction(ctx: GeneratedParser.TrimFunctionContext) = translate(ctx) {
            val spec = ctx.mod?.let {
                try {
                    TrimSpec.valueOf(it.text.uppercase())
                } catch (ex: IllegalArgumentException) {
                    throw error(it, "Expected on of: ${TrimSpec.values().joinToString()}", ex)
                }
            }
            val (chars, value) = when (ctx.expr().size) {
                1 -> null to visitExpr(ctx.expr(0))
                2 -> visitExpr(ctx.expr(0)) to visitExpr(ctx.expr(1))
                else -> throw error(ctx, "Expected one or two TRIM expression arguments")
            }
            exprTrim(value, chars, spec)
        }

        /**
         * Window Functions
         */

        override fun visitLagLeadFunction(ctx: GeneratedParser.LagLeadFunctionContext) = translate(ctx) {
            val function = when {
                ctx.LAG() != null -> WindowFunction.LAG()
                ctx.LEAD() != null -> WindowFunction.LEAD()
                else -> throw error(ctx, "Expected LAG or LEAD")
            }
            val expression = visitExpr(ctx.expr(0))
            val offset = visitOrNull<Expr>(ctx.expr(1))
            val default = visitOrNull<Expr>(ctx.expr(2))
            val over = visitOver(ctx.over())
            if (over.sorts == null) {
                throw error(ctx.over(), "$function requires Window ORDER BY")
            }
            exprWindow(function, expression, offset, default, over)
        }

        override fun visitOver(ctx: GeneratedParser.OverContext) = translate(ctx) {
            val partitions = ctx.windowPartitionList()?.let { visitOrEmpty<Expr>(it.expr()) }
            val sorts = ctx.windowSortSpecList()?.let { visitOrEmpty<Sort>(it.orderSortSpec()) }
            exprWindowOver(partitions, sorts)
        }

        /**
         *
         * LITERALS
         *
         */

        override fun visitBag(ctx: GeneratedParser.BagContext) = translate(ctx) {
            // Prohibit hidden characters between angle brackets
            val startTokenIndex = ctx.start.tokenIndex
            val endTokenIndex = ctx.stop.tokenIndex
            if (tokens.getHiddenTokensToRight(startTokenIndex, GeneratedLexer.HIDDEN) != null || tokens.getHiddenTokensToLeft(endTokenIndex, GeneratedLexer.HIDDEN) != null) {
                throw error(ctx, "Invalid bag expression")
            }
            val expressions = visitOrEmpty<Expr>(ctx.expr())
            exprBag(expressions)
        }

        override fun visitLiteralDecimal(ctx: GeneratedParser.LiteralDecimalContext) = translate(ctx) {
            val decimal = try {
                val v = ctx.LITERAL_DECIMAL().text.trim()
                BigDecimal(v, MathContext(38, RoundingMode.HALF_EVEN))
            } catch (e: NumberFormatException) {
                throw error(ctx, "Invalid decimal literal", e)
            }
            exprLit(decimalValue(decimal))
        }

        override fun visitArray(ctx: GeneratedParser.ArrayContext) = translate(ctx) {
            val expressions = visitOrEmpty<Expr>(ctx.expr())
            exprArray(expressions)
        }

        override fun visitLiteralNull(ctx: GeneratedParser.LiteralNullContext) = translate(ctx) {
            exprLit(nullValue())
        }

        override fun visitLiteralMissing(ctx: GeneratedParser.LiteralMissingContext) = translate(ctx) {
            exprLit(missingValue())
        }

        override fun visitLiteralTrue(ctx: GeneratedParser.LiteralTrueContext) = translate(ctx) {
            exprLit(boolValue(true))
        }

        override fun visitLiteralFalse(ctx: GeneratedParser.LiteralFalseContext) = translate(ctx) {
            exprLit(boolValue(false))
        }

        override fun visitLiteralIon(ctx: GeneratedParser.LiteralIonContext) = translate(ctx) {
            val value = ctx.ION_CLOSURE().getStringValue()
            val encoding = "ion"
            exprVariant(value, encoding)
        }

        override fun visitLiteralString(ctx: GeneratedParser.LiteralStringContext) = translate(ctx) {
            val value = ctx.LITERAL_STRING().getStringValue()
            exprLit(stringValue(value))
        }

        override fun visitLiteralInteger(ctx: GeneratedParser.LiteralIntegerContext) = translate(ctx) {
            val n = ctx.LITERAL_INTEGER().text

            // 1st, try parse as int
            try {
                val v = n.toInt(10)
                return@translate exprLit(int32Value(v))
            } catch (ex: NumberFormatException) {
                // ignore
            }

            // 2nd, try parse as long
            try {
                val v = n.toLong(10)
                return@translate exprLit(int64Value(v))
            } catch (ex: NumberFormatException) {
                // ignore
            }

            // 3rd, try parse as BigInteger
            try {
                val v = BigInteger(n)
                return@translate exprLit(intValue(v))
            } catch (ex: NumberFormatException) {
                throw ex
            }
        }

        override fun visitLiteralDate(ctx: GeneratedParser.LiteralDateContext) = translate(ctx) {
            val pattern = ctx.LITERAL_STRING().symbol
            val dateString = ctx.LITERAL_STRING().getStringValue()
            if (DATE_PATTERN_REGEX.matches(dateString).not()) {
                throw error(pattern, "Expected DATE string to be of the format yyyy-MM-dd")
            }
            val value = try {
                LocalDate.parse(dateString, DateTimeFormatter.ISO_LOCAL_DATE)
            } catch (e: DateTimeParseException) {
                throw error(pattern, e.localizedMessage, e)
            } catch (e: IndexOutOfBoundsException) {
                throw error(pattern, e.localizedMessage, e)
            }
            val date = DateTimeValue.date(value.year, value.monthValue, value.dayOfMonth)
            exprLit(dateValue(date))
        }

        override fun visitLiteralTime(ctx: GeneratedParser.LiteralTimeContext) = translate(ctx) {
            val (timeString, precision) = getTimeStringAndPrecision(ctx.LITERAL_STRING(), ctx.LITERAL_INTEGER())
            val time = try {
                DateTimeUtils.parseTimeLiteral(timeString)
            } catch (e: DateTimeException) {
                throw error(ctx, "Invalid Date Time Literal", e)
            }
            val value = time.toPrecision(precision)
            exprLit(timeValue(value))
        }

        override fun visitLiteralTimestamp(ctx: GeneratedParser.LiteralTimestampContext) = translate(ctx) {
            val (timeString, precision) = getTimeStringAndPrecision(ctx.LITERAL_STRING(), ctx.LITERAL_INTEGER())
            val timestamp = try {
                DateTimeUtils.parseTimestamp(timeString)
            } catch (e: DateTimeException) {
                throw error(ctx, "Invalid Date Time Literal", e)
            }
            val value = timestamp.toPrecision(precision)
            exprLit(timestampValue(value))
        }

        override fun visitTuple(ctx: GeneratedParser.TupleContext) = translate(ctx) {
            val fields = ctx.pair().map {
                val k = visitExpr(it.lhs)
                val v = visitExpr(it.rhs)
                exprStructField(k, v)
            }
            exprStruct(fields)
        }

        /**
         *
         * TYPES
         *
         */

        override fun visitTypeAtomic(ctx: GeneratedParser.TypeAtomicContext) = translate(ctx) {
            when (ctx.datatype.type) {
                GeneratedParser.NULL -> DataType.NULL()
                GeneratedParser.BOOL -> DataType.BOOLEAN()
                GeneratedParser.BOOLEAN -> DataType.BOOL()
                GeneratedParser.SMALLINT -> DataType.SMALLINT()
                GeneratedParser.INT2 -> DataType.INT2()
                GeneratedParser.INTEGER2 -> DataType.INTEGER2()
                // TODO, we have INT aliased to INT4 when it should be visa-versa.
                GeneratedParser.INT4 -> DataType.INT4()
                GeneratedParser.INTEGER4 -> DataType.INTEGER4()
                GeneratedParser.INT -> DataType.INT()
                GeneratedParser.INTEGER -> DataType.INTEGER()
                GeneratedParser.BIGINT -> DataType.BIGINT()
                GeneratedParser.INT8 -> DataType.INT8()
                GeneratedParser.INTEGER8 -> DataType.INTEGER8()
                GeneratedParser.FLOAT -> DataType.FLOAT()
                GeneratedParser.DOUBLE -> TODO() // not sure if DOUBLE is to be supported
                GeneratedParser.REAL -> DataType.REAL()
                GeneratedParser.TIMESTAMP -> DataType.TIMESTAMP()
                GeneratedParser.CHAR -> DataType.CHAR()
                GeneratedParser.CHARACTER -> DataType.CHARACTER()
                GeneratedParser.MISSING -> DataType.MISSING()
                GeneratedParser.STRING -> DataType.STRING()
                GeneratedParser.SYMBOL -> DataType.SYMBOL()
                // TODO https://github.com/partiql/partiql-lang-kotlin/issues/1125
                GeneratedParser.BLOB -> DataType.BLOB()
                GeneratedParser.CLOB -> DataType.CLOB()
                GeneratedParser.DATE -> DataType.DATE()
                GeneratedParser.STRUCT -> DataType.STRUCT()
                GeneratedParser.TUPLE -> DataType.TUPLE()
                GeneratedParser.LIST -> DataType.LIST()
                GeneratedParser.SEXP -> DataType.SEXP()
                GeneratedParser.BAG -> DataType.BAG()
                GeneratedParser.ANY -> TODO() // not sure if ANY is to be supported
                else -> throw error(ctx, "Unknown atomic type.") // TODO other types included in parser
            }
        }

        override fun visitTypeVarChar(ctx: GeneratedParser.TypeVarCharContext): DataType = translate(ctx) {
            when (val n = ctx.arg0?.text?.toInt()) {
                null -> DataType.VARCHAR()
                else -> DataType.VARCHAR(n)
            }
        }

        override fun visitTypeArgSingle(ctx: GeneratedParser.TypeArgSingleContext) = translate(ctx) {
            val n = ctx.arg0?.text?.toInt()
            when (ctx.datatype.type) {
                GeneratedParser.FLOAT -> when (n) {
                    null -> DataType.FLOAT(64)
                    32 -> DataType.FLOAT(32)
                    64 -> DataType.FLOAT(64)
                    else -> throw error(ctx.datatype, "Invalid FLOAT precision. Expected 32 or 64")
                }
                GeneratedParser.CHAR, GeneratedParser.CHARACTER -> when (n) {
                    null -> DataType.CHAR()
                    else -> DataType.CHAR(n)
                }
                GeneratedParser.VARCHAR -> when (n) {
                    null -> DataType.VARCHAR()
                    else -> DataType.VARCHAR(n)
                }
                else -> throw error(ctx.datatype, "Invalid datatype")
            }
        }

        override fun visitTypeArgDouble(ctx: GeneratedParser.TypeArgDoubleContext) = translate(ctx) {
            val arg0 = ctx.arg0?.text?.toInt()
            val arg1 = ctx.arg1?.text?.toInt()
            when (ctx.datatype.type) {
                GeneratedParser.DECIMAL -> when {
                    arg0 == null && arg1 == null -> DataType.DECIMAL()
                    arg0 != null && arg1 == null -> DataType.DECIMAL(arg0)
                    arg0 != null && arg1 != null -> DataType.DECIMAL(arg0, arg1)
                    else -> error("Invalid parameters for decimal")
                }
                GeneratedParser.DEC -> when {
                    arg0 == null && arg1 == null -> DataType.DEC()
                    arg0 != null && arg1 == null -> DataType.DEC(arg0)
                    arg0 != null && arg1 != null -> DataType.DEC(arg0, arg1)
                    else -> error("Invalid parameters for dec")
                }
                GeneratedParser.NUMERIC -> when {
                    arg0 == null && arg1 == null -> DataType.NUMERIC()
                    arg0 != null && arg1 == null -> DataType.NUMERIC(arg0)
                    arg0 != null && arg1 != null -> DataType.NUMERIC(arg0, arg1)
                    else -> error("Invalid parameters for decimal")
                }
                else -> throw error(ctx.datatype, "Invalid datatype")
            }
        }

        override fun visitTypeTimeZone(ctx: GeneratedParser.TypeTimeZoneContext) = translate(ctx) {
            val precision = ctx.precision?.let {
                val p = ctx.precision.text.toInt()
                if (p < 0 || 9 < p) throw error(ctx.precision, "Unsupported time precision")
                p
            }

            when (ctx.datatype.type) {
                GeneratedParser.TIME -> when (ctx.ZONE()) {
                    null -> when (precision) {
                        null -> DataType.TIME()
                        else -> DataType.TIME(precision)
                    }
                    else -> when (precision) {
                        null -> DataType.TIME_WITH_TIME_ZONE()
                        else -> DataType.TIME_WITH_TIME_ZONE(precision)
                    }
                }
                GeneratedParser.TIMESTAMP -> when (ctx.ZONE()) {
                    null -> when (precision) {
                        null -> DataType.TIMESTAMP()
                        else -> DataType.TIMESTAMP(precision)
                    }
                    else -> when (precision) {
                        null -> DataType.TIMESTAMP_WITH_TIME_ZONE()
                        else -> DataType.TIMESTAMP_WITH_TIME_ZONE(precision)
                    }
                }
                else -> throw error(ctx.datatype, "Invalid datatype")
            }
        }

        override fun visitTypeCustom(ctx: GeneratedParser.TypeCustomContext) = translate(ctx) {
            DataType.USER_DEFINED(ctx.text.uppercase().toIdentifierChain())
        }

        private inline fun <reified T : AstNode> visitOrEmpty(ctx: List<ParserRuleContext>?): List<T> = when {
            ctx.isNullOrEmpty() -> emptyList()
            else -> ctx.map { visit(it) as T }
        }

        private inline fun <reified T : AstNode> visitOrNull(ctx: ParserRuleContext?): T? =
            ctx?.let { it.accept(this) as T }

        private inline fun <reified T : AstNode> visitAs(ctx: ParserRuleContext): T = visit(ctx) as T

        /**
         * Visiting a symbol to get a string, skip the wrapping, unwrapping, and location tracking.
         */
        private fun symbolToString(ctx: GeneratedParser.SymbolPrimitiveContext) = when (ctx) {
            is GeneratedParser.IdentifierQuotedContext -> ctx.IDENTIFIER_QUOTED().getStringValue()
            is GeneratedParser.IdentifierUnquotedContext -> ctx.text
            else -> throw error(ctx, "Invalid symbol reference.")
        }

        /**
         * Convert [ALL|DISTINCT] to SetQuantifier Enum
         */
        private fun convertSetQuantifier(ctx: GeneratedParser.SetQuantifierStrategyContext?): SetQuantifier? = when {
            ctx == null -> null
            ctx.ALL() != null -> SetQuantifier.ALL()
            ctx.DISTINCT() != null -> SetQuantifier.DISTINCT()
            else -> throw error(ctx, "Expected set quantifier ALL or DISTINCT")
        }

        /**
         * With the <string> and <int> nodes of a literal time expression, returns the parsed string and precision.
         * TIME (<int>)? (WITH TIME ZONE)? <string>
         */
        private fun getTimeStringAndPrecision(
            stringNode: TerminalNode,
            integerNode: TerminalNode?,
        ): Pair<String, Int> {
            val timeString = stringNode.getStringValue()
            val precision = when (integerNode) {
                null -> {
                    try {
                        getPrecisionFromTimeString(timeString)
                    } catch (e: Exception) {
                        throw error(stringNode.symbol, "Unable to parse precision.", e)
                    }
                }
                else -> {
                    val p = integerNode.text.toBigInteger().toInt()
                    if (p < 0 || 9 < p) throw error(integerNode.symbol, "Precision out of bounds")
                    p
                }
            }
            return timeString to precision
        }

        private fun getPrecisionFromTimeString(timeString: String): Int {
            val matcher = GENERIC_TIME_REGEX.toPattern().matcher(timeString)
            if (!matcher.find()) {
                throw IllegalArgumentException("Time string does not match the format 'HH:MM:SS[.ddd....][+|-HH:MM]'")
            }
            val fraction = matcher.group(1)?.removePrefix(".")
            return fraction?.length ?: 0
        }

        /**
         * Converts a Path expression into a Projection Item (either ALL or EXPR). Note: A Projection Item only allows a
         * subset of a typical Path expressions. See the following examples.
         *
         * Examples of valid projections are:
         *
         * ```partiql
         *      SELECT * FROM foo
         *      SELECT foo.* FROM foo
         *      SELECT f.* FROM foo as f
         *      SELECT foo.bar.* FROM foo
         *      SELECT f.bar.* FROM foo as f
         * ```
         * Also validates that the expression is valid for select list context. It does this by making
         * sure that expressions looking like the following do not appear:
         *
         * ```partiql
         *      SELECT foo[*] FROM foo
         *      SELECT f.*.bar FROM foo as f
         *      SELECT foo[1].* FROM foo
         *      SELECT foo.*.bar FROM foo
         * ```
         */
        protected fun convertPathToProjectionItem(ctx: ParserRuleContext, path: ExprPath, alias: Identifier?) =
            translate(ctx) {
                val steps = mutableListOf<PathStep>()
                var containsIndex = false
                var curStep = path.next
                var last = curStep
                while (curStep != null) {
                    val isLastStep = curStep.next == null
                    // Only last step can have a '.*'
                    if (curStep is PathStep.AllFields && !isLastStep) {
                        throw error(ctx, "Projection item cannot unpivot unless at end.")
                    }
                    // No step can have an indexed wildcard: '[*]'
                    if (curStep is PathStep.AllElements) {
                        throw error(ctx, "Projection item cannot index using wildcard.")
                    }
                    // TODO If the last step is '.*', no indexing is allowed
                    // if (step.metas.containsKey(IsPathIndexMeta.TAG)) {
                    //     containsIndex = true
                    // }
                    if (curStep !is PathStep.AllFields) {
                        steps.add(curStep)
                    }

                    if (isLastStep && curStep is PathStep.AllFields && containsIndex) {
                        throw error(ctx, "Projection item use wildcard with any indexing.")
                    }
                    last = curStep
                    curStep = curStep.next
                }
                when {
                    last is PathStep.AllFields && steps.isEmpty() -> {
                        selectItemStar(path.root)
                    }
                    last is PathStep.AllFields -> {
                        val init: PathStep? = null
                        val newSteps = steps.reversed().fold(init) { acc, step ->
                            when (step) {
                                is PathStep.Element -> PathStep.Element(step.element, acc)
                                is PathStep.Field -> PathStep.Field(step.field, acc)
                                is PathStep.AllElements -> PathStep.AllElements(acc)
                                is PathStep.AllFields -> PathStep.AllFields(acc)
                                else -> error("Unexpected path step")
                            }
                        }
                        selectItemStar(exprPath(path.root, newSteps))
                    }
                    else -> {
                        selectItemExpr(path, alias)
                    }
                }
            }

        private fun TerminalNode.getStringValue(): String = this.symbol.getStringValue()

        private fun Token.getStringValue(): String = when (this.type) {
            GeneratedParser.IDENTIFIER -> this.text
            GeneratedParser.IDENTIFIER_QUOTED -> this.text.removePrefix("\"").removeSuffix("\"").replace("\"\"", "\"")
            GeneratedParser.LITERAL_STRING -> this.text.removePrefix("'").removeSuffix("'").replace("''", "'")
            GeneratedParser.ION_CLOSURE -> this.text.removePrefix("`").removeSuffix("`")
            else -> throw error(this, "Unsupported token for grabbing string value.")
        }

        private fun String.toIdentifier(): Identifier = identifier(this, false)

        private fun String.toIdentifierChain(): IdentifierChain = identifierChain(root = this.toIdentifier(), next = null)

        private fun String.toBigInteger() = BigInteger(this, 10)

        private fun assertIntegerElement(token: Token?, value: IonElement?) {
            if (value == null || token == null) return
            if (value !is IntElement) throw error(token, "Expected an integer value.")
            if (value.integerSize == IntElementSize.BIG_INTEGER || value.longValue > Int.MAX_VALUE || value.longValue < Int.MIN_VALUE) throw error(
                token, "Type parameter exceeded maximum value"
            )
        }

        private enum class ExplainParameters {
            TYPE, FORMAT;

            fun getCompliantString(target: String?, input: Token): String = when (target) {
                null -> input.text!!
                else -> throw error(input, "Cannot set EXPLAIN parameter ${this.name} multiple times.")
            }
        }
    }
}