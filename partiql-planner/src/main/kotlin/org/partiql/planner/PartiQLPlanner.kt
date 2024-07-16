package org.partiql.planner

import org.partiql.ast.Statement
import org.partiql.errors.Problem
import org.partiql.errors.ProblemCallback
import org.partiql.plan.PartiQLPlan
import org.partiql.planner.catalog.Catalogs
import org.partiql.planner.catalog.Session
import org.partiql.planner.internal.PartiQLPlannerDefault
import org.partiql.planner.internal.PlannerFlag

/**
 * PartiQLPlanner is responsible for transforming an AST into PartiQL's logical query plan.
 */
public interface PartiQLPlanner {

    /**
     * Transform an AST to a [PartiQLPlan].
     *
     * @param statement
     * @param session
     * @param onProblem
     * @return
     */
    public fun plan(statement: Statement, session: Session, onProblem: ProblemCallback = {}): Result

    /**
     * Planner result along with any warnings.
     *
     * @property plan
     */
    public class Result(
        public val plan: PartiQLPlan,
        public val problems: List<Problem>,
    )

    public companion object {

        @JvmStatic
        public fun builder(): Builder = Builder()

        @JvmStatic
        public fun default(): PartiQLPlanner = Builder().build()
    }

    public class Builder {

        private val flags: MutableSet<PlannerFlag> = mutableSetOf()
        private var catalogs: Catalogs? = null

        /**
         * Build the builder, return an implementation of a [PartiQLPlanner].
         *
         * @return
         */
        public fun build(): PartiQLPlanner {
            assert(catalogs != null) { "The `catalogs` field cannot be null, set with .catalgos(...)"}
            return PartiQLPlannerDefault(catalogs!!, flags)
        }

        /**
         * Adds a catalog provider to this planner builder.
         */
        public fun catalogs(catalogs: Catalogs): Builder = this.apply { this.catalogs = catalogs }

        /**
         * Java style method for setting the planner to signal mode
         */
        public fun signalMode(): Builder = this.apply {
            this.flags.add(PlannerFlag.SIGNAL_MODE)
        }
    }
}
