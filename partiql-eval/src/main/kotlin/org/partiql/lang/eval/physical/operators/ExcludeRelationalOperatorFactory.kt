package org.partiql.lang.eval.physical.operators

import com.amazon.ionelement.api.emptyMetaContainer
import org.partiql.lang.domains.PartiqlPhysical
import org.partiql.lang.eval.ExprValue
import org.partiql.lang.eval.ExprValueType
import org.partiql.lang.eval.internal.BagExprValue
import org.partiql.lang.eval.internal.ListExprValue
import org.partiql.lang.eval.internal.NamedExprValue
import org.partiql.lang.eval.internal.SexpExprValue
import org.partiql.lang.eval.internal.StructExprValue
import org.partiql.lang.eval.internal.exclude.CompiledExcludeExpr
import org.partiql.lang.eval.internal.exclude.ExcludeNode
import org.partiql.lang.eval.internal.ext.name
import org.partiql.lang.eval.internal.ext.namedValue
import org.partiql.lang.eval.internal.newSequenceExprValue
import org.partiql.lang.eval.longValue
import org.partiql.lang.eval.physical.EvaluatorState
import org.partiql.lang.eval.relation.RelationIterator
import org.partiql.lang.eval.relation.relation
import org.partiql.lang.eval.stringValue
import org.partiql.lang.planner.transforms.DEFAULT_IMPL_NAME

/**
 * Provides an implementation of the [PartiqlPhysical.Bexpr.ExcludeClause] operator.
 *
 * @constructor
 *
 * @param name
 */
internal abstract class ExcludeRelationalOperatorFactory(name: String) : RelationalOperatorFactory {
    final override val key = RelationalOperatorFactoryKey(RelationalOperatorKind.EXCLUDE, name)

    /**
     * Creates a [RelationExpression] instance for [PartiqlPhysical.Bexpr.ExcludeClause].
     *
     * @param impl
     * @param sourceBexpr
     * @param compiledExcludeExprs
     * @return
     */
    abstract fun create(
        impl: PartiqlPhysical.Impl,
        sourceBexpr: RelationExpression,
        compiledExcludeExprs: List<CompiledExcludeExpr>
    ): RelationExpression
}

internal object ExcludeRelationalOperatorFactoryDefault : ExcludeRelationalOperatorFactory(DEFAULT_IMPL_NAME) {
    override fun create(
        impl: PartiqlPhysical.Impl,
        sourceBexpr: RelationExpression,
        compiledExcludeExprs: List<CompiledExcludeExpr>
    ): RelationExpression = ExcludeOperator(
        input = sourceBexpr,
        compiledExcludeExprs = compiledExcludeExprs
    )
}

private fun excludeBindings(
    curRegisters: Array<ExprValue>,
    exclusions: CompiledExcludeExpr
): Array<ExprValue> {
    val curExprValue = curRegisters.getOrNull(exclusions.root)
    return if (curExprValue != null) {
        val newExprValue = excludeExprValue(curExprValue, exclusions)
        curRegisters[exclusions.root] = newExprValue
        curRegisters
    } else {
        curRegisters
    }
}

private fun excludeStructExprValue(
    structExprValue: StructExprValue,
    exclusions: ExcludeNode
): ExprValue {
    val toRemove = exclusions.leaves.map { leaf -> leaf.step }
    val otherSteps = exclusions.branches
    if (toRemove.any { it is PartiqlPhysical.ExcludeStep.ExcludeTupleWildcard }) {
        // tuple wildcard at current level. return empty struct
        return StructExprValue(
            sequence = emptySequence(),
            ordering = structExprValue.ordering
        )
    }
    val attrsToRemove = toRemove.filterIsInstance<PartiqlPhysical.ExcludeStep.ExcludeTupleAttr>()
        .map { it.attr.name.text }
        .toSet()
    val sequenceWithRemoved = structExprValue.mapNotNull { structField ->
        if (attrsToRemove.contains(structField.name?.stringValue())) {
            null
        } else {
            structField as NamedExprValue
        }
    }
    val finalSequence = sequenceWithRemoved.map { structField ->
        var expr = structField.value
        val name = structField.name
        // apply case-sensitive tuple attr exclusions
        val structFieldCaseSensitiveKey = PartiqlPhysical.build {
            excludeTupleAttr(
                identifier(
                    name.stringValue(),
                    caseSensitive()
                )
            )
        }
        otherSteps.find {
            it.step == structFieldCaseSensitiveKey
        }?.let {
            expr = excludeExprValue(expr, it)
        }
        // apply case-insensitive tuple attr exclusions
        val structFieldCaseInsensitiveKey = PartiqlPhysical.build {
            excludeTupleAttr(
                identifier(
                    name.stringValue(),
                    caseInsensitive()
                )
            )
        }
        otherSteps.find {
            it.step == structFieldCaseInsensitiveKey
        }?.let {
            expr = excludeExprValue(expr, it)
        }
        // apply tuple wildcard exclusions
        val tupleWildcardKey = PartiqlPhysical.build { excludeTupleWildcard(emptyMetaContainer()) }
        otherSteps.find {
            it.step == tupleWildcardKey
        }?.let {
            expr = excludeExprValue(expr, it)
        }
        expr.namedValue(name)
    }.asSequence()
    return StructExprValue(sequence = finalSequence, ordering = structExprValue.ordering)
}

