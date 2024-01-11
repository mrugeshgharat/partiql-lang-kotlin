package org.partiql.spi.fn

/**
 * Utility class for an optimized function lookup data structure. Right now this is read only.
 */
@OptIn(FnExperimental::class)
public interface FnIndex {

    /**
     * Search for all functions matching the normalized path.
     *
     * @param path
     * @return
     */
    public fun list(path: List<String>): List<Fn>

    /**
     * Lookup a function by its specific name.
     *
     * @param specific
     * @return
     */
    public fun get(path: List<String>, specific: String): Fn?

    public class Builder {

        /**
         * A catalog's builtins exposed via INFORMATION_SCHEMA.
         */
        private val builtins: MutableList<Fn> = mutableListOf()

        public fun add(fn: Fn): Builder = this.apply {
            builtins.add(fn)
        }

        public fun addAll(fns: List<Fn>): Builder = this.apply {
            builtins.addAll(fns)
        }

        /**
         * TODO
         *
         * @return
         */
        public fun build(): FnIndex {
            return FnIndexMap(emptyMap())
        }
    }

    public companion object {

        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}
