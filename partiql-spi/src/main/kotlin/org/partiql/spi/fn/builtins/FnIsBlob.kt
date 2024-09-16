// ktlint-disable filename
@file:Suppress("ClassName")

package org.partiql.spi.fn.builtins

import org.partiql.eval.value.Datum
import org.partiql.spi.fn.FnSignature
import org.partiql.spi.fn.Function
import org.partiql.spi.fn.Parameter
import org.partiql.types.PType

internal object Fn_IS_BLOB__ANY__BOOL : Function {

    override val signature = FnSignature(
        name = "is_blob",
        returns = PType.bool(),
        parameters = listOf(Parameter("value", PType.dynamic())),
        isNullCall = true,
        isNullable = false,
    )

    override fun invoke(args: Array<Datum>): Datum {
        return Datum.bool(args[0].type.kind == PType.Kind.BLOB)
    }
}
