package dev.pbroman.brat.core.api;

import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.data.runtime.RuntimeData;

/**
 * Generic interface of a request definition.
 */
public interface RequestDefinition {

    /**
     * Gets an interpolated version of this object.
     *
     * @param interpolation - the interpolation implementation
     * @param runtimeData - the runtime data
     * @return a copy of the object with executed interpolations
     */
    RequestDefinition interpolated(Interpolation interpolation, RuntimeData runtimeData);
}