private fun excludeCollectionExprValue(
    initialExprValue: ExprValue,
    exprValueType: ExprValueType,
    exclusions: ExcludeNode
): ExprValue {
    val toRemove = exclusions.leaves.map { leaf -> leaf.step }
    val otherSteps = exclusions.branches
    if (toRemove.any { it is PartiqlPhysical.ExcludeStep.ExcludeCollectionWildcard }) {
        // collection wildcard at current level. return empty collection
        return newSequenceExprValue(exprValueType, emptySequence())
    } else {
        val indexesToRemove = toRemove.filterIsInstance<PartiqlPhysical.ExcludeStep.ExcludeCollectionIndex>()
            .map { it.index.value }
            .toSet()
        val sequenceWithRemoved = initialExprValue.mapNotNull { element ->
            if (indexesToRemove.contains(element.name?.longValue())) {
                null
            } else {
                element
            }
        }.asSequence()
        val finalSequence = sequenceWithRemoved.map { element ->
            var expr = element
            if (initialExprValue is ListExprValue || initialExprValue is SexpExprValue) {
                element as NamedExprValue
                val index = element.name.longValue()
                // apply collection index exclusions for lists and sexps
                val elementKey = PartiqlPhysical.build {
                    excludeCollectionIndex(
                        index
                    )
                }
                otherSteps.find {
                    it.step == elementKey
                }?.let {
                    expr = excludeExprValue(element.value, it)
                }
            }
            // apply collection wildcard exclusions for lists, bags, and sexps
            val collectionWildcardKey = PartiqlPhysical.build { excludeCollectionWildcard(emptyMetaContainer()) }
            otherSteps.find {
                it.step == collectionWildcardKey
            }?.let {
                expr = excludeExprValue(expr, it)
            }
            expr
        }
        return newSequenceExprValue(exprValueType, finalSequence)
    }
}

private fun excludeExprValue(initialExprValue: ExprValue, exclusions: ExcludeNode): ExprValue {
    return when (initialExprValue) {
        is NamedExprValue -> excludeExprValue(initialExprValue.value, exclusions)
        is StructExprValue -> excludeStructExprValue(initialExprValue, exclusions)
        is ListExprValue -> excludeCollectionExprValue(initialExprValue, ExprValueType.LIST, exclusions)
        is BagExprValue -> excludeCollectionExprValue(initialExprValue, ExprValueType.BAG, exclusions)
        is SexpExprValue -> excludeCollectionExprValue(initialExprValue, ExprValueType.SEXP, exclusions)
        else -> {
            initialExprValue
        }
    }
}

internal class ExcludeOperator(
    val input: RelationExpression,
    val compiledExcludeExprs: List<CompiledExcludeExpr>
) : RelationExpression {
    override fun evaluate(state: EvaluatorState): RelationIterator {
        val rows = input.evaluate(state)

        return relation(rows.relType) {
            while (rows.nextRow()) {
                val newRegisters = compiledExcludeExprs.fold(state.registers) { curRegisters, expr ->
                    excludeBindings(curRegisters, expr)
                }
                state.load(newRegisters)
                yield()
            }
        }
    }
}

/**
 * Creates a list of compiled exclude expressions with each index of the resulting list corresponding to a different
 * exclude path root.
 */
internal fun compileExcludeClause(excludeClause: PartiqlPhysical.Bexpr.ExcludeClause): List<CompiledExcludeExpr> {
    val excludeExprs = excludeClause.exprs
    val compiledExcludeExprs = excludeExprs
        .groupBy { it.root }
        .map { (root, exclusions) ->
            exclusions.fold(CompiledExcludeExpr.empty(root.value.toInt())) { acc, exclusion ->
                acc.addSteps(exclusion.steps)
                acc
            }
        }
    return compiledExcludeExprs
}