package dev.pbroman.brat.core.data;

import java.util.Map;

import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import lombok.Getter;
import lombok.Setter;

/**
 * A condition is a function 'a func b' that can be evaluated to true or false.
 */
@Getter
@Setter
public class Condition extends ConfigData {

    private String func;
    private Object a;
    private Object b;

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
     * {@code outcomes} defaults to {@code null} (not yet an interpolated copy).
     *
     * @param func the function name
     * @param a the first operand
     * @param b the second operand
     */
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
        this.args = args == null ? Map.of() : args;
    }

    public String toString() {
        return String.format("%s %s %s", a, func, b == null ? "" : b);
    }
}
