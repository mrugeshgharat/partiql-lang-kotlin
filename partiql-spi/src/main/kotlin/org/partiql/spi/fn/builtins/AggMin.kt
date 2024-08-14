// ktlint-disable filename
@file:Suppress("ClassName")

package org.partiql.spi.fn.builtins

import org.partiql.spi.fn.Agg
import org.partiql.spi.fn.AggSignature
import org.partiql.spi.fn.FnParameter
import org.partiql.spi.fn.builtins.internal.AccumulatorMin
import org.partiql.types.PType

internal object Agg_MIN__INT8__INT8 : Agg {

    override val signature: AggSignature = AggSignature(
        name = "min",
        returns = PType.tinyint(),
        parameters = listOf(
            FnParameter("value", PType.tinyint()),
        ),
        isNullable = true,
        isDecomposable = true
    )

    override fun accumulator(): Agg.Accumulator = AccumulatorMin()
}

internal object Agg_MIN__INT16__INT16 : Agg {

    override val signature: AggSignature = AggSignature(
        name = "min",
        returns = PType.smallint(),
        parameters = listOf(
            FnParameter("value", PType.smallint()),
        ),
        isNullable = true,
        isDecomposable = true
    )

    override fun accumulator(): Agg.Accumulator = AccumulatorMin()
}

internal object Agg_MIN__INT32__INT32 : Agg {

    override val signature: AggSignature = AggSignature(
        name = "min",
        returns = PType.integer(),
        parameters = listOf(
            FnParameter("value", PType.integer()),
        ),
        isNullable = true,
        isDecomposable = true
    )

    override fun accumulator(): Agg.Accumulator = AccumulatorMin()
}

internal object Agg_MIN__INT64__INT64 : Agg {

    override val signature: AggSignature = AggSignature(
        name = "min",
        returns = PType.bigint(),
        parameters = listOf(
            FnParameter("value", PType.bigint()),
        ),
        isNullable = true,
        isDecomposable = true
    )

    override fun accumulator(): Agg.Accumulator = AccumulatorMin()
}

internal object Agg_MIN__INT__INT : Agg {

    override val signature: AggSignature = AggSignature(
        name = "min",
        returns = PType.numeric(),
        parameters = listOf(
            @Suppress("DEPRECATION") FnParameter("value", PType.numeric()),
        ),
        isNullable = true,
        isDecomposable = true
    )

    override fun accumulator(): Agg.Accumulator = AccumulatorMin()
}

internal object Agg_MIN__DECIMAL_ARBITRARY__DECIMAL_ARBITRARY : Agg {

    override val signature: AggSignature = AggSignature(
        name = "min",
        returns = PType.decimal(),
        parameters = listOf(
            @Suppress("DEPRECATION") FnParameter("value", PType.decimal()),
        ),
        isNullable = true,
        isDecomposable = true
    )

    override fun accumulator(): Agg.Accumulator = AccumulatorMin()
}

internal object Agg_MIN__FLOAT32__FLOAT32 : Agg {

    override val signature: AggSignature = AggSignature(
        name = "min",
        returns = PType.real(),
        parameters = listOf(
            FnParameter("value", PType.real()),
        ),
        isNullable = true,
        isDecomposable = true
    )

    override fun accumulator(): Agg.Accumulator = AccumulatorMin()
}

internal object Agg_MIN__FLOAT64__FLOAT64 : Agg {

    override val signature: AggSignature = AggSignature(
        name = "min",
        returns = PType.doublePrecision(),
        parameters = listOf(
            FnParameter("value", PType.doublePrecision()),
        ),
        isNullable = true,
        isDecomposable = true
    )

    override fun accumulator(): Agg.Accumulator = AccumulatorMin()
}

internal object Agg_MIN__ANY__ANY : Agg {

    override val signature: AggSignature = AggSignature(
        name = "min",
        returns = PType.dynamic(),
        parameters = listOf(
            FnParameter("value", PType.dynamic()),
        ),
        isNullable = true,
        isDecomposable = true
    )

    override fun accumulator(): Agg.Accumulator = AccumulatorMin()
}