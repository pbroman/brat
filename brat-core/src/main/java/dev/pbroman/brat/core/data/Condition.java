package dev.pbroman.brat.core.data;

import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;
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
    private String reportingString;

    public Condition(String func, Object a, Object b, String reportingString) {
        this.func = func;
        this.a = a;
        this.b = b;
        this.reportingString = reportingString;
    }

    /**
     * {@code reportingString} defaults to {@code null} (not yet an interpolated copy).
     *
     * @param func the function name
     * @param a the first operand
     * @param b the second operand
     */
    public Condition(String func, Object a, Object b) {
        this(func, a, b, null);
    }

    /**
     * {@code b} and {@code reportingString} default to {@code null} — for unary funcs
     * (e.g. {@code isNull}) that don't need a second operand.
     *
     * @param func the function name
     * @param a the first operand
     */
    public Condition(String func, Object a) {
        this(func, a, null, null);
    }

    @Override
    public Condition interpolated(Interpolation interpolation, RuntimeData runtimeData) {
        if (reportingString != null) {
            throw new BratException(String.format("This Condition (%s) is already an interpolated copy", this));
        }
        var aOutcome = interpolation.outcome(String.valueOf(a), runtimeData);
        var bOutcome = interpolation.outcome(String.valueOf(b), runtimeData);
        var report = String.format("a: %s, b: %s", aOutcome.reportingString(), bOutcome.reportingString());
        return new Condition(func, aOutcome.value(), bOutcome.value(), report);
    }

    public String toString() {
        return String.format("%s %s %s", a, func, b == null ? "" : b);
    }
}
