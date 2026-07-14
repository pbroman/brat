package dev.pbroman.brat.core.data;

import static dev.pbroman.brat.core.util.ConfigDataInterpolationUtils.checkNotInterpolated;
import static dev.pbroman.brat.core.util.ConfigDataInterpolationUtils.interpolateIfPresent;

import java.util.LinkedHashMap;

import dev.pbroman.brat.core.api.data.ConfigDataInterpolation;
import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.data.runtime.RuntimeData;

/**
 * Interpolates every field of a {@link Condition}.
 */
public final class ConditionInterpolation implements ConfigDataInterpolation<Condition> {

    @Override
    public Condition interpolated(Condition target, Interpolation interpolation, RuntimeData runtimeData) {
        checkNotInterpolated(target);

        var outcomes = new LinkedHashMap<String, InterpolationOutcome>();
        var aValue = target.getA() == null ? null : String.valueOf(target.getA());
        var aOutcome = interpolateIfPresent(interpolation, runtimeData, outcomes, "a", aValue);

        var bValue = target.getB() == null ? null : String.valueOf(target.getB());
        var bOutcome = interpolateIfPresent(interpolation, runtimeData, outcomes, "b", bValue);

        return new Condition(target.getFunc(),
                aOutcome == null ? null : aOutcome.value(),
                bOutcome == null ? null : bOutcome.value(), outcomes);
    }
}
