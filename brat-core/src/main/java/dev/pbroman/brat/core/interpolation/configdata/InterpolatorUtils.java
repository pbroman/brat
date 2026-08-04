package dev.pbroman.brat.core.interpolation.configdata;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.data.ConfigData;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;

/**
 * Helper methods shared by {@code ConfigDataInterpolator} implementations.
 */
public final class InterpolatorUtils {

    private InterpolatorUtils() {
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
            throw new BratException(String.format(
                    "This %s (%s) is already an interpolated copy",
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
    public static InterpolationOutcome interpolateIfPresent(
            Interpolation interpolation,
            RuntimeData runtimeData,
            Map<String, InterpolationOutcome> outcomes,
            String field,
            String value) {
        if (value == null) {
            return null;
        }
        var outcome = interpolation.outcome(value, runtimeData);
        outcomes.put(field, outcome);
        return outcome;
    }

    /**
     * Returns the outcome as string, if it is not {@code null}, then returns {@code null}.
     *
     * @param outcome an outcome, or {@code null}
     * @return {@link InterpolationOutcome#asString()}, or {@code null} if {@code outcome} is
     *         {@code null}
     */
    public static String asStringOrNull(InterpolationOutcome outcome) {
        return outcome == null ? null : outcome.asString();
    }

    /**
     * Interpolates an operand of any shape, recording one outcome per scalar leaf.
     * <p>
     * A mapping or a sequence is walked and rebuilt with its structure intact — only the scalar
     * leaves are interpolated, which is what keeps a structured operand comparable as a structure
     * rather than as its text form. Leaf outcomes are keyed by dotted path
     * ({@code b.address.street}, {@code b[0].id}), so a nested value never collides with the
     * operand's own key.
     *
     * @param interpolation the interpolation implementation
     * @param runtimeData the runtime data
     * @param outcomes the map to record leaf outcomes in
     * @param path the path of this value, used as the outcome key for a scalar
     * @param value the value to interpolate, of any shape, or {@code null}
     * @return the interpolated value, structure preserved; {@code null} if {@code value} was
     *         {@code null}
     */
    public static Object interpolateStructure(
            Interpolation interpolation,
            RuntimeData runtimeData,
            Map<String, InterpolationOutcome> outcomes,
            String path,
            Object value) {
        return switch (value) {
            case null -> null;
            case Map<?, ?> map -> {
                var interpolated = new LinkedHashMap<Object, Object>();
                for (var entry : map.entrySet()) {
                    interpolated.put(
                            entry.getKey(),
                            interpolateStructure(
                                    interpolation,
                                    runtimeData,
                                    outcomes,
                                    path + "." + entry.getKey(),
                                    entry.getValue()));
                }
                yield interpolated;
            }
            case List<?> list -> {
                var interpolated = new ArrayList<>();
                for (var i = 0; i < list.size(); i++) {
                    interpolated.add(interpolateStructure(
                            interpolation, runtimeData, outcomes, path + "[" + i + "]", list.get(i)));
                }
                yield interpolated;
            }
            default -> {
                var outcome = interpolateIfPresent(interpolation, runtimeData, outcomes, path, String.valueOf(value));
                yield outcome == null ? null : outcome.value();
            }
        };
    }
}
