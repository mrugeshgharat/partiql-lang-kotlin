package org.partiql.eval.compiler;

import org.partiql.eval.Expr;
import org.partiql.plan.Operator;

import java.util.Collections;
import java.util.function.Predicate;

/**
 * Strategy converts a logical operator into a physical operator. The compiler uses the list of operands
 * to determine a subtree match, then invokes `apply` to
 */
public abstract class Strategy {

    private final Pattern operand;

    protected Strategy(Pattern operand) {
        this.operand = operand;
    }

    /**
     * Applies the strategy to a logical plan operator and returns the physical operation (expr).
     *
     * @param operator the logical operator to be converted
     * @return the physical operation
     */
    public abstract Expr apply(Operator operator);

    // -- PATTERN CONSTRUCTORS

    /**
     * Create an operand that matches the given class.
     */
    public static Pattern pattern(Class<? extends Operator> clazz) {
        return new Pattern(clazz);
    }

    /**
     * Create an operand that matches the given class and predicate.
     */
    public static Pattern pattern(Class<? extends Operator> clazz, Predicate<Operator> predicate) {
        return new Pattern(clazz, predicate, Collections.emptyList());
    }
}
