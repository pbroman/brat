package dev.pbroman.brat.core.data;

import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.ValidationException;
import lombok.Getter;
import lombok.Setter;

/**
 * A condition is a function 'a func b' that can be evaluated to true or false.
 */
@Getter
@Setter
public class Condition extends ConfigData {

    private final String func;
    private Object a;
    private Object b;
    private Object originalA;
    private Object originalB;

    public Condition(String func, Object a, Object b) {
        this.func = func;
        this.a = a;
        this.b = b;
    }

    public Condition(String func, Object a) {
        this(func, a, null);
    }

    @Override
    public void interpolate(Interpolation interpolation, RuntimeData runtimeData) throws ValidationException {
        originalA = a;
        originalB = b;
        a = interpolation.interpolate(String.valueOf(a), runtimeData);
        b = interpolation.interpolate(String.valueOf(b), runtimeData);
    }

    public String toString() {
        return String.format("%s %s%s %s%s",
                a,
                originalA == null || String.valueOf(a).equals(String.valueOf(originalA)) ? "" : String.format("(original: %s) ", originalA),
                func,
                b == null ? "" : b,
                b == null || originalB == null || String.valueOf(b).equals(String.valueOf(originalB)) ? "" : String.format(" (original: %s)", originalB));
    }
}
