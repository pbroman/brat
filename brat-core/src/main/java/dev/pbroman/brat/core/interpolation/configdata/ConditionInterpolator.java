package dev.pbroman.brat.core.interpolation.configdata;

import java.util.LinkedHashMap;

import dev.pbroman.brat.core.api.interpolation.ConfigDataInterpolator;
import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.data.Condition;
import dev.pbroman.brat.core.data.runtime.RuntimeData;

import static dev.pbroman.brat.core.interpolation.configdata.InterpolatorUtils.checkNotInterpolated;
import static dev.pbroman.brat.core.interpolation.configdata.InterpolatorUtils.interpolateArgs;
import static dev.pbroman.brat.core.interpolation.configdata.InterpolatorUtils.interpolateStructure;

/**
 * Interpolates every field of a {@link Condition}.
 */
public final class ConditionInterpolator implements ConfigDataInterpolator<Condition> {

    /**
     * Interpolates the condition's {@code a}, {@code b} and {@code args}.
     * <p>
     * {@code func} is a name, not a value, and is copied through uninterpolated.
     * <p>
     * Outcome keys are {@code a}, {@code b} and {@code args.<name>}.
     *
     * @param target the condition to interpolate
     * @param interpolation the interpolation implementation
     * @param runtimeData the runtime data
     * @return a new condition with {@code a}, {@code b} and every {@code args} value interpolated,
     *         carrying one outcome per interpolated field in that order. A {@code null} {@code a} or
     *         {@code b} yields no outcome for that field and stays {@code null} on the copy; an empty
     *         {@code args} yields no {@code args.*} outcomes
     * @throws dev.pbroman.brat.core.exception.BratException if {@code target} is already an
     *         interpolated copy, or if an {@code args} entry has a {@code null} value
     */
    @Override
    public Condition interpolated(Condition target, Interpolation interpolation, RuntimeData runtimeData) {
        checkNotInterpolated(target);

        var outcomes = new LinkedHashMap<String, InterpolationOutcome>();
        var aValue = interpolateStructure(interpolation, runtimeData, outcomes, "a", target.getA());
        var bValue = interpolateStructure(interpolation, runtimeData, outcomes, "b", target.getB());
        var argsValues = interpolateArgs(target.getArgs(), interpolation, runtimeData, outcomes);

        var interpolated = new Condition(target.getFunc(), aValue, bValue, outcomes);
        interpolated.setArgs(argsValues);
        return interpolated;
    }
}
