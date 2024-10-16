package org.partiql.eval.internal.operator.rel

import org.partiql.eval.internal.Row
import org.partiql.eval.internal.helpers.RecordUtility.coerceMissing
import org.partiql.eval.internal.operator.Operator
import java.util.TreeSet

/**
 * Non-communicative, this performs better when [lhs] is larger than [rhs].
 *
 * @property lhs
 * @property rhs
 */
internal class RelOpExceptDistinct(
    private val lhs: Operator.Relation,
    private val rhs: Operator.Relation,
) : RelOpPeeking() {

    private var seen = TreeSet(DatumArrayComparator)
    private var init: Boolean = false

    override fun openPeeking() {
        lhs.open()
        rhs.open()
        init = false
    }

    override fun peek(): Row? {
        if (!init) {
            seed()
        }
        for (row in lhs) {
            row.values.coerceMissing()
            if (!seen.contains(row.values)) {
                return Row(row.values)
            }
        }
        return null
    }

    override fun closePeeking() {
        lhs.close()
        rhs.close()
        seen.clear()
    }

    /**
     * Read the entire right-hand-side into our search structure.
     */
    private fun seed() {
        init = true
        for (row in rhs) {
            row.values.coerceMissing()
            seen.add(row.values)
        }
    }
}
