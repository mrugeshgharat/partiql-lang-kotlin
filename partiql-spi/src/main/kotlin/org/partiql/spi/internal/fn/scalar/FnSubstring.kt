// ktlint-disable filename
@file:Suppress("ClassName")

package org.partiql.spi.internal.fn.scalar

import org.partiql.spi.connector.ConnectorFunction
import org.partiql.spi.connector.ConnectorFunctionExperimental
import org.partiql.types.function.FunctionParameter
import org.partiql.types.function.FunctionSignature
import org.partiql.value.PartiQLValue
import org.partiql.value.PartiQLValueExperimental
import org.partiql.value.PartiQLValueType.CLOB
import org.partiql.value.PartiQLValueType.INT64
import org.partiql.value.PartiQLValueType.STRING
import org.partiql.value.PartiQLValueType.SYMBOL

@OptIn(PartiQLValueExperimental::class, ConnectorFunctionExperimental::class)
internal object Fn_SUBSTRING__STRING_INT64__STRING : ConnectorFunction.Scalar {

    override val signature = FunctionSignature.Scalar(
        name = "substring",
        returns = STRING,
        parameters = listOf(
            FunctionParameter("value", STRING),
            FunctionParameter("start", INT64),
        ),
        isNullCall = true,
        isNullable = false,
    )

    override fun invoke(args: Array<PartiQLValue>): PartiQLValue {
        TODO("Function substring not implemented")
    }
}

@OptIn(PartiQLValueExperimental::class, ConnectorFunctionExperimental::class)
internal object Fn_SUBSTRING__STRING_INT64_INT64__STRING : ConnectorFunction.Scalar {

    override val signature = FunctionSignature.Scalar(
        name = "substring",
        returns = STRING,
        parameters = listOf(
            FunctionParameter("value", STRING),
            FunctionParameter("start", INT64),
            FunctionParameter("end", INT64),
        ),
        isNullCall = true,
        isNullable = false,
    )

    override fun invoke(args: Array<PartiQLValue>): PartiQLValue {
        TODO("Function substring not implemented")
    }
}

@OptIn(PartiQLValueExperimental::class, ConnectorFunctionExperimental::class)
internal object Fn_SUBSTRING__SYMBOL_INT64__SYMBOL : ConnectorFunction.Scalar {

    override val signature = FunctionSignature.Scalar(
        name = "substring",
        returns = SYMBOL,
        parameters = listOf(
            FunctionParameter("value", SYMBOL),
            FunctionParameter("start", INT64),
        ),
        isNullCall = true,
        isNullable = false,
    )

    override fun invoke(args: Array<PartiQLValue>): PartiQLValue {
        TODO("Function substring not implemented")
    }
}

@OptIn(PartiQLValueExperimental::class, ConnectorFunctionExperimental::class)
internal object Fn_SUBSTRING__SYMBOL_INT64_INT64__SYMBOL : ConnectorFunction.Scalar {

    override val signature = FunctionSignature.Scalar(
        name = "substring",
        returns = SYMBOL,
        parameters = listOf(
            FunctionParameter("value", SYMBOL),
            FunctionParameter("start", INT64),
            FunctionParameter("end", INT64),
        ),
        isNullCall = true,
        isNullable = false,
    )

    override fun invoke(args: Array<PartiQLValue>): PartiQLValue {
        TODO("Function substring not implemented")
    }
}

@OptIn(PartiQLValueExperimental::class, ConnectorFunctionExperimental::class)
internal object Fn_SUBSTRING__CLOB_INT64__CLOB : ConnectorFunction.Scalar {

    override val signature = FunctionSignature.Scalar(
        name = "substring",
        returns = CLOB,
        parameters = listOf(
            FunctionParameter("value", CLOB),
            FunctionParameter("start", INT64),
        ),
        isNullCall = true,
        isNullable = false,
    )

    override fun invoke(args: Array<PartiQLValue>): PartiQLValue {
        TODO("Function substring not implemented")
    }
}

@OptIn(PartiQLValueExperimental::class, ConnectorFunctionExperimental::class)
internal object Fn_SUBSTRING__CLOB_INT64_INT64__CLOB : ConnectorFunction.Scalar {

    override val signature = FunctionSignature.Scalar(
        name = "substring",
        returns = CLOB,
        parameters = listOf(
            FunctionParameter("value", CLOB),
            FunctionParameter("start", INT64),
            FunctionParameter("end", INT64),
        ),
        isNullCall = true,
        isNullable = false,
    )

    override fun invoke(args: Array<PartiQLValue>): PartiQLValue {
        TODO("Function substring not implemented")
    }
}
