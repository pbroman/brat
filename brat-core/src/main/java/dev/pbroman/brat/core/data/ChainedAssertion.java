package dev.pbroman.brat.core.data;

import lombok.Getter;
import lombok.Setter;

/**
 * A chained assertion is an extension of an assertion in that it defines an additional condition to the assertions
 * primary {@link Condition}.
 */
@Getter
@Setter
public class ChainedAssertion {

    private final String func;
    private final Object b;
    private final String message;

    public ChainedAssertion(String func, Object b, String message) {
        this.func = func;
        this.b = b;
        this.message = message;
    }

    public ChainedAssertion(String func, Object b) {
        this(func, b, null);
    }

}
