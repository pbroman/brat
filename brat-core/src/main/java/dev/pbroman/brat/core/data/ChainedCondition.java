package dev.pbroman.brat.core.data;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import lombok.Getter;

/**
 * An additional condition chained onto an {@link Assertion}, sharing that assertion's subject.
 * <p>
 * It carries only {@code func} and {@code b}: the {@code a} it is tested against is the parent
 * assertion's, so a resolver turns each chained condition into a full {@link Condition} before
 * resolving it. Its {@code message} overrides the parent's when the condition fails.
 */
@Getter
public final class ChainedCondition extends ConfigData {

    private final String func;
    private final Object b;
    private final String message;

    /**
     * This link's own func arguments, independent of the parent assertion's. Never {@code null}.
     */
    private Map<String, String> args = Map.of();

    /**
     * Constructs an interpolated copy of a chained condition, carrying its named outcomes.
     *
     * @param func the condition function, resolved against the parent assertion's {@code a}
     * @param b the second operand, or {@code null} for a func that needs none
     * @param message the message to report if this condition fails, overriding the parent's
     * @param outcomes the named interpolation outcomes of an interpolated copy, or {@code null} on
     *        an as-authored instance
     */
    public ChainedCondition(String func, Object b, String message, Map<String, InterpolationOutcome> outcomes) {
        super(outcomes);
        this.func = func;
        this.b = b;
        this.message = message;
    }

    /**
     * Constructs a chained condition with its own failure message. This is the constructor the
     * loader binds a {@code chain:} entry to; {@code outcomes} defaults to {@code null}, so a bound
     * instance is always an as-authored one.
     *
     * @param func the condition function, resolved against the parent assertion's {@code a}
     * @param b the second operand, or {@code null} for a func that needs none
     * @param message the message to report if this condition fails, overriding the parent's
     */
    @JsonCreator
    public ChainedCondition(String func, Object b, String message) {
        this(func, b, message, null);
    }

    /**
     * Constructs a chained condition without its own message, so a failure reports the parent
     * assertion's.
     *
     * @param func the condition function, resolved against the parent assertion's {@code a}
     * @param b the second operand, or {@code null} for a func that needs none
     */
    public ChainedCondition(String func, Object b) {
        this(func, b, null, null);
    }

    /**
     * Treats {@code null} as "no arguments", keeping the never-null invariant.
     *
     * @param args the func's arguments, or {@code null} for none
     */
    public void setArgs(Map<String, String> args) {
        this.args = args == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(args));
    }
}
