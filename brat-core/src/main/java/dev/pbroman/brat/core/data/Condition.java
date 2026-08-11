package dev.pbroman.brat.core.data;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import lombok.Getter;

/**
 * A condition is a function 'a func b' that can be evaluated to true or false.
 * <p>
 * {@code sealed} rather than {@code final}: {@link Assertion} is the one designed subclass, and no
 * extender has a path to a second one — an {@code assertions:} entry binds to {@link Assertion} and a
 * {@code skipCondition:} to this type, each by field with a known static type, so nothing picks a
 * subclass at runtime.
 */
@Getter
public sealed class Condition extends ConfigData permits Assertion {

    private final String func;
    private final Object a;
    private final Object b;

    /**
     * The func's own arguments, e.g. {@code offset} for {@code isCloseTo}. Never {@code null};
     * empty when the author declared none. A func needing an argument takes it from here rather
     * than from a field of its own, so adding one never changes this type.
     * <p>
     * Named {@code args} rather than {@code params} deliberately: {@code params} is the
     * interpolation namespace behind {@code ${params.x}}, and the two would otherwise be
     * indistinguishable in a suite file and in reported outcome keys.
     */
    private Map<String, String> args = Map.of();

    /**
     * Constructor for an interpolated {@link Condition} object with its named outcomes.
     *
     * @param func the function name
     * @param a the first operand
     * @param b the second operand
     * @param outcomes the named interpolation outcomes
     */
    public Condition(String func, Object a, Object b, Map<String, InterpolationOutcome> outcomes) {
        super(outcomes);
        this.func = func;
        this.a = a;
        this.b = b;
    }

    /**
     * {@code outcomes} defaults to {@code null} (not yet an interpolated copy). This is the
     * constructor the loader binds an authored condition to.
     *
     * @param func the function name
     * @param a the first operand
     * @param b the second operand
     */
    @JsonCreator
    public Condition(String func, Object a, Object b) {
        this(func, a, b, null);
    }

    /**
     * {@code b} and {@code outcomes} default to {@code null} — for unary funcs (e.g.
     * {@code isNull}) that don't need a second operand.
     *
     * @param func the function name
     * @param a the first operand
     */
    public Condition(String func, Object a) {
        this(func, a, null, null);
    }

    /**
     * Treats {@code null} as "no arguments", keeping the never-null invariant the resolver rules
     * rely on.
     *
     * @param args the func's arguments, or {@code null} for none
     */
    public void setArgs(Map<String, String> args) {
        this.args = args == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(args));
    }

    /**
     * Renders the condition as {@code a func b}, which is how it appears in a failure message.
     *
     * @return the rendered condition
     */
    public String toString() {
        return String.format("%s %s %s", a, func, b == null ? "" : b);
    }
}
