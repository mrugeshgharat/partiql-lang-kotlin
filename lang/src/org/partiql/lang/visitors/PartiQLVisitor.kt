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

package org.partiql.lang.visitors

import com.amazon.ion.IonSystem
import com.amazon.ionelement.api.MetaContainer
import com.amazon.ionelement.api.StringElement
import com.amazon.ionelement.api.SymbolElement
import com.amazon.ionelement.api.emptyMetaContainer
import com.amazon.ionelement.api.ionBool
import com.amazon.ionelement.api.ionInt
import com.amazon.ionelement.api.ionNull
import com.amazon.ionelement.api.ionString
import com.amazon.ionelement.api.ionSymbol
import com.amazon.ionelement.api.toIonElement
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.tree.ErrorNode
import org.antlr.v4.runtime.tree.RuleNode
import org.antlr.v4.runtime.tree.TerminalNode
import org.partiql.lang.ast.IsCountStarMeta
import org.partiql.lang.ast.IsImplictJoinMeta
import org.partiql.lang.ast.IsPathIndexMeta
import org.partiql.lang.ast.LegacyLogicalNotMeta
import org.partiql.lang.ast.SourceLocationMeta
import org.partiql.lang.domains.PartiqlAst
import org.partiql.lang.domains.metaContainerOf
import org.partiql.lang.eval.EvaluationException
import org.partiql.lang.eval.time.MAX_PRECISION_FOR_TIME
import org.partiql.lang.generated.PartiQLBaseVisitor
import org.partiql.lang.generated.PartiQLParser
import org.partiql.lang.syntax.DATE_TIME_PART_KEYWORDS
import org.partiql.lang.syntax.PartiQLParser.ParseErrorListener.ParseException
import org.partiql.lang.types.CustomType
import org.partiql.lang.util.bigDecimalOf
import org.partiql.lang.util.getPrecisionFromTimeString
import org.partiql.pig.runtime.SymbolPrimitive
import org.partiql.pig.runtime.asPrimitive
import java.math.BigInteger
import java.time.LocalTime
import java.time.OffsetTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlin.reflect.KClass
import kotlin.reflect.cast

/**
 * Extends ANTLR's generated [PartiQLBaseVisitor] to visit an ANTLR ParseTree and convert it into a PartiQL AST. This
 * class uses the [PartiqlAst.PartiqlAstNode] to represent all nodes within the new AST.
 */
