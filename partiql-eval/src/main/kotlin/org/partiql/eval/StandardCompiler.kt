package org.partiql.eval

import org.partiql.eval.internal.operator.Aggregate
import org.partiql.eval.internal.operator.rel.RelOpAggregate
import org.partiql.eval.internal.operator.rel.RelOpDistinct
import org.partiql.eval.internal.operator.rel.RelOpExceptAll
import org.partiql.eval.internal.operator.rel.RelOpExceptDistinct
import org.partiql.eval.internal.operator.rel.RelOpExclude
import org.partiql.eval.internal.operator.rel.RelOpFilter
import org.partiql.eval.internal.operator.rel.RelOpIntersectAll
import org.partiql.eval.internal.operator.rel.RelOpIntersectDistinct
import org.partiql.eval.internal.operator.rel.RelOpIterate
import org.partiql.eval.internal.operator.rel.RelOpIteratePermissive
import org.partiql.eval.internal.operator.rel.RelOpJoinInner
import org.partiql.eval.internal.operator.rel.RelOpJoinOuterFull
import org.partiql.eval.internal.operator.rel.RelOpJoinOuterLeft
import org.partiql.eval.internal.operator.rel.RelOpJoinOuterRight
import org.partiql.eval.internal.operator.rel.RelOpLimit
import org.partiql.eval.internal.operator.rel.RelOpOffset
import org.partiql.eval.internal.operator.rel.RelOpProject
import org.partiql.eval.internal.operator.rel.RelOpScan
import org.partiql.eval.internal.operator.rel.RelOpScanPermissive
import org.partiql.eval.internal.operator.rel.RelOpSort
import org.partiql.eval.internal.operator.rel.RelOpUnionAll
import org.partiql.eval.internal.operator.rel.RelOpUnionDistinct
import org.partiql.eval.internal.operator.rel.RelOpUnpivot
import org.partiql.eval.internal.operator.rex.ExprArray
import org.partiql.eval.internal.operator.rex.ExprBag
import org.partiql.eval.internal.operator.rex.ExprCall
import org.partiql.eval.internal.operator.rex.ExprCallDynamic
import org.partiql.eval.internal.operator.rex.ExprCaseBranch
import org.partiql.eval.internal.operator.rex.ExprCaseSearched
import org.partiql.eval.internal.operator.rex.ExprCast
import org.partiql.eval.internal.operator.rex.ExprCoalesce
import org.partiql.eval.internal.operator.rex.ExprLit
import org.partiql.eval.internal.operator.rex.ExprMissing
import org.partiql.eval.internal.operator.rex.ExprNullIf
import org.partiql.eval.internal.operator.rex.ExprPathIndex
import org.partiql.eval.internal.operator.rex.ExprPathKey
import org.partiql.eval.internal.operator.rex.ExprPathSymbol
import org.partiql.eval.internal.operator.rex.ExprPermissive
import org.partiql.eval.internal.operator.rex.ExprPivot
import org.partiql.eval.internal.operator.rex.ExprPivotPermissive
import org.partiql.eval.internal.operator.rex.ExprSelect
import org.partiql.eval.internal.operator.rex.ExprSpread
import org.partiql.eval.internal.operator.rex.ExprStructField
import org.partiql.eval.internal.operator.rex.ExprStructPermissive
import org.partiql.eval.internal.operator.rex.ExprStructStrict
import org.partiql.eval.internal.operator.rex.ExprSubquery
import org.partiql.eval.internal.operator.rex.ExprSubqueryRow
import org.partiql.eval.internal.operator.rex.ExprTable
import org.partiql.eval.internal.operator.rex.ExprVar
import org.partiql.eval.operator.Expression
import org.partiql.eval.operator.PhysicalOperator
import org.partiql.eval.operator.Relation
import org.partiql.eval.operator.Strategy
import org.partiql.plan.Collation
import org.partiql.plan.JoinType
import org.partiql.plan.Operation
import org.partiql.plan.Plan
import org.partiql.plan.Visitor
import org.partiql.plan.rel.Rel
import org.partiql.plan.rel.RelAggregate
import org.partiql.plan.rel.RelDistinct
import org.partiql.plan.rel.RelError
import org.partiql.plan.rel.RelExcept
import org.partiql.plan.rel.RelExclude
import org.partiql.plan.rel.RelFilter
import org.partiql.plan.rel.RelIntersect
import org.partiql.plan.rel.RelIterate
import org.partiql.plan.rel.RelJoin
import org.partiql.plan.rel.RelLimit
import org.partiql.plan.rel.RelOffset
import org.partiql.plan.rel.RelProject
import org.partiql.plan.rel.RelScan
import org.partiql.plan.rel.RelSort
import org.partiql.plan.rel.RelUnion
import org.partiql.plan.rel.RelUnpivot
import org.partiql.plan.rex.Rex
import org.partiql.plan.rex.RexArray
import org.partiql.plan.rex.RexBag
import org.partiql.plan.rex.RexCall
import org.partiql.plan.rex.RexCallDynamic
import org.partiql.plan.rex.RexCase
import org.partiql.plan.rex.RexCast
import org.partiql.plan.rex.RexCoalesce
import org.partiql.plan.rex.RexError
import org.partiql.plan.rex.RexLit
import org.partiql.plan.rex.RexMissing
import org.partiql.plan.rex.RexNullIf
import org.partiql.plan.rex.RexPathIndex
import org.partiql.plan.rex.RexPathKey
import org.partiql.plan.rex.RexPathSymbol
import org.partiql.plan.rex.RexPivot
import org.partiql.plan.rex.RexSelect
import org.partiql.plan.rex.RexSpread
import org.partiql.plan.rex.RexStruct
import org.partiql.plan.rex.RexSubquery
import org.partiql.plan.rex.RexSubqueryComp
import org.partiql.plan.rex.RexSubqueryIn
import org.partiql.plan.rex.RexSubqueryTest
import org.partiql.plan.rex.RexTable
import org.partiql.plan.rex.RexVar
import org.partiql.spi.value.Datum
import org.partiql.types.PType

