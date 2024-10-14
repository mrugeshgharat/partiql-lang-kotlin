package org.partiql.plan.rex

import org.partiql.plan.Visitor
import org.partiql.spi.value.Datum

/**
 * TODO DOCUMENTATION
 */
public interface RexLit : Rex {

    public fun getValue(): Datum

    override fun getChildren(): Collection<Rex> = emptyList()

    override fun <R, C> accept(visitor: Visitor<R, C>, ctx: C): R = visitor.visitLit(this, ctx)
}

internal class RexLitImpl(value: Datum) : RexLit {

    // DO NOT USE FINAL
    private var _value = value
    private var _type = RexType(_value.type)

    override fun getValue(): Datum = _value

    override fun getType(): RexType = _type

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RexLit) return false
        if (_value != other.getValue()) return false
        return true
    }

    override fun hashCode(): Int = _value.hashCode()
}