class PartiQLVisitor(val ion: IonSystem, val customTypes: List<CustomType> = listOf(), val parameterIndexes: Map<Int, Int> = mapOf()) :
    PartiQLBaseVisitor<PartiqlAst.PartiqlAstNode>() {

    private val CUSTOM_KEYWORDS = customTypes.map { it.name.toLowerCase() }

    private val CUSTOM_TYPE_ALIASES =
        customTypes.map { customType ->
            customType.aliases.map { alias ->
                Pair(alias.toLowerCase(), customType.name.toLowerCase())
            }
        }.flatten().toMap()

    /**
     *
     * DATA DEFINITION LANGUAGE (DDL)
     *
     */

    override fun visitQueryDdl(ctx: PartiQLParser.QueryDdlContext) = PartiqlAst.build {
        val op = visitDdl(ctx.ddl()) as PartiqlAst.DdlOp
        ddl(op)
    }

    override fun visitDropTable(ctx: PartiQLParser.DropTableContext) = PartiqlAst.build {
        val id = visitSymbolPrimitive(ctx.symbolPrimitive())
        dropTable(id.toIdentifier())
    }

    override fun visitDropIndex(ctx: PartiQLParser.DropIndexContext) = PartiqlAst.build {
        val id = visitSymbolPrimitive(ctx.target)
        val key = visitSymbolPrimitive(ctx.on)
        dropIndex(key.toIdentifier(), id.toIdentifier())
    }

    override fun visitCreateTable(ctx: PartiQLParser.CreateTableContext) = PartiqlAst.build {
        val name = ctx.symbolPrimitive().getString()
        createTable(name)
    }

    override fun visitCreateIndex(ctx: PartiQLParser.CreateIndexContext) = PartiqlAst.build {
        val id = visitSymbolPrimitive(ctx.symbolPrimitive())
        val fields = ctx.pathSimple().map { path -> visitPathSimple(path) }
        createIndex(id.toIdentifier(), fields)
    }

    /**
     *
     * EXECUTE
     *
     */

    override fun visitQueryExec(ctx: PartiQLParser.QueryExecContext) = visitExecCommand(ctx.execCommand())

    override fun visitExecCommand(ctx: PartiQLParser.ExecCommandContext) = PartiqlAst.build {
        val name = visitExpr(ctx.expr()).getStringValue()
        val args = ctx.querySet().map { expr -> visit(expr) as PartiqlAst.Expr }
        exec(name, args)
    }

    /**
     *
     * DATA MANIPULATION LANGUAGE (DML)
     *
     */

    // @TODO: NOTE.. Need to update SET to only return the operation.

    override fun visitDmlUpdateWhereReturn(ctx: PartiQLParser.DmlUpdateWhereReturnContext) = PartiqlAst.build {
        val from = if (ctx.updateClause() != null) visitUpdateClause(ctx.updateClause()) else null
        val where = if (ctx.whereClause() != null) visitWhereClause(ctx.whereClause()) else null
        val returning = if (ctx.returningClause() != null) visitReturningClause(ctx.returningClause()) else null
        val operations = ctx.dmlBaseCommand().map { command -> getCommandList(visit(command)) }.flatten()
        dml(dmlOpList(operations), from, where, returning)
    }

    override fun visitDmlBase(ctx: PartiQLParser.DmlBaseContext) = PartiqlAst.build {
        val commands = getCommandList(visit(ctx.dmlBaseCommand()))
        dml(dmlOpList(commands))
    }

    private fun getCommandList(command: PartiqlAst.PartiqlAstNode): List<PartiqlAst.DmlOp> {
        return when (command) {
            is PartiqlAst.DmlOpList -> command.ops
            is PartiqlAst.DmlOp -> listOf(command)
            else -> throw ParseException("Unable to grab DML operation.")
        }
    }

    override fun visitDmlFromWhereReturn(ctx: PartiQLParser.DmlFromWhereReturnContext) = PartiqlAst.build {
        val from = if (ctx.fromClause() != null) visitFromClause(ctx.fromClause()) else null
        val where = if (ctx.whereClause() != null) visitWhereClause(ctx.whereClause()) else null
        val returning = if (ctx.returningClause() != null) visitReturningClause(ctx.returningClause()) else null
        val operations = ctx.dmlBaseCommand().map { command -> getCommandList(visit(command)) }.flatten()
        dml(dmlOpList(operations), from, where, returning)
    }

    override fun visitRemoveCommand(ctx: PartiQLParser.RemoveCommandContext) = PartiqlAst.build {
        val target = visitPathSimple(ctx.pathSimple())
        remove(target)
    }

    override fun visitDeleteCommand(ctx: PartiQLParser.DeleteCommandContext) = PartiqlAst.build {
        val from = visit(ctx.fromClauseSimple()) as PartiqlAst.FromSource
        val where = if (ctx.whereClause() != null) visitWhereClause(ctx.whereClause()) else null
        val returning = if (ctx.returningClause() != null) visitReturningClause(ctx.returningClause()) else null
        dml(dmlOpList(delete()), from, where, returning)
    }

    override fun visitInsertSimple(ctx: PartiQLParser.InsertSimpleContext): PartiqlAst.PartiqlAstNode {
        val target = visitPathSimple(ctx.pathSimple())
        return PartiqlAst.build { insert(target, visit(ctx.value) as PartiqlAst.Expr) }
    }

    override fun visitInsertValue(ctx: PartiQLParser.InsertValueContext): PartiqlAst.PartiqlAstNode {
        val target = visitPathSimple(ctx.pathSimple())
        val index = if (ctx.pos != null) visitExpr(ctx.pos) else null
        val onConflict = if (ctx.onConflict() != null) visitOnConflict(ctx.onConflict()) else null
        return PartiqlAst.build {
            insertValue(target, visit(ctx.value) as PartiqlAst.Expr, index = index, onConflict = onConflict)
        }
    }

    // FIXME: See `FIXME #001` in file `PartiQL.g4`.
    override fun visitInsertCommandReturning(ctx: PartiQLParser.InsertCommandReturningContext) = PartiqlAst.build {
        val target = visitPathSimple(ctx.pathSimple())
        val index = if (ctx.pos != null) visitExpr(ctx.pos) else null
        val onConflict = if (ctx.onConflict() != null) visitOnConflict(ctx.onConflict()) else null
        val returning = if (ctx.returningClause() != null) visitReturningClause(ctx.returningClause()) else null
        val insert = insertValue(target, visit(ctx.value) as PartiqlAst.Expr, index = index, onConflict = onConflict)
        dml(dmlOpList(insert), returning = returning)
    }

    override fun visitReturningClause(ctx: PartiQLParser.ReturningClauseContext) = PartiqlAst.build {
        val elements = ctx.returningColumn().map { col -> visitReturningColumn(col) }
        returningExpr(elements)
    }

    private fun getReturningMapping(status: Token, age: Token) = PartiqlAst.build {
        when {
            status.type == PartiQLParser.MODIFIED && age.type == PartiQLParser.NEW -> modifiedNew()
            status.type == PartiQLParser.MODIFIED && age.type == PartiQLParser.OLD -> modifiedOld()
            status.type == PartiQLParser.ALL && age.type == PartiQLParser.NEW -> allNew()
            status.type == PartiQLParser.ALL && age.type == PartiQLParser.OLD -> allOld()
            else -> throw ParseException("Unable to get return mapping.")
        }
    }

    override fun visitReturningColumn(ctx: PartiQLParser.ReturningColumnContext) = PartiqlAst.build {
        val column = if (ctx.ASTERISK() != null) returningWildcard() else returningColumn(visitExpr(ctx.expr()))
        returningElem(getReturningMapping(ctx.status, ctx.age), column)
    }

    override fun visitOnConflict(ctx: PartiQLParser.OnConflictContext) = PartiqlAst.build {
        onConflict(visitExpr(ctx.expr()), doNothing())
    }

    override fun visitPathSimple(ctx: PartiQLParser.PathSimpleContext): PartiqlAst.Expr {
        val root = visitSymbolPrimitive(ctx.symbolPrimitive())
        if (ctx.pathSimpleSteps().isEmpty()) return root
        val steps = ctx.pathSimpleSteps().map { step -> visit(step) as PartiqlAst.PathStep }
        return PartiqlAst.build { path(root, steps) }
    }

    override fun visitPathSimpleLiteral(ctx: PartiQLParser.PathSimpleLiteralContext) = PartiqlAst.build {
        pathExpr(visit(ctx.literal()) as PartiqlAst.Expr, caseSensitive())
    }

    override fun visitPathSimpleSymbol(ctx: PartiQLParser.PathSimpleSymbolContext) = PartiqlAst.build {
        pathExpr(visitSymbolPrimitive(ctx.symbolPrimitive()), caseSensitive())
    }

    override fun visitPathSimpleDotSymbol(ctx: PartiQLParser.PathSimpleDotSymbolContext) = getSymbolPathExpr(ctx.symbolPrimitive())

    override fun visitSetCommand(ctx: PartiQLParser.SetCommandContext): PartiqlAst.PartiqlAstNode {
        val assignments = ctx.setAssignment().map { assignment -> visitSetAssignment(assignment) }
        return PartiqlAst.build {
            dmlOpList(assignments)
        }
    }

    override fun visitSetAssignment(ctx: PartiQLParser.SetAssignmentContext): PartiqlAst.DmlOp.Set {
        return PartiqlAst.build {
            set(assignment(visitPathSimple(ctx.pathSimple()), visitExpr(ctx.expr())))
        }
    }

    override fun visitUpdateClause(ctx: PartiQLParser.UpdateClauseContext): PartiqlAst.FromSource {
        return visit(ctx.tableBaseReference()) as PartiqlAst.FromSource
    }

    /**
     *
     * DATA QUERY LANGUAGE (DQL)
     *
     */

    override fun visitSfwQuery(ctx: PartiQLParser.SfwQueryContext): PartiqlAst.Expr.Select {
        val projection = visit(ctx.selectClause()) as PartiqlAst.Projection
        val strategy = getSetQuantifierStrategy(ctx.selectClause())
        val from = visitFromClause(ctx.fromClause())
        val order = if (ctx.orderByClause() != null) visit(ctx.orderByClause()) as PartiqlAst.OrderBy else null
        val group = if (ctx.groupClause() != null) visit(ctx.groupClause()) as PartiqlAst.GroupBy else null
        val limit = if (ctx.limitClause() != null) visit(ctx.limitClause()) as PartiqlAst.Expr else null
        val offset = if (ctx.offsetByClause() != null) visit(ctx.offsetByClause()) as PartiqlAst.Expr else null
        val where = if (ctx.whereClause() != null) visit(ctx.whereClause()) as PartiqlAst.Expr else null
        val having = if (ctx.havingClause() != null) visit(ctx.havingClause()) as PartiqlAst.Expr else null
        val let = if (ctx.letClause() != null) visit(ctx.letClause()) as PartiqlAst.Let else null
        val metas = ctx.selectClause().getMetas()
        return PartiqlAst.BUILDER().select(
            project = projection,
            from = from,
            setq = strategy,
            order = order,
            group = group,
            limit = limit,
            offset = offset,
            where = where,
            having = having,
            fromLet = let,
            metas = metas
        )
    }

    private fun PartiQLParser.SelectClauseContext.getMetas(): MetaContainer = when (this) {
        is PartiQLParser.SelectAllContext -> this.SELECT().getSourceMetaContainer()
        is PartiQLParser.SelectItemsContext -> this.SELECT().getSourceMetaContainer()
        is PartiQLParser.SelectValueContext -> this.SELECT().getSourceMetaContainer()
        is PartiQLParser.SelectPivotContext -> this.PIVOT().getSourceMetaContainer()
        else -> throw ParseException("Unknown meta location.")
    }

    private fun TerminalNode?.getSourceMetaContainer(): MetaContainer {
        if (this == null) return emptyMetaContainer()
        val metas = this.getSourceMetas()
        return com.amazon.ionelement.api.metaContainerOf(Pair(metas.tag, metas))
    }

    private fun Token?.getSourceMetaContainer(): MetaContainer {
        if (this == null) return emptyMetaContainer()
        val metas = this.getSourceMetas()
        return com.amazon.ionelement.api.metaContainerOf(Pair(metas.tag, metas))
    }

    private fun TerminalNode.getSourceMetas(): SourceLocationMeta = this.symbol.getSourceMetas()

    private fun Token.getSourceMetas(): SourceLocationMeta {
        val length = this.stopIndex - this.startIndex + 1
        return SourceLocationMeta(this.line.toLong(), this.charPositionInLine.toLong() + 1, length.toLong())
    }

    override fun visitSelectAll(ctx: PartiQLParser.SelectAllContext) = PartiqlAst.build { projectStar() }
    override fun visitSelectItems(ctx: PartiQLParser.SelectItemsContext) = visitProjectionItems(ctx.projectionItems())

    override fun visitSelectPivot(ctx: PartiQLParser.SelectPivotContext) = PartiqlAst.build {
        projectPivot(visitExpr(ctx.at), visitExpr(ctx.pivot))
    }

    override fun visitSelectValue(ctx: PartiQLParser.SelectValueContext) = PartiqlAst.build {
        projectValue(visitExpr(ctx.expr()))
    }

    override fun visitProjectionItems(ctx: PartiQLParser.ProjectionItemsContext): PartiqlAst.Projection.ProjectList {
        val projections = ctx.projectionItem().map { projection -> visit(projection) as PartiqlAst.ProjectItem }
        return PartiqlAst.BUILDER().projectList(projections)
    }

    override fun visitProjectionItem(ctx: PartiQLParser.ProjectionItemContext): PartiqlAst.ProjectItem {
        val expr = visit(ctx.expr()) as PartiqlAst.Expr
        val alias = if (ctx.symbolPrimitive() != null) ctx.symbolPrimitive().getString() else null
        return if (expr is PartiqlAst.Expr.Path) convertPathToProjectionItem(expr, alias)
        else PartiqlAst.build { projectExpr(expr, asAlias = alias) }
    }

    override fun visitTuple(ctx: PartiQLParser.TupleContext): PartiqlAst.PartiqlAstNode {
        val pairs = ctx.pair().map { pair -> visitPair(pair) }
        return PartiqlAst.build { struct(pairs) }
    }

    override fun visitPair(ctx: PartiQLParser.PairContext): PartiqlAst.ExprPair {
        val lhs = visitExpr(ctx.lhs)
        val rhs = visitExpr(ctx.rhs)
        return PartiqlAst.build { exprPair(lhs, rhs) }
    }

    override fun visitLimitClause(ctx: PartiQLParser.LimitClauseContext): PartiqlAst.Expr =
        visitExpr(ctx.expr())

    override fun visitExpr(ctx: PartiQLParser.ExprContext) = visitExprOr(ctx.exprOr())

    override fun visitOffsetByClause(ctx: PartiQLParser.OffsetByClauseContext): PartiqlAst.PartiqlAstNode =
        visit(ctx.expr())

    override fun visitWhereClause(ctx: PartiQLParser.WhereClauseContext) = visitExpr(ctx.expr())

    override fun visitHavingClause(ctx: PartiQLParser.HavingClauseContext) = visitExpr(ctx.expr())

    override fun visitLetClause(ctx: PartiQLParser.LetClauseContext): PartiqlAst.Let {
        val letBindings = ctx.letBinding().map { binding -> visit(binding) as PartiqlAst.LetBinding }
        return PartiqlAst.BUILDER().let(letBindings)
    }

    override fun visitLetBinding(ctx: PartiQLParser.LetBindingContext): PartiqlAst.LetBinding {
        val expr = visit(ctx.expr()) as PartiqlAst.Expr
        val name = ctx.symbolPrimitive().getString()
        return PartiqlAst.BUILDER().letBinding(expr, name)
    }

    override fun visitOrderBy(ctx: PartiQLParser.OrderByContext): PartiqlAst.OrderBy {
        val sortSpecs = ctx.orderSortSpec().map { spec -> visit(spec) as PartiqlAst.SortSpec }
        val metas = ctx.ORDER().getSourceMetaContainer()
        return PartiqlAst.build { orderBy(sortSpecs, metas) }
    }

    override fun visitOrderBySortSpec(ctx: PartiQLParser.OrderBySortSpecContext): PartiqlAst.SortSpec {
        val expr = visit(ctx.expr()) as PartiqlAst.Expr
        val order =
            if (ctx.bySpec() != null) visit(ctx.bySpec()) as PartiqlAst.OrderingSpec else PartiqlAst.BUILDER().asc()
        val nullSpec = when {
            ctx.byNullSpec() != null -> visit(ctx.byNullSpec()) as PartiqlAst.NullsSpec
            order == PartiqlAst.BUILDER().desc() -> PartiqlAst.BUILDER().nullsFirst()
            else -> PartiqlAst.BUILDER().nullsLast()
        }
        return PartiqlAst.BUILDER().sortSpec(expr, orderingSpec = order, nullsSpec = nullSpec)
    }

    override fun visitNullSpecFirst(ctx: PartiQLParser.NullSpecFirstContext): PartiqlAst.NullsSpec.NullsFirst =
        PartiqlAst.BUILDER().nullsFirst()

    override fun visitNullSpecLast(ctx: PartiQLParser.NullSpecLastContext): PartiqlAst.NullsSpec.NullsLast =
        PartiqlAst.BUILDER().nullsLast()

    override fun visitOrderByAsc(ctx: PartiQLParser.OrderByAscContext): PartiqlAst.OrderingSpec.Asc =
        PartiqlAst.BUILDER().asc()

    override fun visitOrderByDesc(ctx: PartiQLParser.OrderByDescContext): PartiqlAst.OrderingSpec.Desc =
        PartiqlAst.BUILDER().desc()

    override fun visitGroupClause(ctx: PartiQLParser.GroupClauseContext): PartiqlAst.GroupBy {
        val strategy =
            if (ctx.PARTIAL() != null) PartiqlAst.BUILDER().groupPartial() else PartiqlAst.BUILDER().groupFull()
        val keys = ctx.groupKey().map { key -> visit(key) as PartiqlAst.GroupKey }
        val keyList = PartiqlAst.BUILDER().groupKeyList(keys)
        val alias = if (ctx.groupAlias() != null) ctx.groupAlias().symbolPrimitive().getString() else null
        return PartiqlAst.BUILDER().groupBy(strategy, keyList = keyList, groupAsAlias = alias)
    }

    override fun visitGroupKeyAliasNone(ctx: PartiQLParser.GroupKeyAliasNoneContext): PartiqlAst.GroupKey {
        val expr = visit(ctx.expr()) as PartiqlAst.Expr
        return PartiqlAst.BUILDER().groupKey(expr)
    }

    override fun visitGroupKeyAlias(ctx: PartiQLParser.GroupKeyAliasContext): PartiqlAst.GroupKey {
        val expr = visit(ctx.expr()) as PartiqlAst.Expr
        val alias = if (ctx.symbolPrimitive() != null) ctx.symbolPrimitive().getString() else null
        return PartiqlAst.BUILDER().groupKey(expr, asAlias = alias)
    }

    override fun visitFromClause(ctx: PartiQLParser.FromClauseContext) = visit(ctx.tableReference()) as PartiqlAst.FromSource
    override fun visitExprTermWrappedQuery(ctx: PartiQLParser.ExprTermWrappedQueryContext) = visit(ctx.query()) as PartiqlAst.Expr

    override fun visitDql(ctx: PartiQLParser.DqlContext) = PartiqlAst.build {
        val query = visitQuery(ctx.query())
        query(query, query.metas)
    }

    override fun visitQueryDql(ctx: PartiQLParser.QueryDqlContext): PartiqlAst.PartiqlAstNode = visitDql(ctx.dql())
    override fun visitQueryDml(ctx: PartiQLParser.QueryDmlContext): PartiqlAst.PartiqlAstNode = visit(ctx.dml())

    override fun visitQuery(ctx: PartiQLParser.QueryContext): PartiqlAst.Expr = visit(ctx.querySet()) as PartiqlAst.Expr

    override fun visitQuerySetSingleQuery(ctx: PartiQLParser.QuerySetSingleQueryContext): PartiqlAst.PartiqlAstNode = visit(ctx.singleQuery())

    override fun visitQuerySetIntersect(ctx: PartiQLParser.QuerySetIntersectContext): PartiqlAst.Expr.Intersect {
        val lhs = visit(ctx.lhs) as PartiqlAst.Expr
        val rhs = visit(ctx.rhs) as PartiqlAst.Expr
        val quantifier = if (ctx.ALL() != null) PartiqlAst.BUILDER().all() else PartiqlAst.BUILDER().distinct()
        return PartiqlAst.BUILDER().intersect(quantifier, listOf(lhs, rhs))
    }

    override fun visitQuerySetExcept(ctx: PartiQLParser.QuerySetExceptContext): PartiqlAst.Expr.Except {
        val lhs = visit(ctx.lhs) as PartiqlAst.Expr
        val rhs = visit(ctx.rhs) as PartiqlAst.Expr
        val quantifier = if (ctx.ALL() != null) PartiqlAst.BUILDER().all() else PartiqlAst.BUILDER().distinct()
        return PartiqlAst.BUILDER().except(quantifier, listOf(lhs, rhs))
    }

    override fun visitQuerySetUnion(ctx: PartiQLParser.QuerySetUnionContext): PartiqlAst.Expr.Union {
        val lhs = visit(ctx.lhs) as PartiqlAst.Expr
        val rhs = visit(ctx.rhs) as PartiqlAst.Expr
        val quantifier = if (ctx.ALL() != null) PartiqlAst.BUILDER().all() else PartiqlAst.BUILDER().distinct()
        return PartiqlAst.BUILDER().union(quantifier, listOf(lhs, rhs))
    }

    // TODO: Add metas
    private fun convertSymbolPrimitive(sym: PartiQLParser.SymbolPrimitiveContext?): SymbolPrimitive? = when (sym) {
        null -> null
        else -> SymbolPrimitive(sym.getString(), mapOf())
    }

    /**
     *
     * TABLE REFERENCES & JOINS
     *
     */

    override fun visitTableBaseRefClauses(ctx: PartiQLParser.TableBaseRefClausesContext): PartiqlAst.FromSource.Scan {
        val expr = visitExpr(ctx.expr())
        val asAlias = if (ctx.asIdent() != null) convertSymbolPrimitive(ctx.asIdent().symbolPrimitive()) else null
        val atAlias = if (ctx.atIdent() != null) convertSymbolPrimitive(ctx.atIdent().symbolPrimitive()) else null
        val byAlias = if (ctx.byIdent() != null) convertSymbolPrimitive(ctx.byIdent().symbolPrimitive()) else null
        return PartiqlAst.FromSource.Scan(expr, asAlias = asAlias, byAlias = byAlias, atAlias = atAlias)
    }

    override fun visitFromClauseSimpleExplicit(ctx: PartiQLParser.FromClauseSimpleExplicitContext): PartiqlAst.FromSource.Scan {
        val expr = visitPathSimple(ctx.pathSimple())
        val asAlias = if (ctx.asIdent() != null) convertSymbolPrimitive(ctx.asIdent().symbolPrimitive()) else null
        val atAlias = if (ctx.atIdent() != null) convertSymbolPrimitive(ctx.atIdent().symbolPrimitive()) else null
        val byAlias = if (ctx.byIdent() != null) convertSymbolPrimitive(ctx.byIdent().symbolPrimitive()) else null
        return PartiqlAst.FromSource.Scan(expr, asAlias = asAlias, byAlias = byAlias, atAlias = atAlias)
    }

    override fun visitTableUnpivot(ctx: PartiQLParser.TableUnpivotContext): PartiqlAst.PartiqlAstNode {
        val expr = visit(ctx.expr()) as PartiqlAst.Expr
        val asAlias = if (ctx.asIdent() != null) ctx.asIdent().symbolPrimitive().getString() else null
        val atAlias = if (ctx.atIdent() != null) ctx.atIdent().symbolPrimitive().getString() else null
        val byAlias = if (ctx.byIdent() != null) ctx.byIdent().symbolPrimitive().getString() else null
        return PartiqlAst.build {
            unpivot(expr, asAlias = asAlias, atAlias = atAlias, byAlias = byAlias)
        }
    }

    /**
     * Note: Similar to the old SqlParser, we have an odd condition (if the RHS is a nested join), where we flip
     * the LHS and RHS operands.
     */
    override fun visitTableCrossJoin(ctx: PartiQLParser.TableCrossJoinContext): PartiqlAst.FromSource {
        val lhs = visit(ctx.lhs) as PartiqlAst.FromSource
        val joinType = visitJoinType(ctx.joinType())
        val rhs = visit(ctx.rhs) as PartiqlAst.FromSource
        val metas = metaContainerOf(IsImplictJoinMeta.instance)
        return when (ctx.rhs) {
            is PartiQLParser.JoinRhsTableJoinedContext -> PartiqlAst.BUILDER().join(joinType, rhs, lhs, metas = metas)
            else -> PartiqlAst.BUILDER().join(joinType, lhs, rhs, metas = metas)
        }
    }

    /**
     * Note: Similar to the old SqlParser, we have an odd condition (if the RHS is a nested join), where we flip
     * the LHS and RHS operands.
     */
    override fun visitTableQualifiedJoin(ctx: PartiQLParser.TableQualifiedJoinContext): PartiqlAst.PartiqlAstNode {
        val lhs = visit(ctx.lhs) as PartiqlAst.FromSource
        val joinType = visitJoinType(ctx.joinType())
        val rhs = visit(ctx.rhs) as PartiqlAst.FromSource
        val predicate = if (ctx.joinSpec() != null) visit(ctx.joinSpec()) as PartiqlAst.Expr else null
        return when (ctx.rhs) {
            is PartiQLParser.JoinRhsTableJoinedContext -> PartiqlAst.BUILDER().join(joinType, rhs, lhs, predicate)
            else -> PartiqlAst.BUILDER().join(joinType, lhs, rhs, predicate)
        }
    }

    override fun visitTableBaseRefSymbol(ctx: PartiQLParser.TableBaseRefSymbolContext): PartiqlAst.FromSource {
        val expr = visitExpr(ctx.expr())
        val name = ctx.symbolPrimitive().getString()
        return PartiqlAst.BUILDER().scan(expr, name)
    }

    override fun visitFromClauseSimpleImplicit(ctx: PartiQLParser.FromClauseSimpleImplicitContext): PartiqlAst.FromSource {
        val path = visitPathSimple(ctx.pathSimple())
        val name = ctx.symbolPrimitive().getString()
        return PartiqlAst.BUILDER().scan(path, name)
    }

    override fun visitTableWrapped(ctx: PartiQLParser.TableWrappedContext): PartiqlAst.PartiqlAstNode = visit(ctx.tableReference())

    override fun visitJoinSpec(ctx: PartiQLParser.JoinSpecContext) = visitExpr(ctx.expr())

    override fun visitJoinType(ctx: PartiQLParser.JoinTypeContext?): PartiqlAst.JoinType {
        return when {
            ctx == null -> PartiqlAst.JoinType.Inner()
            ctx.LEFT() != null -> PartiqlAst.JoinType.Left()
            ctx.RIGHT() != null -> PartiqlAst.JoinType.Right()
            ctx.INNER() != null -> PartiqlAst.JoinType.Inner()
            ctx.FULL() != null -> PartiqlAst.JoinType.Full()
            ctx.OUTER() != null -> PartiqlAst.JoinType.Full()
            else -> PartiqlAst.JoinType.Inner()
        }
    }

    override fun visitJoinRhsTableJoined(ctx: PartiQLParser.JoinRhsTableJoinedContext) = visit(ctx.tableReference()) as PartiqlAst.FromSource

    /**
     *
     * EXPRESSIONS
     *
     */

    override fun visitBag(ctx: PartiQLParser.BagContext): PartiqlAst.Expr.Bag {
        val exprList = ctx.expr().map { expr -> visit(expr) as PartiqlAst.Expr }
        return PartiqlAst.Expr.Bag(exprList)
    }

    override fun visitParameter(ctx: PartiQLParser.ParameterContext): PartiqlAst.PartiqlAstNode {
        val parameterIndex = parameterIndexes[ctx.QUESTION_MARK().symbol.tokenIndex]
            ?: throw ParseException("Unable to find index of parameter.")
        return PartiqlAst.build { parameter(parameterIndex.toLong()) }
    }

    override fun visitSequenceConstructor(ctx: PartiQLParser.SequenceConstructorContext): PartiqlAst.Expr {
        val expressions = visitOrEmpty(PartiqlAst.Expr::class, ctx.expr())
        return PartiqlAst.build {
            when (ctx.datatype.type) {
                PartiQLParser.LIST -> list(expressions)
                PartiQLParser.SEXP -> sexp(expressions)
                else -> throw ParseException("Unknown sequence")
            }
        }
    }

    override fun visitAggregateBase(ctx: PartiQLParser.AggregateBaseContext): PartiqlAst.Expr.CallAgg {
        val strategy = getStrategy(ctx.setQuantifierStrategy(), default = PartiqlAst.SetQuantifier.All())
        val arg = visitExpr(ctx.expr())
        return PartiqlAst.build { callAgg(strategy, ctx.func.text.toLowerCase(), arg) }
    }

    override fun visitCountAll(ctx: PartiQLParser.CountAllContext) = PartiqlAst.build {
        callAgg(all(), ctx.func.text.toLowerCase(), lit(ionInt(1)), metaContainerOf(IsCountStarMeta.instance))
    }

    override fun visitExtract(ctx: PartiQLParser.ExtractContext): PartiqlAst.Expr.Call {
        if (!DATE_TIME_PART_KEYWORDS.contains(ctx.IDENTIFIER().text.toLowerCase())) {
            throw ParseException("Expected one of: $DATE_TIME_PART_KEYWORDS")
        }
        val datetimePart = PartiqlAst.Expr.Lit(ion.newSymbol(ctx.IDENTIFIER().text).toIonElement())
        val timeExpr = visit(ctx.rhs) as PartiqlAst.Expr
        val args = listOf(datetimePart, timeExpr)
        return PartiqlAst.Expr.Call(SymbolPrimitive(ctx.EXTRACT().text.toLowerCase(), mapOf()), args)
    }

    override fun visitTrimFunction(ctx: PartiQLParser.TrimFunctionContext): PartiqlAst.PartiqlAstNode {
        val modifier = if (ctx.mod != null) ctx.mod.text.toLowerCase().toSymbol() else null
        val substring = if (ctx.sub != null) visitExpr(ctx.sub) else null
        val target = visitExpr(ctx.target)
        val args = listOfNotNull(modifier, substring, target)
        return PartiqlAst.Expr.Call(SymbolPrimitive(ctx.func.text.toLowerCase(), mapOf()), args)
    }

    override fun visitDateFunction(ctx: PartiQLParser.DateFunctionContext): PartiqlAst.Expr.Call {
        if (!DATE_TIME_PART_KEYWORDS.contains(ctx.dt.text.toLowerCase())) {
            throw ParseException("Expected one of: $DATE_TIME_PART_KEYWORDS")
        }
        val datetimePart = PartiqlAst.Expr.Lit(ion.newSymbol(ctx.dt.text).toIonElement())
        val secondaryArgs = visitOrEmpty(PartiqlAst.Expr::class, ctx.expr())
        val args = listOf(datetimePart) + secondaryArgs
        return PartiqlAst.Expr.Call(SymbolPrimitive(ctx.func.text.toLowerCase(), mapOf()), args, mapOf())
    }

    override fun visitSubstring(ctx: PartiQLParser.SubstringContext): PartiqlAst.Expr.Call {
        val args = ctx.expr().map { expr -> visit(expr) as PartiqlAst.Expr }
        return PartiqlAst.Expr.Call(SymbolPrimitive(ctx.SUBSTRING().text.toLowerCase(), mapOf()), args, mapOf())
    }

    override fun visitVarRefExpr(ctx: PartiQLParser.VarRefExprContext): PartiqlAst.PartiqlAstNode = PartiqlAst.build {
        when {
            ctx.IDENTIFIER_QUOTED() != null -> id(ctx.IDENTIFIER_QUOTED().getStringValue(), caseSensitive(), unqualified(), ctx.IDENTIFIER_QUOTED().getSourceMetaContainer())
            ctx.IDENTIFIER() != null -> id(ctx.IDENTIFIER().getStringValue(), caseInsensitive(), unqualified(), ctx.IDENTIFIER().getSourceMetaContainer())
            ctx.IDENTIFIER_AT_QUOTED() != null -> id(ctx.IDENTIFIER_AT_QUOTED().getStringValue(), caseSensitive(), localsFirst(), ctx.IDENTIFIER_AT_QUOTED().getSourceMetaContainer())
            ctx.IDENTIFIER_AT_UNQUOTED() != null -> id(ctx.IDENTIFIER_AT_UNQUOTED().getStringValue(), caseInsensitive(), localsFirst(), ctx.IDENTIFIER_AT_UNQUOTED().getSourceMetaContainer())
            else -> throw ParseException("Invalid variable reference.")
        }
    }

    override fun visitSymbolPrimitive(ctx: PartiQLParser.SymbolPrimitiveContext) = PartiqlAst.build {
        when {
            ctx.IDENTIFIER_QUOTED() != null -> id(ctx.IDENTIFIER_QUOTED().getStringValue(), caseSensitive(), unqualified(), ctx.IDENTIFIER_QUOTED().getSourceMetaContainer())
            ctx.IDENTIFIER() != null -> id(ctx.IDENTIFIER().getStringValue(), caseInsensitive(), unqualified(), ctx.IDENTIFIER().getSourceMetaContainer())
            else -> throw ParseException("Invalid symbol reference.")
        }
    }

    /**
     * EXPRESSIONS
     */

    override fun visitExprOr(ctx: PartiQLParser.ExprOrContext) = visitBinaryOperation(ctx.lhs, ctx.rhs, ctx.op, ctx.parent)
    override fun visitExprAnd(ctx: PartiQLParser.ExprAndContext) = visitBinaryOperation(ctx.lhs, ctx.rhs, ctx.op, ctx.parent)
    override fun visitMathOp00(ctx: PartiQLParser.MathOp00Context): PartiqlAst.PartiqlAstNode = visitBinaryOperation(ctx.lhs, ctx.rhs, ctx.op, ctx.parent)
    override fun visitMathOp01(ctx: PartiQLParser.MathOp01Context): PartiqlAst.PartiqlAstNode = visitBinaryOperation(ctx.lhs, ctx.rhs, ctx.op, ctx.parent)
    override fun visitMathOp02(ctx: PartiQLParser.MathOp02Context): PartiqlAst.PartiqlAstNode = visitBinaryOperation(ctx.lhs, ctx.rhs, ctx.op, ctx.parent)
    override fun visitPredicateComparison(ctx: PartiQLParser.PredicateComparisonContext) = visitBinaryOperation(ctx.lhs, ctx.rhs, ctx.op)

    private fun visitBinaryOperation(lhs: ParserRuleContext?, rhs: ParserRuleContext?, op: Token?, parent: ParserRuleContext? = null): PartiqlAst.Expr {
        if (parent != null) return visit(parent) as PartiqlAst.Expr
        val args = visitOrEmpty(PartiqlAst.Expr::class, lhs!!, rhs!!)
        return PartiqlAst.build {
            val metas = op.getSourceMetaContainer()
            when (op!!.type) {
                PartiQLParser.AND -> and(args, metas)
                PartiQLParser.OR -> or(args, metas)
                PartiQLParser.ASTERISK -> times(args, metas)
                PartiQLParser.SLASH_FORWARD -> divide(args, metas)
                PartiQLParser.PLUS -> plus(args, metas)
                PartiQLParser.MINUS -> minus(args, metas)
                PartiQLParser.PERCENT -> modulo(args, metas)
                PartiQLParser.CONCAT -> concat(args, metas)
                PartiQLParser.ANGLE_LEFT -> lt(args, metas)
                PartiQLParser.LT_EQ -> lte(args, metas)
                PartiQLParser.ANGLE_RIGHT -> gt(args, metas)
                PartiQLParser.GT_EQ -> gte(args, metas)
                PartiQLParser.NEQ -> ne(args, metas)
                PartiQLParser.EQ -> eq(args, metas)
                else -> throw ParseException("Unknown binary operator")
            }
        }
    }

    private fun visitUnaryOperation(operand: ParserRuleContext?, op: Token?, parent: ParserRuleContext? = null): PartiqlAst.PartiqlAstNode {
        if (parent != null) return visit(parent) as PartiqlAst.Expr
        val arg = visitOrEmpty(PartiqlAst.Expr::class, operand!!)
        return PartiqlAst.build {
            val metas = op.getSourceMetaContainer()
            when (op!!.type) {
                PartiQLParser.PLUS -> pos(arg, metas)
                PartiQLParser.MINUS -> neg(arg, metas)
                PartiQLParser.NOT -> not(arg, metas)
                else -> throw ParseException("Unknown unary operator")
            }
        }
    }

    override fun visitValueExpr(ctx: PartiQLParser.ValueExprContext) = visitUnaryOperation(ctx.rhs, ctx.sign, ctx.parent)
    override fun visitExprNot(ctx: PartiQLParser.ExprNotContext) = visitUnaryOperation(ctx.rhs, ctx.op, ctx.parent)

    override fun visitPredicateIn(ctx: PartiQLParser.PredicateInContext): PartiqlAst.PartiqlAstNode {
        val args = visitOrEmpty(PartiqlAst.Expr::class, ctx.lhs, ctx.rhs)
        val metas = ctx.IN().getSourceMetaContainer()
        val notMetas = ctx.NOT().getSourceMetaContainer()
        return PartiqlAst.build {
            if (ctx.NOT() != null) not(inCollection(args, metas), notMetas + metaContainerOf(LegacyLogicalNotMeta.instance))
            else inCollection(args, metas)
        }
    }

    override fun visitPredicateIs(ctx: PartiQLParser.PredicateIsContext): PartiqlAst.PartiqlAstNode {
        val lhs = visit(ctx.lhs) as PartiqlAst.Expr
        val rhs = visit(ctx.type()) as PartiqlAst.Type
        val isType = PartiqlAst.build { isType(lhs, rhs, ctx.IS().getSourceMetaContainer()) }
        return if (ctx.NOT() == null) isType else PartiqlAst.build { not(isType, ctx.NOT().getSourceMetaContainer() + metaContainerOf(LegacyLogicalNotMeta.instance)) }
    }

    override fun visitPredicateBetween(ctx: PartiQLParser.PredicateBetweenContext): PartiqlAst.PartiqlAstNode {
        val args = visitOrEmpty(PartiqlAst.Expr::class, ctx.lhs, ctx.lower, ctx.upper)
        val between = PartiqlAst.build { between(args[0], args[1], args[2], ctx.BETWEEN().getSourceMetaContainer()) }
        return if (ctx.NOT() == null) between else PartiqlAst.build { not(between, ctx.NOT().getSourceMetaContainer() + metaContainerOf(LegacyLogicalNotMeta.instance)) }
    }

    override fun visitPredicateLike(ctx: PartiQLParser.PredicateLikeContext): PartiqlAst.PartiqlAstNode {
        val args = visitOrEmpty(PartiqlAst.Expr::class, ctx.lhs, ctx.rhs)
        val escape = if (ctx.escape == null) null else visit(ctx.escape) as PartiqlAst.Expr
        val like: PartiqlAst.Expr = PartiqlAst.BUILDER().like(args[0], args[1], escape, ctx.LIKE().getSourceMetaContainer())
        return if (ctx.NOT() == null) like else PartiqlAst.build { not(like, metas = ctx.NOT().getSourceMetaContainer() + metaContainerOf(LegacyLogicalNotMeta.instance)) }
    }

    override fun visitLiteralNull(ctx: PartiQLParser.LiteralNullContext): PartiqlAst.PartiqlAstNode =
        PartiqlAst.build { lit(ionNull(), ctx.NULL().getSourceMetaContainer()) }

    override fun visitLiteralMissing(ctx: PartiQLParser.LiteralMissingContext): PartiqlAst.PartiqlAstNode =
        PartiqlAst.build { missing(ctx.MISSING().getSourceMetaContainer()) }

    override fun visitLiteralTrue(ctx: PartiQLParser.LiteralTrueContext): PartiqlAst.PartiqlAstNode =
        PartiqlAst.build { lit(ionBool(true), ctx.TRUE().getSourceMetaContainer()) }

    override fun visitLiteralFalse(ctx: PartiQLParser.LiteralFalseContext): PartiqlAst.PartiqlAstNode =
        PartiqlAst.build { lit(ionBool(false), ctx.FALSE().getSourceMetaContainer()) }

    override fun visitLiteralIon(ctx: PartiQLParser.LiteralIonContext): PartiqlAst.PartiqlAstNode =
        PartiqlAst.build { lit(ion.singleValue(ctx.ION_CLOSURE().getStringValue()).toIonElement(), ctx.ION_CLOSURE().getSourceMetaContainer()) }

    override fun visitLiteralString(ctx: PartiQLParser.LiteralStringContext): PartiqlAst.PartiqlAstNode =
        PartiqlAst.build { lit(ionString(ctx.LITERAL_STRING().getStringValue()), ctx.LITERAL_STRING().getSourceMetaContainer()) }

    override fun visitLiteralInteger(ctx: PartiQLParser.LiteralIntegerContext): PartiqlAst.Expr.Lit = PartiqlAst.build {
        lit(ionInt(BigInteger(ctx.LITERAL_INTEGER().text, 10)), ctx.LITERAL_INTEGER().getSourceMetaContainer())
    }

    override fun visitLiteralDate(ctx: PartiQLParser.LiteralDateContext): PartiqlAst.PartiqlAstNode {
        val dateString = ctx.LITERAL_STRING().getStringValue()
        val (year, month, day) = dateString.split("-")
        return PartiqlAst.BUILDER().date(year.toLong(), month.toLong(), day.toLong(), ctx.DATE().getSourceMetaContainer())
    }

    override fun visitLiteralTime(ctx: PartiQLParser.LiteralTimeContext): PartiqlAst.PartiqlAstNode {
        val timeString = ctx.LITERAL_STRING().getStringValue()
        val precision = when (ctx.LITERAL_INTEGER()) {
            null -> try {
                getPrecisionFromTimeString(timeString).toLong()
            } catch (e: EvaluationException) {
                throw ParseException(
                    "Unable to parse precision.",
                    e
                )
            }
            else -> ctx.LITERAL_INTEGER().text.toInteger().toLong()
        }
        if (precision < 0 || precision > MAX_PRECISION_FOR_TIME) {
            throw ParseException("Precision out of bounds")
        }
        val time: LocalTime
        try {
            time = LocalTime.parse(timeString, DateTimeFormatter.ISO_TIME)
        } catch (e: DateTimeParseException) {
            throw ParseException("Unable to parse time", e)
        }
        return PartiqlAst.build {
            litTime(
                timeValue(
                    time.hour.toLong(), time.minute.toLong(), time.second.toLong(), time.nano.toLong(),
                    precision, false, null, ctx.LITERAL_STRING().getSourceMetaContainer()
                ),
                ctx.TIME().getSourceMetaContainer()
            )
        }
    }

    override fun visitLiteralTimeZone(ctx: PartiQLParser.LiteralTimeZoneContext): PartiqlAst.PartiqlAstNode {
        val timeString = ctx.LITERAL_STRING().getStringValue()
        val precision = when (ctx.LITERAL_INTEGER()) {
            null -> try {
                getPrecisionFromTimeString(timeString).toLong()
            } catch (e: EvaluationException) {
                throw ParseException(
                    "Unable to parse precision.",
                    e
                )
            }
            else -> ctx.LITERAL_INTEGER().text.toInteger().toLong()
        }
        if (precision < 0 || precision > MAX_PRECISION_FOR_TIME) {
            throw ParseException("Precision out of bounds")
        }
        try {
            val time: OffsetTime
            time = OffsetTime.parse(timeString)
            return PartiqlAst.BUILDER().litTime(
                PartiqlAst.BUILDER().timeValue(
                    time.hour.toLong(), time.minute.toLong(), time.second.toLong(), time.nano.toLong(),
                    precision, true, (time.offset.totalSeconds / 60).toLong()
                )
            )
        } catch (e: DateTimeParseException) {
            val time: LocalTime
            try {
                time = LocalTime.parse(timeString)
            } catch (e: DateTimeParseException) {
                throw ParseException("Unable to parse time", e)
            }
            return PartiqlAst.build {
                litTime(
                    timeValue(
                        time.hour.toLong(), time.minute.toLong(), time.second.toLong(),
                        time.nano.toLong(), precision, true, null,
                        ctx.LITERAL_STRING().getSourceMetaContainer()
                    ),
                    ctx.TIME(0).getSourceMetaContainer()
                )
            }
        }
    }

    override fun visitValues(ctx: PartiQLParser.ValuesContext): PartiqlAst.Expr.Bag {
        val rows = ctx.valueRow().map { row -> visitValueRow(row) }
        return PartiqlAst.build { bag(rows) }
    }

    override fun visitValueRow(ctx: PartiQLParser.ValueRowContext): PartiqlAst.Expr.List {
        val expressions = ctx.expr().map { expr -> visitExpr(expr) }
        return PartiqlAst.build { list(expressions) }
    }

    override fun visitValueList(ctx: PartiQLParser.ValueListContext): PartiqlAst.Expr.List {
        val expressions = ctx.expr().map { expr -> visitExpr(expr) }
        return PartiqlAst.build { list(expressions) }
    }

    override fun visitCaseExpr(ctx: PartiQLParser.CaseExprContext): PartiqlAst.Expr {
        val exprPairList = mutableListOf<PartiqlAst.ExprPair>()
        val start = if (ctx.case_ == null) 0 else 1
        val end = if (ctx.ELSE() == null) ctx.expr().size else ctx.expr().size - 1
        for (i in start until end step 2) {
            val whenExpr = visitExpr(ctx.expr(i))
            val thenExpr = visitExpr(ctx.expr(i + 1))
            exprPairList.add(PartiqlAst.build { exprPair(whenExpr, thenExpr) })
        }
        val elseExpr = if (ctx.ELSE() != null) visitExpr(ctx.expr(end)) else null
        return PartiqlAst.build {
            val caseMeta = ctx.CASE().getSourceMetaContainer()
            when (ctx.case_) {
                null -> searchedCase(exprPairList(exprPairList), elseExpr, metas = caseMeta)
                else -> simpleCase(visitExpr(ctx.case_), exprPairList(exprPairList), elseExpr, metas = caseMeta)
            }
        }
    }

    override fun visitCast(ctx: PartiQLParser.CastContext): PartiqlAst.Expr.Cast {
        val expr = visitExpr(ctx.expr())
        val type = visit(ctx.type()) as PartiqlAst.Type
        return PartiqlAst.BUILDER().cast(expr, type)
    }

    override fun visitCanCast(ctx: PartiQLParser.CanCastContext): PartiqlAst.Expr.CanCast {
        val expr = visitExpr(ctx.expr())
        val type = visit(ctx.type()) as PartiqlAst.Type
        return PartiqlAst.BUILDER().canCast(expr, type)
    }

    override fun visitCanLosslessCast(ctx: PartiQLParser.CanLosslessCastContext): PartiqlAst.Expr.CanLosslessCast {
        val expr = visitExpr(ctx.expr())
        val type = visit(ctx.type()) as PartiqlAst.Type
        return PartiqlAst.BUILDER().canLosslessCast(expr, type)
    }

    override fun visitTypeAtomic(ctx: PartiQLParser.TypeAtomicContext): PartiqlAst.Type {
        return when {
            ctx.NULL() != null -> PartiqlAst.Type.NullType()
            ctx.BOOL() != null || ctx.BOOLEAN() != null -> PartiqlAst.Type.BooleanType()
            ctx.SMALLINT() != null -> PartiqlAst.Type.SmallintType()
            ctx.INT2() != null || ctx.INTEGER2() != null -> PartiqlAst.Type.SmallintType()
            ctx.INT() != null || ctx.INTEGER() != null -> PartiqlAst.Type.IntegerType()
            ctx.INT4() != null || ctx.INTEGER4() != null -> PartiqlAst.Type.Integer4Type()
            ctx.INT8() != null || ctx.INTEGER8() != null -> PartiqlAst.Type.Integer8Type()
            ctx.BIGINT() != null -> PartiqlAst.Type.Integer8Type()
            ctx.REAL() != null -> PartiqlAst.Type.RealType()
            ctx.DOUBLE() != null -> PartiqlAst.Type.DoublePrecisionType()
            ctx.TIMESTAMP() != null -> PartiqlAst.Type.TimestampType()
            ctx.MISSING() != null -> PartiqlAst.Type.MissingType()
            ctx.STRING() != null -> PartiqlAst.Type.StringType()
            ctx.SYMBOL() != null -> PartiqlAst.Type.SymbolType()
            ctx.BLOB() != null -> PartiqlAst.Type.BlobType()
            ctx.CLOB() != null -> PartiqlAst.Type.ClobType()
            ctx.DATE() != null -> PartiqlAst.Type.DateType()
            ctx.STRUCT() != null -> PartiqlAst.Type.StructType()
            ctx.TUPLE() != null -> PartiqlAst.Type.TupleType()
            ctx.LIST() != null -> PartiqlAst.Type.SexpType()
            ctx.BAG() != null -> PartiqlAst.Type.BagType()
            ctx.ANY() != null -> PartiqlAst.Type.AnyType()
            else -> PartiqlAst.Type.AnyType()
        }
    }

    override fun visitTypeVarChar(ctx: PartiQLParser.TypeVarCharContext): PartiqlAst.Type.CharacterVaryingType {
        val length = if (ctx.length != null) ctx.length.text.toInteger().toLong().asPrimitive() else null
        return PartiqlAst.Type.CharacterVaryingType(length)
    }

    override fun visitTypeChar(ctx: PartiQLParser.TypeCharContext): PartiqlAst.Type.CharacterType {
        val length = if (ctx.length != null) ctx.length.text.toInteger().toLong().asPrimitive() else null
        return PartiqlAst.Type.CharacterType(length)
    }

    override fun visitTypeFloat(ctx: PartiQLParser.TypeFloatContext): PartiqlAst.Type.FloatType {
        val precision = if (ctx.precision != null) ctx.precision.text.toInteger().toLong().asPrimitive() else null
        return PartiqlAst.Type.FloatType(precision)
    }

    override fun visitTypeDecimal(ctx: PartiQLParser.TypeDecimalContext): PartiqlAst.Type {
        val precision = if (ctx.precision != null) ctx.precision.text.toInteger().toLong().asPrimitive() else null
        val scale = if (ctx.scale != null) ctx.scale.text.toInteger().toLong().asPrimitive() else null
        return PartiqlAst.Type.DecimalType(precision, scale)
    }

    override fun visitTypeNumeric(ctx: PartiQLParser.TypeNumericContext): PartiqlAst.Type.NumericType {
        val precision = if (ctx.precision != null) ctx.precision.text.toInteger().toLong().asPrimitive() else null
        val scale = if (ctx.scale != null) ctx.scale.text.toInteger().toLong().asPrimitive() else null
        return PartiqlAst.Type.NumericType(precision, scale)
    }

    override fun visitTypeTime(ctx: PartiQLParser.TypeTimeContext): PartiqlAst.Type.TimeType {
        val precision = if (ctx.precision != null) ctx.precision.text.toInteger().toLong().asPrimitive() else null
        return PartiqlAst.Type.TimeType(precision)
    }

    override fun visitTypeTimeZone(ctx: PartiQLParser.TypeTimeZoneContext): PartiqlAst.Type.TimeWithTimeZoneType {
        val precision = if (ctx.precision != null) ctx.precision.text.toInteger().toLong().asPrimitive() else null
        return PartiqlAst.Type.TimeWithTimeZoneType(precision)
    }

    // TODO: Determine if should throw error on else
    override fun visitTypeCustom(ctx: PartiQLParser.TypeCustomContext): PartiqlAst.Type {
        val customName: String = when (val name = ctx.symbolPrimitive().getString().toLowerCase()) {
            in CUSTOM_KEYWORDS -> name
            in CUSTOM_TYPE_ALIASES.keys -> CUSTOM_TYPE_ALIASES.getOrDefault(name, name)
            else -> name
        }
        return PartiqlAst.Type.CustomType(SymbolPrimitive(customName, mapOf()))
    }

    // TODO: Catch exception for exponent too large
    override fun visitLiteralDecimal(ctx: PartiQLParser.LiteralDecimalContext): PartiqlAst.PartiqlAstNode =
        PartiqlAst.Expr.Lit(ion.newDecimal(bigDecimalOf(ctx.LITERAL_DECIMAL().text)).toIonElement(), ctx.LITERAL_DECIMAL().getSourceMetaContainer())

    override fun visitFunctionCall(ctx: PartiQLParser.FunctionCallContext): PartiqlAst.PartiqlAstNode {
        val name = ctx.name.getString().toLowerCase()
        val args = ctx.expr().map { arg -> visit(arg) as PartiqlAst.Expr }
        return PartiqlAst.BUILDER().call(name, args)
    }

    override fun visitExprPrimaryPath(ctx: PartiQLParser.ExprPrimaryPathContext): PartiqlAst.PartiqlAstNode {
        val base = visit(ctx.exprPrimary()) as PartiqlAst.Expr
        val steps = ctx.pathStep().map { step -> visit(step) as PartiqlAst.PathStep }
        return PartiqlAst.Expr.Path(base, steps)
    }

    override fun visitPathStepIndexExpr(ctx: PartiQLParser.PathStepIndexExprContext): PartiqlAst.PartiqlAstNode {
        val expr = visit(ctx.key) as PartiqlAst.Expr
        return PartiqlAst.build { pathExpr(expr, PartiqlAst.CaseSensitivity.CaseSensitive(), metaContainerOf(IsPathIndexMeta.instance)) }
    }

    override fun visitPathStepDotExpr(ctx: PartiQLParser.PathStepDotExprContext) = getSymbolPathExpr(ctx.key)

    override fun visitPathStepIndexAll(ctx: PartiQLParser.PathStepIndexAllContext) = PartiqlAst.build { pathWildcard() }
    override fun visitPathStepDotAll(ctx: PartiQLParser.PathStepDotAllContext) = PartiqlAst.build { pathUnpivot() }

    override fun visitArray(ctx: PartiQLParser.ArrayContext) = PartiqlAst.build {
        list(visitOrEmpty(PartiqlAst.Expr::class, ctx.expr()))
    }

    override fun visitSetQuantifierStrategy(ctx: PartiQLParser.SetQuantifierStrategyContext?): PartiqlAst.SetQuantifier? = when {
        ctx == null -> null
        ctx.DISTINCT() != null -> PartiqlAst.SetQuantifier.Distinct()
        ctx.ALL() != null -> PartiqlAst.SetQuantifier.All()
        else -> null
    }

    /**
     * NOT OVERRIDDEN
     * Explicitly defining the override helps by showing the user (via the IDE) which methods remain to be overridden.
     */

    override fun visitAsIdent(ctx: PartiQLParser.AsIdentContext?): PartiqlAst.PartiqlAstNode = super.visitAsIdent(ctx)
    override fun visitAtIdent(ctx: PartiQLParser.AtIdentContext?): PartiqlAst.PartiqlAstNode = super.visitAtIdent(ctx)
    override fun visitByIdent(ctx: PartiQLParser.ByIdentContext?): PartiqlAst.PartiqlAstNode = super.visitByIdent(ctx)
    override fun visitTerminal(node: TerminalNode?): PartiqlAst.PartiqlAstNode = super.visitTerminal(node)
    override fun shouldVisitNextChild(node: RuleNode?, currentResult: PartiqlAst.PartiqlAstNode?) = super.shouldVisitNextChild(node, currentResult)
    override fun visitErrorNode(node: ErrorNode?): PartiqlAst.PartiqlAstNode = super.visitErrorNode(node)
    override fun visitChildren(node: RuleNode?): PartiqlAst.PartiqlAstNode = super.visitChildren(node)
    override fun visitExprPrimaryBase(ctx: PartiQLParser.ExprPrimaryBaseContext?): PartiqlAst.PartiqlAstNode = super.visitExprPrimaryBase(ctx)
    override fun visitExprTermBase(ctx: PartiQLParser.ExprTermBaseContext?): PartiqlAst.PartiqlAstNode = super.visitExprTermBase(ctx)
    override fun visitCollection(ctx: PartiQLParser.CollectionContext?): PartiqlAst.PartiqlAstNode = super.visitCollection(ctx)
    override fun visitPredicateBase(ctx: PartiQLParser.PredicateBaseContext?): PartiqlAst.PartiqlAstNode = super.visitPredicateBase(ctx)
    override fun visitGroupAlias(ctx: PartiQLParser.GroupAliasContext?): PartiqlAst.PartiqlAstNode = super.visitGroupAlias(ctx)
    override fun visitSingleQuery(ctx: PartiQLParser.SingleQueryContext?): PartiqlAst.PartiqlAstNode = super.visitSingleQuery(ctx)
    override fun visitTableJoined(ctx: PartiQLParser.TableJoinedContext?): PartiqlAst.PartiqlAstNode = super.visitTableJoined(ctx)
    override fun visitTableNonJoin(ctx: PartiQLParser.TableNonJoinContext?): PartiqlAst.PartiqlAstNode = super.visitTableNonJoin(ctx)
    override fun visitTableRefBase(ctx: PartiQLParser.TableRefBaseContext?): PartiqlAst.PartiqlAstNode = super.visitTableRefBase(ctx)
    override fun visitJoinRhsBase(ctx: PartiQLParser.JoinRhsBaseContext?): PartiqlAst.PartiqlAstNode = super.visitJoinRhsBase(ctx)

    /**
     *
     * HELPER METHODS
     *
     */

    private fun PartiqlAst.Expr.getStringValue(): String = when (this) {
        is PartiqlAst.Expr.Id -> this.name.text.toLowerCase()
        is PartiqlAst.Expr.Lit -> {
            when (this.value) {
                is SymbolElement -> this.value.symbolValue.toLowerCase()
                is StringElement -> this.value.stringValue.toLowerCase()
                else ->
                    this.value.stringValueOrNull ?: throw ParseException("Unable to pass the string value")
            }
        }
        else -> throw ParseException("Unable to get value")
    }

    private fun PartiqlAst.Expr.Id.toIdentifier(): PartiqlAst.Identifier {
        val name = this.name.text
        val case = this.case
        return PartiqlAst.build {
            identifier(name, case)
        }
    }

    /**
     * Converts a Path expression into a Projection Item (either ALL or EXPR). Note: A Projection Item only allows a
     * subset of a typical Path expressions. See the following examples.
     *
     * Examples of valid projections are:
     *
     * ```sql
     *      SELECT * FROM foo
     *      SELECT foo.* FROM foo
     *      SELECT f.* FROM foo as f
     *      SELECT foo.bar.* FROM foo
     *      SELECT f.bar.* FROM foo as f
     * ```
     * Also validates that the expression is valid for select list context. It does this by making
     * sure that expressions looking like the following do not appear:
     *
     * ```sql
     *      SELECT foo[*] FROM foo
     *      SELECT f.*.bar FROM foo as f
     *      SELECT foo[1].* FROM foo
     *      SELECT foo.*.bar FROM foo
     * ```
     */
    private fun convertPathToProjectionItem(path: PartiqlAst.Expr.Path, alias: String?): PartiqlAst.ProjectItem {
        val steps = mutableListOf<PartiqlAst.PathStep>()
        var containsIndex = false
        path.steps.forEachIndexed { index, step ->

            // Only last step can have a '.*'
            if (step is PartiqlAst.PathStep.PathUnpivot && index != path.steps.lastIndex) {
                throw ParseException("Projection item cannot unpivot unless at end.")
            }

            // No step can have an indexed wildcard: '[*]'
            if (step is PartiqlAst.PathStep.PathWildcard) {
                throw ParseException("Projection item cannot index using wildcard.")
            }

            // If the last step is '.*', no indexing is allowed
            if (step.metas.containsKey(IsPathIndexMeta.TAG)) {
                containsIndex = true
            }

            if (step !is PartiqlAst.PathStep.PathUnpivot) {
                steps.add(step)
            }
        }

        if (path.steps.last() is PartiqlAst.PathStep.PathUnpivot && containsIndex) {
            throw ParseException("Projection item use wildcard with any indexing.")
        }

        return PartiqlAst.build {
            when {
                path.steps.last() is PartiqlAst.PathStep.PathUnpivot && steps.isEmpty() -> projectAll(path.root)
                path.steps.last() is PartiqlAst.PathStep.PathUnpivot -> projectAll(path(path.root, steps))
                else -> projectExpr(path, asAlias = alias)
            }
        }
    }

    private fun <T : PartiqlAst.PartiqlAstNode> visitOrEmpty(clazz: KClass<T>, ctx: ParserRuleContext): T = clazz.cast(visit(ctx))
    private fun <T : PartiqlAst.PartiqlAstNode> visitOrEmpty(clazz: KClass<T>, ctx: List<ParserRuleContext>): List<T> = ctx.map { clazz.cast(visit(it)) }
    private fun <T : PartiqlAst.PartiqlAstNode> visitOrEmpty(clazz: KClass<T>, vararg ctx: ParserRuleContext): List<T> = when {
        ctx.isNullOrEmpty() -> emptyList()
        else -> visitOrEmpty(clazz, ctx.asList())
    }

    private fun TerminalNode.getStringValue(): String = when (this.symbol.type) {
        PartiQLParser.IDENTIFIER -> this.text
        PartiQLParser.IDENTIFIER_QUOTED -> this.text.removePrefix("\"").removeSuffix("\"").replace("\"\"", "\"")
        PartiQLParser.IDENTIFIER_AT_UNQUOTED -> this.text.removePrefix("@")
        PartiQLParser.IDENTIFIER_AT_QUOTED -> this.text.removePrefix("@").removePrefix("\"").removeSuffix("\"").replace("\"\"", "\"")
        PartiQLParser.LITERAL_STRING -> this.text.removePrefix("'").removeSuffix("'").replace("''", "'")
        PartiQLParser.ION_CLOSURE -> this.text.removePrefix("`").removeSuffix("`")
        else -> throw ParseException("Unsupported token for grabbing string value.")
    }

    private fun getStrategy(strategy: PartiQLParser.SetQuantifierStrategyContext?, default: PartiqlAst.SetQuantifier): PartiqlAst.SetQuantifier {
        return when {
            strategy == null -> default
            strategy.DISTINCT() != null -> PartiqlAst.SetQuantifier.Distinct()
            strategy.ALL() != null -> PartiqlAst.SetQuantifier.All()
            else -> default
        }
    }

    private fun getStrategy(strategy: PartiQLParser.SetQuantifierStrategyContext?): PartiqlAst.SetQuantifier? {
        return when {
            strategy == null -> null
            strategy.DISTINCT() != null -> PartiqlAst.SetQuantifier.Distinct()
            else -> null
        }
    }

    private fun getSetQuantifierStrategy(ctx: PartiQLParser.SelectClauseContext): PartiqlAst.SetQuantifier? {
        return when (ctx) {
            is PartiQLParser.SelectAllContext -> getStrategy(ctx.setQuantifierStrategy())
            is PartiQLParser.SelectItemsContext -> getStrategy(ctx.setQuantifierStrategy())
            is PartiQLParser.SelectValueContext -> getStrategy(ctx.setQuantifierStrategy())
            is PartiQLParser.SelectPivotContext -> null
            else -> null
        }
    }

    private fun PartiQLParser.SymbolPrimitiveContext.getString(): String {
        return when {
            this.IDENTIFIER_QUOTED() != null -> this.IDENTIFIER_QUOTED().getStringValue()
            this.IDENTIFIER() != null -> this.IDENTIFIER().text
            else -> throw ParseException("Unable to get symbol's text.")
        }
    }

    private fun getSymbolPathExpr(ctx: PartiQLParser.SymbolPrimitiveContext) = PartiqlAst.build {
        when {
            ctx.IDENTIFIER_QUOTED() != null -> pathExpr(lit(ionString(ctx.IDENTIFIER_QUOTED().getStringValue())), caseSensitive())
            ctx.IDENTIFIER() != null -> pathExpr(lit(ionString(ctx.IDENTIFIER().text)), caseInsensitive())
            else -> throw ParseException("Unable to get symbol's text.")
        }
    }

    private fun String.toInteger() = BigInteger(this, 10)

    private fun String.toSymbol(): PartiqlAst.Expr.Lit {
        val str = this
        return PartiqlAst.build {
            lit(ionSymbol(str))
        }
    }
}
