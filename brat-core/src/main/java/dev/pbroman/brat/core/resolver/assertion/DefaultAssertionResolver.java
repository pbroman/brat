package dev.pbroman.brat.core.resolver.assertion;

import java.util.ArrayList;
import java.util.List;

import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.api.resolver.AssertionResolver;
import dev.pbroman.brat.core.api.resolver.ConditionResolver;
import dev.pbroman.brat.core.data.Assertion;
import dev.pbroman.brat.core.data.ChainedAssertion;
import dev.pbroman.brat.core.data.Condition;
import dev.pbroman.brat.core.data.result.AssertionResult;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.ValidationException;

public class DefaultAssertionResolver implements AssertionResolver {

    private final Interpolation interpolation;
    private final ConditionResolver conditionResolver;

    public DefaultAssertionResolver(Interpolation interpolation, ConditionResolver conditionResolver) {
        this.interpolation = interpolation;
        this.conditionResolver = conditionResolver;
    }

    @Override
    public List<AssertionResult> resolve(Assertion assertion, RuntimeData runtimeData) {
        var assertionResults = new ArrayList<AssertionResult>();
        resolve(assertion, assertionResults, assertion.getMessage());

        for (ChainedAssertion chained : assertion.getChain()) {
            var condition = new Condition(chained.getFunc(), assertion.getA(), chained.getB());
            var message = chained.getMessage() != null ? chained.getMessage() : assertion.getMessage();
            try {
                condition.interpolate(interpolation, runtimeData);
                resolve(condition, assertionResults, message);
            } catch (ValidationException e) {
                addValidationFailAssertionResult(condition, assertionResults, message, e);
            }
        }
        return assertionResults;
    }

    protected void resolve(Condition condition, List<AssertionResult> assertionResults, String message) {
        try {
            assertionResults.add(new AssertionResult(condition, message, conditionResolver.resolve(condition)));
        } catch (ValidationException e) {
            addValidationFailAssertionResult(condition, assertionResults, message, e);
        }
    }

    private void addValidationFailAssertionResult(Condition condition,
                                    List<AssertionResult> assertionResults,
                                    String message,
                                    Exception e) {
        var validationMessage = String.format("Validation failed for assertion: %s, message: %s", message, e.getMessage());
        assertionResults.add(new AssertionResult(condition, validationMessage, false));
    }
}
