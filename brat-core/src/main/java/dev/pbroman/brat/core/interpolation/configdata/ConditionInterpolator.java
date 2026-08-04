package dev.pbroman.brat.core.interpolation.configdata;

import java.util.LinkedHashMap;
import java.util.Map;

import dev.pbroman.brat.core.api.interpolation.ConfigDataInterpolator;
import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.data.Condition;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;

import static dev.pbroman.brat.core.interpolation.configdata.InterpolatorUtils.checkNotInterpolated;
import static dev.pbroman.brat.core.interpolation.configdata.InterpolatorUtils.interpolateIfPresent;

/**
 * Interpolates every field of a {@link Condition}.
 */
public final class ConditionInterpolator implements ConfigDataInterpolator<Condition> {

    /**
     * Outcomes for the func's arguments are keyed by dotted path, so an argument never collides with
     * the {@code a}/{@code b} entries.
     */
    private static final String ARGS_PREFIX = "args.";

    @Override
    public Condition interpolated(Condition target, Interpolation interpolation, RuntimeData runtimeData) {
        checkNotInterpolated(target);

        var outcomes = new LinkedHashMap<String, InterpolationOutcome>();
        var aValue = target.getA() == null ? null : String.valueOf(target.getA());
        var aOutcome = interpolateIfPresent(interpolation, runtimeData, outcomes, "a", aValue);

        var bValue = target.getB() == null ? null : String.valueOf(target.getB());
        var bOutcome = interpolateIfPresent(interpolation, runtimeData, outcomes, "b", bValue);

        var args = new LinkedHashMap<String, String>();
        for (var arg : target.getArgs().entrySet()) {
            if (arg.getValue() == null) {
                throw new BratException("The argument '" + arg.getKey() + "' has no value");
            }
            var outcome = interpolateIfPresent(
                    interpolation, runtimeData, outcomes, ARGS_PREFIX + arg.getKey(), arg.getValue());
            args.put(arg.getKey(), String.valueOf(outcome.value()));
        }

        var interpolated = new Condition(
                target.getFunc(),
                aOutcome == null ? null : aOutcome.value(),
                bOutcome == null ? null : bOutcome.value(),
                outcomes);
        interpolated.setArgs(Map.copyOf(args));
        return interpolated;
    }
}
