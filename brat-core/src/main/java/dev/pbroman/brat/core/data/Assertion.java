package dev.pbroman.brat.core.data;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * An Assertion extends a {@link Condition} with a message on fail (i.e. when the condition evaluates to false)
 * and a (possibly empty) list of chained assertions.
 */
@Getter
@Setter
public class Assertion extends Condition {

    private final List<ChainedAssertion> chain;
    private final String message;

    public Assertion(String func, Object a, Object b, List<ChainedAssertion> chain, String message) {
        super(func, a, b);
        this.chain = chain;
        this.message = message;
    }

    public Assertion(String func, Object a, Object b, List<ChainedAssertion> chain) {
        this(func, a, b, chain, null);
    }

    public Assertion(String func, Object a, Object b, String message) {
        this(func, a, b, List.of(), message);
    }

    public Assertion(String func, Object a, List<ChainedAssertion> chain) {
        this(func, a, null, chain, null);
    }

    public Assertion(String func, Object a, Object b) {
        this(func, a, b, List.of(), null);
    }

    public Assertion(String func, Object a) {
        this(func, a, null, List.of(), null);
    }

}
