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

    public String toString() {
        return String.format("%s %s %s", a, func, b == null ? "" : b);
    }
}
