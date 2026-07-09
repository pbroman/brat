package dev.pbroman.brat.core.data;

import java.util.LinkedHashMap;
import java.util.Map;

import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.data.runtime.RuntimeData;

/**
 * Base class for BRAT's interpolatable, YAML-authored domain objects.
 */
public abstract class ConfigData {

    /**
     * Gets an interpolated version of this object.
     *
     * @param interpolation - the interpolation implementation
     * @param runtimeData - the runtime data
     * @return a copy of the object with executed interpolations
     * @throws dev.pbroman.brat.core.exception.BratException if this instance is already an
     *         interpolated copy
     */
    public abstract ConfigData interpolated(Interpolation interpolation, RuntimeData runtimeData);

    /**
     * Interpolates every value in {@code map}, keeping each entry's full
     * {@link InterpolationOutcome} rather than just its resolved value.
     *
     * @param interpolation the interpolation implementation
     * @param runtimeData the runtime data
     * @param map the map whose values are to be interpolated
     * @return a new map, same keys, each value replaced by its {@link InterpolationOutcome}
     */
    protected Map<String, InterpolationOutcome> interpolateMapWithOutcomes(Interpolation interpolation,
                                                                            RuntimeData runtimeData,
                                                                            Map<String, String> map) {
        var result = new LinkedHashMap<String, InterpolationOutcome>();
        for (var entry : map.entrySet()) {
            result.put(entry.getKey(), interpolation.outcome(entry.getValue(), runtimeData));
        }
        return result;
    }
}
