package dev.pbroman.brat.core.resolver.assertion;

import java.util.ArrayList;
import java.util.List;

import dev.pbroman.brat.core.api.data.ConfigDataInterpolation;
import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.api.resolver.AssertionResolver;
import dev.pbroman.brat.core.api.resolver.ConditionResolver;
import dev.pbroman.brat.core.data.Assertion;
import dev.pbroman.brat.core.data.ChainedAssertion;
import dev.pbroman.brat.core.data.Condition;
import dev.pbroman.brat.core.data.result.AssertionResult;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;

public class DefaultAssertionResolver implements AssertionResolver {

    private final Interpolation interpolation;
    private final ConditionResolver conditionResolver;
    private final ConfigDataInterpolation<Condition> conditionInterpolation;

    public DefaultAssertionResolver(Interpolation interpolation, ConditionResolver conditionResolver,
            ConfigDataInterpolation<Condition> conditionInterpolation) {
        this.interpolation = interpolation;
        this.conditionResolver = conditionResolver;
        this.conditionInterpolation = conditionInterpolation;
    }

    @Override
    public List<AssertionResult> resolve(Assertion assertion, RuntimeData runtimeData) {
        var assertionResults = new ArrayList<AssertionResult>();
        resolve(assertion, assertionResults, assertion.getMessage(), runtimeData);

        for (ChainedAssertion chained : assertion.getChain()) {
            var condition = new Condition(chained.getFunc(), assertion.getA(), chained.getB());
            var message = chained.getMessage() != null ? chained.getMessage() : assertion.getMessage();
            resolve(condition, assertionResults, message, runtimeData);
        }
        return assertionResults;
    }

    protected void resolve(Condition condition, List<AssertionResult> assertionResults, String message, RuntimeData runtimeData) {
        try {
            var interpolatedCondition = conditionInterpolation.interpolated(condition, interpolation, runtimeData);
            assertionResults.add(new AssertionResult(interpolatedCondition, message, conditionResolver.resolve(interpolatedCondition)));
        } catch (BratException e) {
            var failMessage = String.format("Error interpolating assertion: %s, message: %s", message, e.getMessage());
            assertionResults.add(new AssertionResult(condition, failMessage, false));
        }
    }

}
