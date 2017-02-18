/*
 * Copyright 2016 Amazon.com, Inc. or its affiliates.  All rights reserved.
 */

package com.amazon.ionsql

import com.amazon.ion.*
import org.junit.Test
import java.math.BigDecimal

class IonExprValueTest : Base() {
    fun over(expectedIonValue: IonValue,
             block: AssertExprValue.() -> Unit = { }) =
        AssertExprValue(IonExprValue(expectedIonValue))
            .apply {
                assertIonValue(expectedIonValue)

                when (expectedIonValue) {
                    is IonContainer -> expectedIonValue.map { it }
                    else -> assertIterator(listOf(expectedIonValue))
                }
            }
            .run(block)

    fun over(text: String,
             transform: IonValue.() -> IonValue = { this },
             block: AssertExprValue.() -> Unit = { }) = over(
        literal(text).transform(),
        block
    )

    @Test
    fun scalarInt() = over("5")

    @Test
    fun scalarString() = over("\"hello\"")

    @Test
    fun list() = over("[1, 2, 3]")

    @Test
    fun struct() = over("{a: 1, b: 3.14, c: \"hello\"}") {
        assertBinding("a") { ion.newInt(1) == ionValue }
        assertBinding("b") { ion.newDecimal(BigDecimal("3.14")) == ionValue }
        assertBinding("c") { ion.newString("hello") == ionValue }
        assertNoBinding("d")
    }

    @Test
    fun listChild() = over("[1, 2, 3]", { this[0] })

    @Test
    fun structChild() = over("{a: 1, b: 2, c: 3}", { this["a"]!! })
}
