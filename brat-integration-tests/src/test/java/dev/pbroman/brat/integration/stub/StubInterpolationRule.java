package dev.pbroman.brat.integration.stub;

import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.interpolation.rules.AbstractInterpolationRule;

/**
 * Makes a stub response readable from a suite as {@code ${stub.echoed}} and {@code ${stub.length}}.
 *
 * <p>A protocol ships its own namespace rather than borrowing {@code response.*}, which is HTTP's
 * vocabulary. Nothing else claims {@code ${stub.…}}, so this needs no priority to be reached.
 */
public final class StubInterpolationRule extends AbstractInterpolationRule {

    /** Constructs the rule for the {@code stub} namespace. */
    public StubInterpolationRule() {
        super("stub");
    }

    @Override
    protected String resolve(String input, RuntimeData runtimeData) {
        return simpleInterpolation(input, runtimeData, runtimeData.getResponseVars());
    }
}
