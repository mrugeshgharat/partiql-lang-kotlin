package org.partiql.plan.passes.impl

import org.partiql.plan.PlannerSession
import org.partiql.plan.impl.PlannerContext
import org.partiql.plan.ir.Attribute
import org.partiql.plan.ir.PlanNode
import org.partiql.plan.ir.Rel
import org.partiql.plan.ir.Rex
import org.partiql.plan.passes.PlanRewriter
import org.partiql.lang.types.BagType
import org.partiql.lang.types.StaticType
import org.partiql.lang.types.StructType
import org.partiql.plan.PlannerSession2
import org.partiql.plan.impl.PlannerContext2

internal object PlanTyper : PlanRewriter<PlanTyper.Context>() {

    /**
     * Given a [Rex], types the logical plan by adding the output schema to each relational operator.
     *
     * Along with typing, this also validates expressions for typing issues.
     */
    public fun type(node: Rex, ctx: Context): Rex {
        return visitRex(node, ctx) as Rex
    }

    /**
     * Used for maintaining state through the visitors
     */
    public class Context(
        internal val session: PlannerSession,
        internal val plannerCtx: PlannerContext
    )

    public class Context2(
        internal val session: PlannerSession2,
        internal val plannerCtx: PlannerContext2
    )

    override fun visitRexQueryCollection(node: Rex.Query.Collection, ctx: Context): PlanNode {
        if (node.constructor != null) {
            TODO("SELECT VALUE is not supported in the PlanTyper yet.")
        }
        val input = visitRel(node.rel, ctx)
        return node.copy(rel = input)
    }

    override fun visitRelProject(node: Rel.Project, ctx: Context): PlanNode {
        val input = visitRel(node.input, ctx)
        val schema = node.bindings.map { binding ->
            Attribute(binding.name, inferType(binding.value, input, ctx))
        }
        return node.copy(
            input = input,
            common = node.common.copy(
                schema = schema
            )
        )
    }

    override fun visitRelScan(node: Rel.Scan, ctx: Context): PlanNode {
        val type = when (val rex = node.value) {
            is Rex.Query -> TODO("PlanTyper doesn't support nested queries yet")
            else -> RexTyperBase.type(
                rex,
                RexTyperBase.Context(
                    null,
                    ctx.session,
                    ctx.plannerCtx,
                    RexTyperBase.ScopingOrder.GLOBALS_THEN_LEXICAL
                )
            )
        }
        return node.copy(
            common = node.common.copy(schema = convertStaticTypeToSchema(type))
        )
    }

    override fun visitRel(node: Rel, ctx: Context): Rel = super.visitRel(node, ctx) as Rel

    override fun visitRelFilter(node: Rel.Filter, ctx: Context): PlanNode = node.copy(input = visitRel(node.input, ctx))

    override fun visitRelSort(node: Rel.Sort, ctx: Context): PlanNode = node.copy(input = visitRel(node.input, ctx))

    override fun visitRelFetch(node: Rel.Fetch, ctx: Context): PlanNode = node.copy(input = visitRel(node.input, ctx))

    //
    //
    // HELPER METHODS
    //
    //

    private fun inferType(expr: Rex, input: Rel?, ctx: Context): StaticType {
        return RexTyperBase.type(
            expr,
            RexTyperBase.Context(
                input,
                ctx.session,
                ctx.plannerCtx,
                RexTyperBase.ScopingOrder.LEXICAL_THEN_GLOBALS
            )
        )
    }

    private fun convertStaticTypeToSchema(type: StaticType): List<Attribute> {
        val bag = type as BagType
        val struct = bag.elementType as StructType
        return struct.fields.map { field ->
            Attribute(field.key, field.value)
        }
    }
}
