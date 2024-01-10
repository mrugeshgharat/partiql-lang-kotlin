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
internal object Fn_POSITION__STRING_STRING__INT64 : ConnectorFunction.Scalar {

    override val signature = FunctionSignature.Scalar(
        name = "position",
        returns = INT64,
        parameters = listOf(
            FunctionParameter("probe", STRING),
            FunctionParameter("value", STRING),
        ),
        isNullCall = true,
        isNullable = false,
    )

    override fun invoke(args: Array<PartiQLValue>): PartiQLValue {
        TODO("Function position not implemented")
    }
}

@OptIn(PartiQLValueExperimental::class, ConnectorFunctionExperimental::class)
internal object Fn_POSITION__SYMBOL_SYMBOL__INT64 : ConnectorFunction.Scalar {

    override val signature = FunctionSignature.Scalar(
        name = "position",
        returns = INT64,
        parameters = listOf(
            FunctionParameter("probe", SYMBOL),
            FunctionParameter("value", SYMBOL),
        ),
        isNullCall = true,
        isNullable = false,
    )

    override fun invoke(args: Array<PartiQLValue>): PartiQLValue {
        TODO("Function position not implemented")
    }
}

@OptIn(PartiQLValueExperimental::class, ConnectorFunctionExperimental::class)
internal object Fn_POSITION__CLOB_CLOB__INT64 : ConnectorFunction.Scalar {

    override val signature = FunctionSignature.Scalar(
        name = "position",
        returns = INT64,
        parameters = listOf(
            FunctionParameter("probe", CLOB),
            FunctionParameter("value", CLOB),
        ),
        isNullCall = true,
        isNullable = false,
    )

    override fun invoke(args: Array<PartiQLValue>): PartiQLValue {
        TODO("Function position not implemented")
    }
}
