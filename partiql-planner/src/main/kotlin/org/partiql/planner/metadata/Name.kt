package org.partiql.planner.metadata

import java.util.Spliterator
import java.util.function.Consumer

/**
 * Thin wrapper over a list of strings.
 *
 * @property steps
 */
public data class Name(public val steps: List<String>) : Iterable<String> {

    public companion object {

        @JvmStatic
        public fun of(vararg steps: String): Name = Name(steps.toList())
    }

    public operator fun get(index: Int): String = steps[index]

    override fun forEach(action: Consumer<in String>?): Unit = steps.forEach(action)

    override fun iterator(): Iterator<String> = steps.iterator()

    override fun spliterator(): Spliterator<String> = steps.spliterator()
}