package org.partiql.planner.internal

import org.partiql.ast.Identifier
import org.partiql.planner.catalog.Catalog
import org.partiql.planner.catalog.Name
import org.partiql.planner.catalog.Session
import org.partiql.planner.internal.casts.CastTable
import org.partiql.planner.internal.ir.Rel
import org.partiql.planner.internal.ir.Rex
import org.partiql.planner.internal.ir.rexOpCastResolved
import org.partiql.planner.internal.typer.CompilerType

/**
 * [Env] is similar to the database type environment from the PartiQL Specification. This includes resolution of
 * database binding values and scoped functions.
 *
 * See TypeEnv for the variables type environment.
 *
 * TODO: function resolution between scalar functions and aggregations.
 *
 * @property session
 */
internal class Env(
    private val catalog: Catalog,
    private val session: Session,
) {

    /**
     *
     * TODO handle missing table error.
     *
     * Convert any remaining binding names (tail) to a path expression.
     */
    fun getTable(identifier: Identifier): Rex? = when (identifier) {
        is Identifier.Qualified -> getTable(identifier)
        is Identifier.Symbol -> getTable(identifier)
    }

    /**
     *
     *
     *
     * @param identifier
     * @return
     */
    fun getTable(identifier: Identifier.Symbol): Rex? {
        val name = Name.of(identifier)
        val table = catalog.getTable(session, name) ?: return null
        return null
    }

    /**
     * TODO
     *
     * Convert any remaining binding names (tail) to a path expression.
     */
    fun getTable(identifier: Identifier.Qualified): Rex? {
        return null
    }

    /**
     * This function looks up a global [Identitifier], returning a global reference expression.
     *
     * Convert any remaining binding names (tail) to a path expression.
     *
     * @param path
     * @return
     */
    // fun resolveObj(path: Identifier): Rex? {
    //
    //     val item = objects.lookup(path) ?: return null
    //     // Create an internal typed reference
    //     val ref = refObj(
    //         catalog = item.catalog,
    //         path = item.handle.path.steps,
    //         type = CompilerType(item.handle.entity.getPType()),
    //     )
    //     // Rewrite as a path expression.
    //     val root = rex(ref.type, rexOpVarGlobal(ref))
    //     val depth = calculateMatched(path, item.input, ref.path)
    //     val tail = path.steps.drop(depth)
    //     return if (tail.isEmpty()) root else root.toPath(tail)
    // }

    fun resolveFn(path: Identifier, args: List<Rex>): Rex? {
        // val item = fns.lookup(path) ?: return null
        // // Invoke FnResolver to determine if we made a match
        // val variants = item.handle.entity.getVariants()
        // val match = FnResolver.resolve(variants, args.map { it.type })
        // // If Type mismatch, then we return a missingOp whose trace is all possible candidates.
        // if (match == null) {
        //     val candidates = variants.map { fnSignature ->
        //         rexOpCallDynamicCandidate(
        //             fn = refFn(
        //                 item.catalog,
        //                 path = item.handle.path.steps,
        //                 signature = fnSignature
        //             ),
        //             coercions = emptyList()
        //         )
        //     }
        //     return ProblemGenerator.missingRex(
        //         rexOpCallDynamic(args, candidates),
        //         ProblemGenerator.incompatibleTypesForOp(path.normalized.joinToString("."), args.map { it.type })
        //     )
        // }
        // return when (match) {
        //     is FnMatch.Dynamic -> {
        //         val candidates = match.candidates.map {
        //             // Create an internal typed reference for every candidate
        //             rexOpCallDynamicCandidate(
        //                 fn = refFn(
        //                     catalog = item.catalog,
        //                     path = item.handle.path.steps,
        //                     signature = it.signature,
        //                 ),
        //                 coercions = it.mapping.toList(),
        //             )
        //         }
        //         // Rewrite as a dynamic call to be typed by PlanTyper
        //         Rex(CompilerType(PType.typeDynamic()), Rex.Op.Call.Dynamic(args, candidates))
        //     }
        //     is FnMatch.Static -> {
        //         // Create an internal typed reference
        //         val ref = refFn(
        //             catalog = item.catalog,
        //             path = item.handle.path.steps,
        //             signature = match.signature,
        //         )
        //         // Apply the coercions as explicit casts
        //         val coercions: List<Rex> = args.mapIndexed { i, arg ->
        //             when (val cast = match.mapping[i]) {
        //                 null -> arg
        //                 else -> Rex(CompilerType(PType.typeDynamic()), Rex.Op.Cast.Resolved(cast, arg))
        //             }
        //         }
        //         // Rewrite as a static call to be typed by PlanTyper
        //         Rex(CompilerType(PType.typeDynamic()), Rex.Op.Call.Static(ref, coercions))
        //     }
        // }
        TODO("resolveFn")
    }

    fun resolveAgg(name: String, setQuantifier: Rel.Op.Aggregate.SetQuantifier, args: List<Rex>): Rel.Op.Aggregate.Call.Resolved? {
        // TODO: Eventually, do we want to support sensitive lookup? With a path?
        TODO("resolveAgg")
        // val path = Identifier(listOf(BindingName(name, BindingCase.INSENSITIVE)))
        // val item = aggs.lookup(path) ?: return null
        // val candidates = item.handle.entity.getVariants()
        // val parameters = args.mapIndexed { i, arg -> arg.type }
        // val match = match(candidates, parameters) ?: return null
        // val agg = match.first
        // val mapping = match.second
        // // Create an internal typed reference
        // val ref = refAgg(item.catalog, item.handle.path.steps, agg)
        // // Apply the coercions as explicit casts
        // val coercions: List<Rex> = args.mapIndexed { i, arg ->
        //     when (val cast = mapping[i]) {
        //         null -> arg
        //         else -> rex(cast.target, rexOpCastResolved(cast, arg))
        //     }
        // }
        // return relOpAggregateCallResolved(ref, setQuantifier, coercions)
    }

    fun resolveCast(input: Rex, target: CompilerType): Rex.Op.Cast.Resolved? {
        val operand = input.type
        val cast = CastTable.partiql.get(operand, target) ?: return null
        return rexOpCastResolved(cast, input)
    }

    // -----------------------
    //  Helpers
    // -----------------------

    /**
     * Logic for determining how many BindingNames were “matched” by the ConnectorMetadata
     *
     * Assume:
     * - steps_matched = user_input_path_size - path_steps_not_found_size
     * - path_steps_not_found_size = catalog_path_sent_to_spi_size - actual_catalog_absolute_path_size
     *
     * Therefore, we present the equation to [calculateMatched]:
     * - steps_matched = user_input_path_size - (catalog_path_sent_to_spi_size - actual_catalog_absolute_path_size)
     *                 = user_input_path_size + actual_catalog_absolute_path_size - catalog_path_sent_to_spi_size
     *
     * For example:
     *
     * Assume we are in some catalog, C, in some schema, S. There is a tuple, T, with attribute, A1. Assume A1 is of type
     * tuple with an attribute A2.
     * If our query references `T.A1.A2`, we will eventually ask SPI (connector C) for `S.T.A1.A2`. In this scenario:
     * - The original user input was `T.A1.A2` (length 3)
     * - The absolute path returned from SPI will be `S.T` (length 2)
     * - The path we eventually sent to SPI to resolve was `S.T.A1.A2` (length 4)
     *
     * So, we can now use [calculateMatched] to determine how many were actually matched from the user input. Using the
     * equation from above:
     *
     * - steps_matched = len(user input) + len(absolute catalog path) - len(path sent to SPI)
     * = len([userInputPath]) + len([actualAbsolutePath]) - len([pathSentToConnector])
     * = 3 + 2 - 4
     * = 5 - 4
     * = 1
     *
     *
     * Therefore, in this example we have determined that from the original input (`T.A1.A2`) `T` is the value matched in the
     * database environment.
     */
    // private fun calculateMatched(
    //     userInputPath: Identitifier,
    //     pathSentToConnector: Identitifier,
    //     actualAbsolutePath: List<String>,
    // ): Int {
    //     return userInputPath.steps.size + actualAbsolutePath.size - pathSentToConnector.steps.size
    // }
    //
    // private fun match(candidates: List<AggSignature>, args: List<PType>): Pair<AggSignature, Array<Ref.Cast?>>? {
    //     // 1. Check for an exact match
    //     for (candidate in candidates) {
    //         if (candidate.matches(args)) {
    //             return candidate to arrayOfNulls(args.size)
    //         }
    //     }
    //     // 2. Look for best match.
    //     var match: Pair<AggSignature, Array<Ref.Cast?>>? = null
    //     for (candidate in candidates) {
    //         val m = candidate.match(args) ?: continue
    //         // TODO AggMatch comparison
    //         // if (match != null && m.exact < match.exact) {
    //         //     // already had a better match.
    //         //     continue
    //         // }
    //         match = m
    //     }
    //     // 3. Return best match or null
    //     return match
    // }
    //
    // /**
    //  * Check if this function accepts the exact input argument types. Assume same arity.
    //  */
    // private fun AggSignature.matches(args: List<PType>): Boolean {
    //     for (i in args.indices) {
    //         val a = args[i]
    //         val p = parameters[i]
    //         if (p.type.kind != Kind.DYNAMIC && a != p.type) return false
    //     }
    //     return true
    // }
    //
    // /**
    //  * Attempt to match arguments to the parameters; return the implicit casts if necessary.
    //  *
    //  * @param args
    //  * @return
    //  */
    // private fun AggSignature.match(args: List<PType>): Pair<AggSignature, Array<Ref.Cast?>>? {
    //     val mapping = arrayOfNulls<Ref.Cast?>(args.size)
    //     for (i in args.indices) {
    //         val arg = args[i]
    //         val p = parameters[i]
    //         when {
    //             // 1. Exact match
    //             arg == p.type -> continue
    //             // 2. Match ANY, no coercion needed
    //             p.type.kind == Kind.DYNAMIC -> continue
    //             // 3. Check for a coercion
    //             else -> when (val coercion = Coercions.get(arg, p.type)) {
    //                 null -> return null // short-circuit
    //                 else -> mapping[i] = coercion
    //             }
    //         }
    //     }
    //     return this to mapping
    // }
}
