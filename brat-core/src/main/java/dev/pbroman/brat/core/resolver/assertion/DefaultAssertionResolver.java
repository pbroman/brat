package dev.pbroman.brat.core.resolver.assertion;

import java.util.ArrayList;
import java.util.List;

import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.api.resolver.AssertionResolver;
import dev.pbroman.brat.core.api.resolver.ConditionResolver;
import dev.pbroman.brat.core.data.Assertion;
import dev.pbroman.brat.core.data.ChainedAssertion;
import dev.pbroman.brat.core.data.Condition;
import dev.pbroman.brat.core.data.result.AssertionFail;
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
    public List<AssertionFail> resolve(Assertion assertion, RuntimeData runtimeData) throws ValidationException {
        var failedConditions = new ArrayList<AssertionFail>();
        resolve(assertion, failedConditions, assertion.getMessage());

        for (ChainedAssertion chained : assertion.getChain()) {
            var condition = createCondition(assertion.getA(), chained.getFunc(), chained.getB(), runtimeData);
            var message = chained.getMessage() != null ? chained.getMessage() : assertion.getMessage();
            resolve(condition, failedConditions, message);
        }
        return failedConditions;
    }

    protected Condition createCondition(Object a, String func, Object b, RuntimeData runtimeData)
            throws ValidationException {
        var condition = new Condition(func, a, b);
        condition.interpolate(interpolation, runtimeData);
        return condition;
    }

    protected void resolve(Condition condition, List<AssertionFail> failedConditions, String message)
            throws ValidationException {
        if (!conditionResolver.resolve(condition)) {
            failedConditions.add(new AssertionFail(condition, message));
        }

    }
}
