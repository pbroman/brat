package dev.pbroman.brat.core.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import lombok.Getter;

/**
 * An Assertion extends a {@link Condition} with a message on fail (i.e. when the condition evaluates to false),
 * a (possibly empty) list of chained conditions, and the severity of a failure.
 * <p>
 * Immutable, like every authored type: {@code severity} and the inherited {@code args} are the last
 * two parameters of the constructor the loader binds to, and both default when an author omits them —
 * {@code severity} to {@link AssertionSeverity#FAIL}, {@code args} to empty. Every shorter overload
 * defaults them, so the common cases stay short.
 */
@Getter
public final class Assertion extends Condition {

    private final List<ChainedCondition> chain;
    private final String message;
    private final AssertionSeverity severity;

    /**
     * Constructs an interpolated copy of an assertion, carrying its named outcomes.
     *
     * @param func the condition function
     * @param a the first operand
     * @param b the second operand, or {@code null} for a func that needs none
     * @param chain further conditions on the same {@code a}, already interpolated; {@code null} is
     *        treated as empty
     * @param message the message to report if the condition fails, or {@code null}
     * @param args the func's arguments, or {@code null} for none
     * @param severity the severity to report a failure at, or {@code null} for
     *        {@link AssertionSeverity#FAIL}
     * @param outcomes the named interpolation outcomes of an interpolated copy, or {@code null} on
     *        an as-authored instance
     */
    public Assertion(
            String func,
            Object a,
            Object b,
            List<ChainedCondition> chain,
            String message,
            Map<String, String> args,
            AssertionSeverity severity,
            Map<String, InterpolationOutcome> outcomes) {
        super(func, a, b, args, outcomes);
        this.chain = chain == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(chain));
        this.message = message;
        this.severity = severity == null ? AssertionSeverity.FAIL : severity;
    }

    /**
     * The constructor the loader binds an authored assertion to — the only one taking {@code args}
     * and {@code severity}, both of which are optional for an author and default here. Every shorter
     * overload defaults them, along with {@code outcomes}.
     *
     * @param func the condition function
     * @param a the first operand
     * @param b the second operand, or {@code null} for a func that needs none
     * @param chain further conditions on the same {@code a}, possibly empty; {@code null} is
     *        treated as empty, so {@link #getChain()} never returns {@code null}
     * @param message the message to report if the condition fails, or {@code null}
     * @param args the func's arguments, or {@code null} for none
     * @param severity the severity to report a failure at, or {@code null} for
     *        {@link AssertionSeverity#FAIL}
     */
    @JsonCreator
    public Assertion(
            String func,
            Object a,
            Object b,
            List<ChainedCondition> chain,
            String message,
            Map<String, String> args,
            AssertionSeverity severity) {
        this(func, a, b, chain, message, args, severity, null);
    }

    /**
     * Constructs a fully specified assertion with no arguments and the default severity.
     *
     * @param func the condition function
     * @param a the first operand
     * @param b the second operand, or {@code null} for a func that needs none
     * @param chain further conditions on the same {@code a}, possibly empty
     * @param message the message to report if the condition fails, or {@code null}
     */
    public Assertion(String func, Object a, Object b, List<ChainedCondition> chain, String message) {
        this(func, a, b, chain, message, null, null, null);
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
