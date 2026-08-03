package dev.pbroman.brat.core.data;

import lombok.Getter;
import lombok.Setter;

/**
 * An additional condition chained onto an {@link Assertion}, sharing that assertion's subject.
 * <p>
 * It carries only {@code func} and {@code b}: the {@code a} it is tested against is the parent
 * assertion's, so a resolver turns each chained condition into a full {@link Condition} before
 * resolving it. Its {@code message} overrides the parent's when the condition fails.
 */
@Getter
@Setter
public class ChainedCondition {

    private final String func;
    private final Object b;
    private final String message;

    /**
     * Constructs a chained condition with its own failure message.
     *
     * @param func the condition function, resolved against the parent assertion's {@code a}
     * @param b the second operand, or {@code null} for a func that needs none
     * @param message the message to report if this condition fails, overriding the parent's
     */
    public ChainedCondition(String func, Object b, String message) {
        this.func = func;
        this.b = b;
        this.message = message;
    }

    /**
     * Constructs a chained condition without its own message, so a failure reports the parent
     * assertion's.
     *
     * @param func the condition function, resolved against the parent assertion's {@code a}
     * @param b the second operand, or {@code null} for a func that needs none
     */
    public ChainedCondition(String func, Object b) {
        this(func, b, null);
    }

}
