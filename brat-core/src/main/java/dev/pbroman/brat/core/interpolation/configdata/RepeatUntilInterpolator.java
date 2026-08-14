package dev.pbroman.brat.core.interpolation.configdata;

import java.util.LinkedHashMap;

import dev.pbroman.brat.core.api.interpolation.ConfigDataInterpolator;
import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.data.Condition;
import dev.pbroman.brat.core.data.RepeatUntil;
import dev.pbroman.brat.core.data.runtime.RuntimeData;

import static dev.pbroman.brat.core.interpolation.configdata.InterpolatorUtils.asStringOrNull;
import static dev.pbroman.brat.core.interpolation.configdata.InterpolatorUtils.checkNotInterpolated;
import static dev.pbroman.brat.core.interpolation.configdata.InterpolatorUtils.interpolateIfPresent;

/**
 * Interpolates every field of a {@link RepeatUntil}, including its condition.
 */
public final class RepeatUntilInterpolator implements ConfigDataInterpolator<RepeatUntil> {

    private final ConfigDataInterpolator<Condition> conditionInterpolator;

    /**
     * Constructs an interpolator over the one it delegates the nested condition to.
     *
     * @param conditionInterpolator the interpolator applied to the loop's condition
     */
    public RepeatUntilInterpolator(ConfigDataInterpolator<Condition> conditionInterpolator) {
        this.conditionInterpolator = conditionInterpolator;
    }

    /**
     * Interpolates {@code maxAttempts}, {@code waitBetweenAttempts} and {@code messageOnFail}, and
     * the nested condition.
     * <p>
     * Outcome keys are the field names themselves, and only for fields that were declared. The
     * condition's own outcomes stay <em>on the condition</em> and are not merged in, so this block's
     * key set does not depend on how the condition is written — the same rule
     * {@code AssertionInterpolator} applies to a chain link.
     * <p>
     * Nothing is parsed here: {@code maxAttempts} and {@code waitBetweenAttempts} stay text, and
     * become numbers at the point of use, where a value that is not a number can be reported against
     * the request that used it.
     *
     * @param target the repeat-until block to interpolate
     * @param interpolation the interpolation implementation
     * @param runtimeData the runtime data
     * @return a new block with every declared field interpolated and its condition replaced by an
     *         interpolated copy. A {@code null} {@code maxAttempts}, {@code waitBetweenAttempts} or
     *         {@code messageOnFail} yields no outcome and stays {@code null} on the copy
     * @throws dev.pbroman.brat.core.exception.BratException if {@code target} is {@code null} or is
     *         already an interpolated copy; if {@code target.condition} is {@code null}; or if the
     *         condition fails to interpolate — a repeat-until block is interpolated wholly or not at
     *         all
     */
    @Override
    public RepeatUntil interpolated(RepeatUntil target, Interpolation interpolation, RuntimeData runtimeData) {
        checkNotInterpolated(target);

        var outcomes = new LinkedHashMap<String, InterpolationOutcome>();

        var conditionInterpolated =
                conditionInterpolator.interpolated(target.getCondition(), interpolation, runtimeData);
        var maxAttemptsOutcome =
                interpolateIfPresent(interpolation, runtimeData, outcomes, "maxAttempts", target.getMaxAttempts());
        var waitBetweenAttemptsOutcome = interpolateIfPresent(
                interpolation, runtimeData, outcomes, "waitBetweenAttempts", target.getWaitBetweenAttempts());
        var messageOnFailOutcome =
                interpolateIfPresent(interpolation, runtimeData, outcomes, "messageOnFail", target.getMessageOnFail());

        return new RepeatUntil(
                conditionInterpolated,
                asStringOrNull(maxAttemptsOutcome),
                asStringOrNull(waitBetweenAttemptsOutcome),
                asStringOrNull(messageOnFailOutcome),
                outcomes);
    }
}
