package dev.pbroman.brat.core.data;

import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
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
    private final Condition nonInterpolated;

    public Condition(String func, Object a, Object b, Condition nonInterpolated) {
        this.func = func;
        this.a = a;
        this.b = b;
        this.nonInterpolated = nonInterpolated;
    }

    public Condition(String func, Object a, Object b) {
        this(func, a, b, null);
    }

    public Condition(String func, Object a) {
        this(func, a, null, null);
    }

    @Override
    public Condition interpolated(Interpolation interpolation, RuntimeData runtimeData) {
        if (nonInterpolated != null) {
            throw new IllegalStateException(String.format("This Condition (%s) is already an interpolated copy", this));
        }
        return new Condition(func,
                interpolation.interpolate(String.valueOf(a), runtimeData),
                interpolation.interpolate(String.valueOf(b), runtimeData),
                this);
    }


    public String toString() {
        return String.format("%s %s%s %s%s",
                a,
                getNonInterpolatedStringForMessage(nonInterpolated == null ? null : nonInterpolated.getA(), a),
                func,
                b == null ? "" : b,
                getNonInterpolatedStringForMessage(nonInterpolated == null ? null : nonInterpolated.getB(), b));
    }
}
