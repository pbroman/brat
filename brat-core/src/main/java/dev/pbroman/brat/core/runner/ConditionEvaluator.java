package dev.pbroman.brat.core.runner;

import dev.pbroman.brat.core.api.interpolation.ConfigDataInterpolator;
import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.api.resolver.ConditionResolver;
import dev.pbroman.brat.core.data.Condition;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;

/**
 * Interpolates a condition and answers it, which a run needs to do in two unrelated places.
 * <p>
 * <strong>Not a {@link ConditionResolver}, and the distinction is the same one
 * {@code FunctionEvaluator} draws against {@code BratFunction}:</strong> the resolver is the
 * extension point that answers a condition already resolved to values, while this composes the step
 * before it. A run's two conditions — a request's {@code skipCondition} and a {@code repeatUntil} —
 * are semantically unrelated but mechanically identical, and were spelled out twice in two classes
 * before this existed.
 */
class ConditionEvaluator {

    private final Interpolation interpolation;
    private final ConfigDataInterpolator<Condition> conditionInterpolator;
    private final ConditionResolver conditionResolver;

    /**
     * Constructs an evaluator over the collaborators it composes.
     *
     * @param interpolation resolves the tokens inside the condition
     * @param conditionInterpolator produces the interpolated copy
     * @param conditionResolver answers the interpolated copy
     */
    ConditionEvaluator(
            Interpolation interpolation,
            ConfigDataInterpolator<Condition> conditionInterpolator,
            ConditionResolver conditionResolver) {
        this.interpolation = interpolation;
        this.conditionInterpolator = conditionInterpolator;
        this.conditionResolver = conditionResolver;
    }

    /**
     * What evaluating a condition produced.
     * <p>
     * The interpolated copy is returned alongside the verdict because a caller reporting the decision
     * needs it: {@code "Skipped due to condition true isTrue"} tells an author what the guard actually
     * saw, where the authored {@code ${vars.skip} isTrue} would only repeat what they wrote.
     *
     * @param condition the interpolated copy, as the resolver saw it
     * @param holds whether it resolved to {@code true}
     */
    record Evaluation(Condition condition, boolean holds) {}

    /**
     * Interpolates {@code condition} and answers it.
     * <p>
     * <strong>Failures are not caught here.</strong> A condition that cannot be interpolated or names
     * a func nothing resolves is an authoring error, and what it means depends on which condition it
     * was — a skip condition is a guard that must fail closed, a loop condition ends the loop at once
     * — so the decision belongs to the caller rather than to this.
     *
     * @param condition the authored condition; never {@code null}
     * @param runtimeData the namespaces to resolve it against
     * @return the interpolated copy and its verdict
     * @throws BratException if {@code condition} cannot be interpolated, or if nothing resolves its
     *         func
     */
    Evaluation evaluate(Condition condition, RuntimeData runtimeData) {
        var interpolated = conditionInterpolator.interpolated(condition, interpolation, runtimeData);
        return new Evaluation(interpolated, conditionResolver.resolve(interpolated));
    }
}
