package org.partiql.planner.internal.typer

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.partiql.planner.catalog.Routine
import org.partiql.planner.internal.FnMatch
import org.partiql.planner.internal.FnResolver
import org.partiql.planner.internal.typer.PlanTyper.Companion.toCType
import org.partiql.types.PType

/**
 * As far as testing is concerned, we can stub out all value related things.
 * We may be able to pretty-print with string equals to also simplify things.
 * Only the "types" of expressions matter, we ignore the underlying ops.
 */
class FnResolverTest {

    @Test
    fun sanity() {
        // 1 + 1.0 -> 2.0
        val variants = listOf(
            Routine.scalar(
                name = "plus",
                parameters = listOf(
                    Routine.Parameter("arg-0", PType.Kind.DOUBLE_PRECISION),
                    Routine.Parameter("arg-1", PType.Kind.DOUBLE_PRECISION),
                ),
                returnType = PType.typeDoublePrecision(),
            )
        )
        val args = listOf(PType.typeInt().toCType(), PType.typeDoublePrecision().toCType())
        val expectedImplicitCasts = listOf(true, false)
        val case = Case.Success(variants, args, expectedImplicitCasts)
        case.assert()
    }

    @Test
    fun split() {
        val variants = listOf(
            Routine.scalar(
                name = "split",
                parameters = listOf(
                    Routine.Parameter("value", PType.Kind.STRING),
                    Routine.Parameter("delimiter", PType.Kind.STRING),
                ),
                returnType = PType.typeList(),
                // isNullable = false,
            )
        )
        val args = listOf(PType.typeString().toCType(), PType.typeString().toCType())
        val expectedImplicitCasts = listOf(false, false)
        val case = Case.Success(variants, args, expectedImplicitCasts)
        case.assert()
    }

    private sealed class Case {

        abstract fun assert()

        class Success(
            private val variants: List<Routine.Scalar>,
            private val inputs: List<CompilerType>,
            private val expectedImplicitCast: List<Boolean>,
        ) : Case() {

            /**
             * Assert we match the function, and the appropriate implicit CASTs were returned.
             *
             * TODO actually look into what the CAST functions are.
             */
            override fun assert() {
                val match = FnResolver.resolve(variants, inputs)
                val diffs = mutableListOf<String>()
                val message = buildString {
                    appendLine("Given arguments did not match any function signature")
                    appendLine("Input: (${inputs.joinToString()}})")
                }
                if (match == null) {
                    fail { message }
                }
                if (match !is FnMatch.Static) {
                    fail { "Dynamic match, expected static match: $message" }
                }

                if (match.mapping.size != expectedImplicitCast.size) {
                    fail { "Mapping size does not match expected mapping size: $message" }
                }

                // compare args
                for (i in match.mapping.indices) {
                    val m = match.mapping[i]
                    val shouldCast = expectedImplicitCast[i]
                    val diff: String? = when {
                        m == null && shouldCast -> "Arg[$i] is missing an implicit CAST"
                        m != null && !shouldCast -> "Arg[$i] had implicit CAST but should not"
                        else -> null
                    }
                    if (diff != null) diffs.add(diff)
                }
                // pretty-print some debug info
                if (diffs.isNotEmpty()) {
                    fail {
                        buildString {
                            appendLine(message)
                            diffs.forEach { appendLine(it) }
                        }
                    }
                }
            }
        }
    }
}
