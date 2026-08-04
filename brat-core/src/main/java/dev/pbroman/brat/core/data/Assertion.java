package dev.pbroman.brat.core.data;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * An Assertion extends a {@link Condition} with a message on fail (i.e. when the condition evaluates to false),
 * a (possibly empty) list of chained conditions, and the severity of a failure.
 * <p>
 * The severity defaults to {@link AssertionSeverity#FAIL} and is not settable through any constructor:
 * declaring it is optional for a suite author, and the constructors are already numerous.
 */
@Getter
@Setter
public class Assertion extends Condition {

    private final List<ChainedCondition> chain;
    private final String message;
    private AssertionSeverity severity = AssertionSeverity.FAIL;

    /**
     * Constructs a fully specified assertion. The other constructors default the arguments they
     * omit: {@code chain} to an empty list, {@code message} and {@code b} to {@code null}. Severity
     * is not a constructor argument at all — it defaults to {@link AssertionSeverity#FAIL} and is
     * set afterwards where an author declares it.
     *
     * @param func the condition function
     * @param a the first operand
     * @param b the second operand, or {@code null} for a func that needs none
     * @param chain further conditions on the same {@code a}, possibly empty
     * @param message the message to report if the condition fails, or {@code null}
     */
    public Assertion(String func, Object a, Object b, List<ChainedCondition> chain, String message) {
        super(func, a, b);
        this.chain = chain;
        this.message = message;
    }

    /**
     * Constructs an assertion with no message.
     *
     * @param func the condition function
     * @param a the first operand
     * @param b the second operand, or {@code null}
     * @param chain further conditions on the same {@code a}
     */
    public Assertion(String func, Object a, Object b, List<ChainedCondition> chain) {
        this(func, a, b, chain, null);
    }

    /**
     * Constructs an assertion with no chain.
     *
     * @param func the condition function
     * @param a the first operand
     * @param b the second operand, or {@code null}
     * @param message the message to report if the condition fails
     */
    public Assertion(String func, Object a, Object b, String message) {
        this(func, a, b, List.of(), message);
    }

    /**
     * Constructs an assertion with no {@code b} and no message.
     *
     * @param func the condition function
     * @param a the first operand
     * @param chain further conditions on the same {@code a}
     */
    public Assertion(String func, Object a, List<ChainedCondition> chain) {
        this(func, a, null, chain, null);
    }

    /**
     * Constructs an assertion with no chain and no message.
     *
     * @param func the condition function
     * @param a the first operand
     * @param b the second operand, or {@code null}
     */
    public Assertion(String func, Object a, Object b) {
        this(func, a, b, List.of(), null);
    }

    /**
     * Constructs an assertion with no {@code b}, no chain and no message.
     *
     * @param func the condition function
     * @param a the first operand
     */
    public Assertion(String func, Object a) {
        this(func, a, null, List.of(), null);
    }

}
