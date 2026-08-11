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
 * The severity defaults to {@link AssertionSeverity#FAIL} and is not settable through any constructor:
 * declaring it is optional for a suite author, and the constructors are already numerous. It is
 * therefore one of the two properties the loader binds through a setter rather than through the
 * creator — {@code args}, inherited from {@link Condition}, is the other.
 */
@Getter
public final class Assertion extends Condition {

    private final List<ChainedCondition> chain;
    private final String message;
    private AssertionSeverity severity = AssertionSeverity.FAIL;

    /**
     * Sets the severity, treating {@code null} as the {@link AssertionSeverity#FAIL} default so an
     * explicitly-null {@code severity:} key cannot produce an assertion with no severity at all.
     *
     * @param severity the severity to report a failure of this assertion at, or {@code null} for
     *        the default
     */
    public void setSeverity(AssertionSeverity severity) {
        this.severity = severity == null ? AssertionSeverity.FAIL : severity;
    }

    /**
     * Constructs an interpolated copy of an assertion, carrying its named outcomes.
     * <p>
     * {@code severity} and {@code args} are not arguments here, matching how
     * {@code ConditionInterpolator} already builds a {@link Condition}: both are applied to the copy
     * through their setters afterwards.
     *
     * @param func the condition function
     * @param a the first operand
     * @param b the second operand, or {@code null} for a func that needs none
     * @param chain further conditions on the same {@code a}, already interpolated; {@code null} is
     *        treated as empty
     * @param message the message to report if the condition fails, or {@code null}
     * @param outcomes the named interpolation outcomes of an interpolated copy, or {@code null} on
     *        an as-authored instance
     */
    public Assertion(
            String func,
            Object a,
            Object b,
            List<ChainedCondition> chain,
            String message,
            Map<String, InterpolationOutcome> outcomes) {
        super(func, a, b, outcomes);
        this.chain = chain == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(chain));
        this.message = message;
    }

    /**
     * Constructs a fully specified assertion. The other constructors default the arguments they
     * omit: {@code chain} to an empty list, {@code message} and {@code b} to {@code null}. Severity
     * is not a constructor argument at all — it defaults to {@link AssertionSeverity#FAIL} and is
     * set afterwards where an author declares it.
     * <p>
     * This is the constructor the loader binds an authored assertion to, so {@code outcomes}
     * defaults to {@code null}: a bound instance is always an as-authored one.
     *
     * @param func the condition function
     * @param a the first operand
     * @param b the second operand, or {@code null} for a func that needs none
     * @param chain further conditions on the same {@code a}, possibly empty; {@code null} is
     *        treated as empty, so {@link #getChain()} never returns {@code null}
     * @param message the message to report if the condition fails, or {@code null}
     */
    @JsonCreator
    public Assertion(String func, Object a, Object b, List<ChainedCondition> chain, String message) {
        this(func, a, b, chain, message, null);
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
