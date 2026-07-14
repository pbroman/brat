package dev.pbroman.brat.core.util;

import java.util.LinkedHashMap;
import java.util.Map;

import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.data.ConfigData;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;

/**
 * Helper methods shared by {@code ConfigDataInterpolation} implementations.
 */
public class ConfigDataInterpolationUtils {

    private ConfigDataInterpolationUtils() {
        // utility class
    }

    /**
     * Checks that {@code target} is not already an interpolated copy.
     *
     * @param target the object to check
     * @throws BratException if {@code target.isInterpolated()} is {@code true}
     */
    public static void checkNotInterpolated(ConfigData target) {
        if (target.isInterpolated()) {
            throw new BratException(String.format("This %s (%s) is already an interpolated copy",
                    target.getClass().getSimpleName(), target));
        }
    }

    /**
     * Interpolates every value in {@code map}, keeping each entry's full
     * {@link InterpolationOutcome} rather than just its resolved value.
     *
     * @param interpolation the interpolation implementation
     * @param runtimeData the runtime data
     * @param map the map whose values are to be interpolated, or {@code null}
     * @return a new map, same keys, each value replaced by its {@link InterpolationOutcome}; an
     *         empty map if {@code map} is {@code null}
     */
    public static Map<String, InterpolationOutcome> interpolateMapWithOutcomes(
            Interpolation interpolation, RuntimeData runtimeData, Map<String, String> map) {
        var result = new LinkedHashMap<String, InterpolationOutcome>();
        if (map == null) {
            return result;
        }
        for (var entry : map.entrySet()) {
            result.put(entry.getKey(), interpolation.outcome(entry.getValue(), runtimeData));
        }
        return result;
    }

    /**
     * Interpolates {@code value} and records it in {@code outcomes} under {@code field}, unless
     * {@code value} is {@code null}.
     *
     * @param interpolation the interpolation implementation
     * @param runtimeData the runtime data
     * @param outcomes the map to record the outcome in, keyed by {@code field}
     * @param field the key to record the outcome under
     * @param value the value to interpolate, or {@code null}
     * @return the outcome, or {@code null} if {@code value} was {@code null}
     */
    public static InterpolationOutcome interpolateIfPresent(Interpolation interpolation, RuntimeData runtimeData,
            Map<String, InterpolationOutcome> outcomes, String field, String value) {
        if (value == null) {
            return null;
        }
        var outcome = interpolation.outcome(value, runtimeData);
        outcomes.put(field, outcome);
        return outcome;
    }

    /**
     * @param outcome an outcome, or {@code null}
     * @return {@link InterpolationOutcome#asString()}, or {@code null} if {@code outcome} is
     *         {@code null}
     */
    public static String asStringOrNull(InterpolationOutcome outcome) {
        return outcome == null ? null : outcome.asString();
    }
}
