package dev.pbroman.brat.core.data;

import lombok.Data;

@Data
public class Condition {

    private final String func;
    private final Object a;
    private final Object b;
    private final String message;

    public Condition(String func, Object a, Object b, String message) {
        this.func = func;
        this.a = a;
        this.b = b;
        this.message = message;
    }

    public Condition(String func, Object a, Object b) {
        this(func, a, b, null);
    }

    public Condition(String func, Object a) {
        this(func, a, null, null);
    }

    public String toString() {
        return String.format("Condition %s %s %s, message: %s", a, func, b, message);
    }
}
