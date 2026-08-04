package dev.pbroman.brat.core.resolver.assertion;

import java.util.ArrayList;
import java.util.List;

import dev.pbroman.brat.core.api.interpolation.ConfigDataInterpolator;
import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.api.resolver.AssertionResolver;
import dev.pbroman.brat.core.api.resolver.ConditionResolver;
import dev.pbroman.brat.core.data.Assertion;
import dev.pbroman.brat.core.data.AssertionSeverity;
import dev.pbroman.brat.core.data.ChainedCondition;
import dev.pbroman.brat.core.data.Condition;
import dev.pbroman.brat.core.data.result.AssertionResult;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;

/**
 * Resolves an {@link Assertion} and every {@link dev.pbroman.brat.core.data.ChainedCondition} chained
 * onto it, yielding one {@link AssertionResult} per link.
 * <p>
 * Each chained condition borrows the assertion's {@code a}, so it becomes a full {@link Condition}
 * before being resolved. Every result carries the assertion's {@link AssertionSeverity}, and its own
 * message where the chained condition declares one.
 */
public class AssertionChainResolver implements AssertionResolver {

    private final Interpolation interpolation;
    private final ConditionResolver conditionResolver;
    private final ConfigDataInterpolator<Condition> conditionInterpolation;

    /**
     * Constructs a resolver over the collaborators it delegates to.
     *
     * @param interpolation the interpolation handed to {@code conditionInterpolation}
     * @param conditionResolver the resolver each interpolated condition is tested with
     * @param conditionInterpolation the interpolator producing the interpolated copy of a condition
     */
    public AssertionChainResolver(
            Interpolation interpolation,
            ConditionResolver conditionResolver,
            ConfigDataInterpolator<Condition> conditionInterpolation) {
        this.interpolation = interpolation;
        this.conditionResolver = conditionResolver;
        this.conditionInterpolation = conditionInterpolation;
    }

    @Override
    public List<AssertionResult> resolve(Assertion assertion, RuntimeData runtimeData) {
        var assertionResults = new ArrayList<AssertionResult>();
        resolve(assertion, assertionResults, assertion.getMessage(), assertion.getSeverity(), runtimeData);

        for (ChainedCondition chained : assertion.getChain()) {
            var condition = new Condition(chained.getFunc(), assertion.getA(), chained.getB());
            condition.setArgs(chained.getArgs());
            var message = chained.getMessage() != null ? chained.getMessage() : assertion.getMessage();
            resolve(condition, assertionResults, message, assertion.getSeverity(), runtimeData);
        }
        return assertionResults;
    }

    /**
     * Resolves one condition of the chain and appends its result, converting a failure to
     * interpolate into a failed result rather than letting it escape.
     *
     * @param condition the condition to resolve
     * @param assertionResults the results collected so far, appended to
     * @param message the message to report if the condition fails
     * @param severity the severity to record on the result
     * @param runtimeData the object containing values
     */
    protected void resolve(
            Condition condition,
            List<AssertionResult> assertionResults,
            String message,
            AssertionSeverity severity,
            RuntimeData runtimeData) {
        try {
            var interpolatedCondition = conditionInterpolation.interpolated(condition, interpolation, runtimeData);
            assertionResults.add(new AssertionResult(
                    interpolatedCondition, message, conditionResolver.resolve(interpolatedCondition), severity));
        } catch (BratException e) {
            var failMessage = String.format("Error interpolating assertion: %s, message: %s", message, e.getMessage());
            assertionResults.add(new AssertionResult(condition, failMessage, false, severity));
        }
    }
}
