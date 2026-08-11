package dev.pbroman.brat.core.interpolation.configdata;

import java.util.LinkedHashMap;

import dev.pbroman.brat.core.api.interpolation.ConfigDataInterpolator;
import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.data.ChainedCondition;
import dev.pbroman.brat.core.data.runtime.RuntimeData;

import static dev.pbroman.brat.core.interpolation.configdata.InterpolatorUtils.asStringOrNull;
import static dev.pbroman.brat.core.interpolation.configdata.InterpolatorUtils.checkNotInterpolated;
import static dev.pbroman.brat.core.interpolation.configdata.InterpolatorUtils.interpolateArgs;
import static dev.pbroman.brat.core.interpolation.configdata.InterpolatorUtils.interpolateIfPresent;
import static dev.pbroman.brat.core.interpolation.configdata.InterpolatorUtils.interpolateStructure;

/**
 * Interpolates every field of a {@link ChainedCondition}.
 */
public final class ChainedConditionInterpolator implements ConfigDataInterpolator<ChainedCondition> {

    /**
     * Interpolates the link's {@code b}, {@code args} and {@code message}.
     * <p>
     * {@code func} is a name, not a value, and is copied through uninterpolated — the same treatment
     * {@code ConditionInterpolator} gives it. The link's {@code a} is not its own: it belongs to the
     * parent assertion and is interpolated exactly once there.
     * <p>
     * Outcome keys are {@code b}, {@code args.<name>} and {@code message}, chosen so they compose
     * without collision with the {@code a} outcome the parent contributes when a resolver builds this
     * link's full condition.
     *
     * @param target the chained condition to interpolate
     * @param interpolation the interpolation implementation
     * @param runtimeData the runtime data
     * @return a new chained condition with {@code b}, every {@code args} value and {@code message}
     *         interpolated, carrying one outcome per interpolated field in that order. A
     *         {@code null} {@code b} or {@code message} yields no outcome for that field and stays
     *         {@code null} on the copy
     * @throws dev.pbroman.brat.core.exception.BratException if {@code target} is already an
     *         interpolated copy, or if an {@code args} entry has a {@code null} value
     */
    @Override
    public ChainedCondition interpolated(
            ChainedCondition target, Interpolation interpolation, RuntimeData runtimeData) {
        checkNotInterpolated(target);

        var outcomes = new LinkedHashMap<String, InterpolationOutcome>();
        var bValue = interpolateStructure(interpolation, runtimeData, outcomes, "b", target.getB());
        var argsValues = interpolateArgs(target.getArgs(), interpolation, runtimeData, outcomes);
        var messageValue = asStringOrNull(
                interpolateIfPresent(interpolation, runtimeData, outcomes, "message", target.getMessage()));

        var interpolated = new ChainedCondition(target.getFunc(), bValue, messageValue, outcomes);
        interpolated.setArgs(argsValues);
        return interpolated;
    }
}
