package dev.pbroman.brat.core.data;

import java.util.HashMap;
import java.util.Map;

import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.data.runtime.RuntimeData;

public abstract class ConfigData {

    /**
     * Gets an interpolated version of this object.
     * <p>
     * The interpolated version should contain the original.
     *
     * @param interpolation - the interpolation implementation
     * @param runtimeData - the runtime data
     * @return a copy of the object with executed interpolations
     */
    public abstract ConfigData interpolated(Interpolation interpolation, RuntimeData runtimeData);

    protected String getNonInterpolatedStringForMessage(Object nonInterpolated, Object interpolated)  {
        return nonInterpolated == null || String.valueOf(interpolated).equals(String.valueOf(nonInterpolated))
                ? ""
                : String.format("(original: %s) ", nonInterpolated);
    }

    protected Map<String, String> interpolateMap(Interpolation interpolation, RuntimeData runtimeData, Map<String, String> map) {
        var interpolated = new HashMap<String, String>();
        for(Map.Entry<String, String> entry : map.entrySet()) {
            interpolated.put(entry.getKey(), interpolation.interpolate(entry.getValue(), runtimeData));
        }
        return interpolated;
    }
}