/**
 * This class is responsible for producing an executable statement from logical operators.
 */
internal class StandardCompiler(mode: Mode, strategies: List<Strategy>) : PartiQLCompiler {

    /**
     * No custom operator strategies.
     */
    internal constructor(mode: Mode) : this(mode, emptyList())

    private val mode = mode.code()
    private val strategies = strategies
    private val unknown = PType.unknown()
    private val visitor = _Visitor()

    override fun prepare(plan: Plan): Statement = try {
        val operation = plan.getOperation()
        val statement: Statement = when {
            operation is Operation.Query -> compile(operation)
            else -> throw IllegalArgumentException("Only query statements are supported")
        }
        statement
    } catch (ex: Exception) {
        // TODO wrap in some PartiQL Exception
        throw ex
    }

    /**
     * Compile a query operation to a query statement.
     */
    private fun compile(operation: Operation.Query) = object : Statement {

        // compile the query root
        private val root = compile(operation.getRex(), Unit).catch()

        // execute with no parameters
        override fun execute(): Datum = root.eval(Environment())
    }

    private fun compile(rel: Rel, ctx: Unit): Relation = rel.accept(visitor, ctx) as Relation

    private fun compile(rex: Rex, ctx: Unit): Expression = rex.accept(visitor, ctx) as Expression

