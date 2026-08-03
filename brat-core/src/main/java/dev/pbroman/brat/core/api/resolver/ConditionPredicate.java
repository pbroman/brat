package dev.pbroman.brat.core.api.resolver;

import java.util.Map;

import dev.pbroman.brat.core.exception.BratException;

/**
 * The test behind one condition func: the two operands plus the func's own arguments.
 * <p>
 * Most funcs need only {@code a} and {@code b} and ignore {@code args} — it is the third argument
 * rather than a separate registration so that a func needing one ({@code isCloseTo}'s {@code offset},
 * {@code isBetween}'s {@code min}/{@code max}) is registered exactly like every other, and so that a
 * new argument-taking func never requires a new field on the data model.
 * <p>
 * {@code args} are the func's own arguments, unrelated to the {@code params} interpolation
 * namespace.
 */
@FunctionalInterface
public interface ConditionPredicate {

    /**
     * Tests the condition's operands.
     *
     * @param a the first operand, already interpolated
     * @param b the second operand, already interpolated, or {@code null} for a unary func
     * @param args the func's arguments, already interpolated; never {@code null}, empty when the
     *        author declared none
     * @return whether the condition holds
     * @throws BratException if an argument this func requires is missing or unusable, or if the
     *         arguments hold a key this func does not know
     */
    boolean test(Object a, Object b, Map<String, String> args);
}