    /**
     * Transforms plan relation operators into the internal physical operators.
     */
    @Suppress("ClassName")
    private inner class _Visitor : Visitor<PhysicalOperator, Unit> {

        /**
         * Apply custom strategies left-to-right, returning the first match.
         *
         * @param operator
         * @param ctx
         * @return
         */
        override fun defaultReturn(operator: org.partiql.plan.Operator, ctx: Unit): PhysicalOperator {
            for (strategy in strategies) {
                val op = strategy.apply(operator)
                if (op != null) {
                    // first match
                    return op
                }
            }
            error("No compiler strategy matches the operator: ${operator::class.simpleName}")
        }

        override fun visitError(rel: RelError, ctx: Unit): Relation {
            throw IllegalStateException(rel.message)
        }

        // OPERATORS

        override fun visitAggregate(rel: RelAggregate, ctx: Unit): Relation {
            val input = compile(rel.getInput(), ctx)
            val aggs = rel.getCalls().map { call ->
                val agg = call.getAgg()
                val args = call.getArgs().map { compile(it, ctx).catch() }
                val distinct = call.isDistinct()
                Aggregate(agg, args, distinct)
            }
            val groups = rel.getGroups().map { compile(it, ctx).catch() }
            return RelOpAggregate(input, aggs, groups)
        }

        override fun visitDistinct(rel: RelDistinct, ctx: Unit): Relation {
            val input = compile(rel.getInput(), ctx)
            return RelOpDistinct(input)
        }

        override fun visitExcept(rel: RelExcept, ctx: Unit): Relation {
            val lhs = compile(rel.getLeft(), ctx)
            val rhs = compile(rel.getRight(), ctx)
            return when (rel.isAll()) {
                true -> RelOpExceptAll(lhs, rhs)
                else -> RelOpExceptDistinct(lhs, rhs)
            }
        }

        override fun visitExclude(rel: RelExclude, ctx: Unit): Relation {
            val input = compile(rel.getInput(), ctx)
            val paths = rel.getExclusions()
            return RelOpExclude(input, paths)
        }

        override fun visitFilter(rel: RelFilter, ctx: Unit): Relation {
            val input = compile(rel.getInput(), ctx)
            val predicate = compile(rel.getPredicate(), ctx).catch()
            return RelOpFilter(input, predicate)
        }

        override fun visitIntersect(rel: RelIntersect, ctx: Unit): Relation {
            val lhs = compile(rel.getLeft(), ctx)
            val rhs = compile(rel.getRight(), ctx)
            return when (rel.isAll()) {
                true -> RelOpIntersectAll(lhs, rhs)
                else -> RelOpIntersectDistinct(lhs, rhs)
            }
        }

        override fun visitIterate(rel: RelIterate, ctx: Unit): Relation {
            val input = compile(rel.getInput(), ctx)
            return when (mode) {
                Mode.PERMISSIVE -> RelOpIteratePermissive(input)
                Mode.STRICT -> RelOpIterate(input)
                else -> throw IllegalStateException("Unsupported execution mode: $mode")
            }
        }

        override fun visitJoin(rel: RelJoin, ctx: Unit): Relation {
            val lhs = compile(rel.getLeft(), ctx)
            val rhs = compile(rel.getRight(), ctx)
            val condition = rel.getCondition()?.let { compile(it, ctx) } ?: ExprLit(Datum.bool(true))

            // TODO JOIN SCHEMAS
            val lhsType = rel.getLeftSchema()
            val rhsType = rel.getRightSchema()

            return when (rel.getJoinType()) {
                JoinType.INNER -> RelOpJoinInner(lhs, rhs, condition)
                JoinType.LEFT -> RelOpJoinOuterLeft(lhs, rhs, condition, rhsType!!)
                JoinType.RIGHT -> RelOpJoinOuterRight(lhs, rhs, condition, lhsType!!)
                JoinType.FULL -> RelOpJoinOuterFull(lhs, rhs, condition, lhsType!!, rhsType!!)
            }
        }

        override fun visitLimit(rel: RelLimit, ctx: Unit): Relation {
            val input = compile(rel.getInput(), ctx)
            val limit = compile(rel.getLimit(), ctx)
            return RelOpLimit(input, limit)
        }

        override fun visitOffset(rel: RelOffset, ctx: Unit): Relation {
            val input = compile(rel.getInput(), ctx)
            val offset = compile(rel.getOffset(), ctx)
            return RelOpOffset(input, offset)
        }

        override fun visitProject(rel: RelProject, ctx: Unit): Relation {
            val input = compile(rel.getInput(), ctx)
            val projections = rel.getProjections().map { compile(it, ctx).catch() }
            return RelOpProject(input, projections)
        }

        override fun visitScan(rel: RelScan, ctx: Unit): Relation {
            val input = compile(rel.getInput(), ctx)
            return when (mode) {
                Mode.PERMISSIVE -> RelOpScanPermissive(input)
                Mode.STRICT -> RelOpScan(input)
                else -> throw IllegalStateException("Unsupported execution mode: $mode")
            }
        }

        override fun visitSort(rel: RelSort, ctx: Unit): Relation {
            val input = compile(rel.getInput(), ctx)
            val collations = rel.getCollations().map {
                val expr = compile(it.getRex(), ctx)
                val desc = it.getOrder() == Collation.Order.DESC
                val last = it.getNulls() == Collation.Nulls.LAST
                RelOpSort.Collation(expr, desc, last)
            }
            return RelOpSort(input, collations)
        }

        override fun visitUnion(rel: RelUnion, ctx: Unit): Relation {
            val lhs = compile(rel.getLeft(), ctx)
            val rhs = compile(rel.getRight(), ctx)
            return when (rel.isAll()) {
                true -> RelOpUnionAll(lhs, rhs)
                else -> RelOpUnionDistinct(lhs, rhs)
            }
        }

        override fun visitUnpivot(rel: RelUnpivot, ctx: Unit): Relation {
            val input = compile(rel.getInput(), ctx)
            return when (mode) {
                Mode.PERMISSIVE -> RelOpUnpivot.Permissive(input)
                Mode.STRICT -> RelOpUnpivot.Strict(input)
                else -> throw IllegalStateException("Unsupported execution mode: $mode")
            }
        }

        override fun visitError(rex: RexError, ctx: Unit): Expression {
            throw IllegalStateException(rex.getMessage())
        }

        // OPERATORS

        override fun visitArray(rex: RexArray, ctx: Unit): Expression {
            val values = rex.getValues().map { compile(it, ctx).catch() }
            return ExprArray(values)
        }

        override fun visitBag(rex: RexBag, ctx: Unit): Expression {
            val values = rex.getValues().map { compile(it, ctx).catch() }
            return ExprBag(values)
        }

        override fun visitCallDynamic(rex: RexCallDynamic, ctx: Unit): Expression {
            // Check candidate arity for uniformity
            var arity: Int = -1
            val name = rex.getName()
            // Check the candidate list size
            val functions = rex.getFunctions()
            if (functions.isEmpty()) {
                error("Dynamic call had an empty candidates list: $rex.")
            }
            // Compile the candidates
            val candidates = Array(functions.size) {
                val fn = functions[it]
                val fnArity = fn.parameters.size
                if (arity == -1) {
                    // set first
                    arity = fnArity
                } else {
                    if (fnArity != arity) {
                        error("Dynamic call candidate had different arity than others; found $fnArity but expected $arity")
                    }
                }
                // make a candidate
                fn
            }
            val args = rex.getArgs().map { compile(it, ctx).catch() }.toTypedArray()
            return ExprCallDynamic(name, candidates, args)
        }

        override fun visitCall(rex: RexCall, ctx: Unit): Expression {
            val func = rex.getFunction()
            val args = rex.getArgs()
            val catch = func.parameters.any { it.kind == PType.Kind.DYNAMIC }
            return when (catch) {
                true -> ExprCall(func, Array(args.size) { i -> compile(args[i], Unit).catch() })
                else -> ExprCall(func, Array(args.size) { i -> compile(args[i], Unit) })
            }
        }

        override fun visitCase(rex: RexCase, ctx: Unit): Expression {
            if (rex.getMatch() != null) {
                TODO("<case> expression")
            }
            val branches = rex.getBranches().map {
                val value = compile(it.getCondition(), ctx).catch()
                val result = compile(it.getResult(), ctx)
                ExprCaseBranch(value, result)
            }
            val default = rex.getDefault()?.let { compile(it, ctx) }
            return ExprCaseSearched(branches, default)
        }

        override fun visitCast(rex: RexCast, ctx: Unit): Expression {
            val operand = compile(rex.getOperand(), ctx)
            val target = rex.getTarget()
            return ExprCast(operand, target)
        }

        override fun visitCoalesce(rex: RexCoalesce, ctx: Unit): Expression {
            val args = rex.getArgs().map { compile(it, ctx) }.toTypedArray()
            return ExprCoalesce(args)
        }

        override fun visitLit(rex: RexLit, ctx: Unit): Expression {
            return ExprLit(rex.getValue())
        }

        override fun visitMissing(rex: RexMissing, ctx: Unit): Expression {
            return ExprMissing(unknown)
        }

        override fun visitNullIf(rex: RexNullIf, ctx: Unit): Expression {
            val value = compile(rex.getV1(), ctx)
            val nullifier = compile(rex.getV2(), ctx)
            return ExprNullIf(value, nullifier)
        }

        override fun visitPathIndex(rex: RexPathIndex, ctx: Unit): Expression {
            val operand = compile(rex.getOperand(), ctx)
            val index = compile(rex.getIndex(), ctx)
            return ExprPathIndex(operand, index)
        }

        override fun visitPathKey(rex: RexPathKey, ctx: Unit): Expression {
            val operand = compile(rex.getOperand(), ctx)
            val key = compile(rex.getKey(), ctx)
            return ExprPathKey(operand, key)
        }

        override fun visitPathSymbol(rex: RexPathSymbol, ctx: Unit): Expression {
            val operand = compile(rex.getOperand(), ctx)
            val symbol = rex.getSymbol()
            return ExprPathSymbol(operand, symbol)
        }

        override fun visitPivot(rex: RexPivot, ctx: Unit): Expression {
            val input = compile(rex.getInput(), ctx)
            val key = compile(rex.getKey(), ctx)
            val value = compile(rex.getValue(), ctx)
            return when (mode) {
                Mode.PERMISSIVE -> ExprPivotPermissive(input, key, value)
                Mode.STRICT -> ExprPivot(input, key, value)
                else -> throw IllegalStateException("Unsupported execution mode: $mode")
            }
        }

        override fun visitSelect(rex: RexSelect, ctx: Unit): Expression {
            val input = compile(rex.getInput(), ctx)
            val constructor = compile(rex.getConstructor(), ctx).catch()
            val ordered = rex.getInput().isOrdered()
            return ExprSelect(input, constructor, ordered)
        }

        override fun visitStruct(rex: RexStruct, ctx: Unit): Expression {
            val fields = rex.getFields().map {
                val k = compile(it.getKey(), ctx)
                val v = compile(it.getValue(), ctx).catch()
                ExprStructField(k, v)
            }
            return when (mode) {
                Mode.PERMISSIVE -> ExprStructPermissive(fields)
                Mode.STRICT -> ExprStructStrict(fields)
                else -> throw IllegalStateException("Unsupported execution mode: $mode")
            }
        }

        override fun visitSubquery(rex: RexSubquery, ctx: Unit): Expression {
            val rel = compile(rex.getRel(), ctx)
            val constructor = compile(rex.getConstructor(), ctx)
            return when (rex.asScalar()) {
                true -> ExprSubquery(rel, constructor)
                else -> ExprSubqueryRow(rel, constructor)
            }
        }

        override fun visitSubqueryComp(rex: RexSubqueryComp, ctx: Unit): Expression {
            TODO("<exists predicate> and <unique predicate>")
        }

        override fun visitSubqueryIn(rex: RexSubqueryIn, ctx: Unit): Expression {
            TODO("<in predicate>")
        }

        override fun visitSubqueryTest(rex: RexSubqueryTest, ctx: Unit): Expression {
            TODO("<exists predicate> and <unique predicate>")
        }

        override fun visitSpread(rex: RexSpread, ctx: Unit): Expression {
            val args = rex.getArgs().map { compile(it, ctx) }.toTypedArray()
            return ExprSpread(args)
        }

        override fun visitTable(rex: RexTable, ctx: Unit): Expression {
            return ExprTable(rex.getTable())
        }

        override fun visitVar(rex: RexVar, ctx: Unit): Expression {
            val depth = rex.getDepth()
            val offset = rex.getOffset()
            return ExprVar(depth, offset)
        }
    }

    /**
     * Some places "catch" an error and return the MISSING value.
     */
    private fun Expression.catch(): Expression = when (mode) {
        Mode.PERMISSIVE -> ExprPermissive(this)
        Mode.STRICT -> this
        else -> throw IllegalStateException("Unsupported execution mode: $mode")
    }
}