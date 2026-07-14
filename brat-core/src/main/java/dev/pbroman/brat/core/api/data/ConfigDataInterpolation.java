package dev.pbroman.brat.core.api.data;

import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.data.ConfigData;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;

/**
 * Interpolates every field of one concrete {@link ConfigData} type.
 *
 * @param <T> the concrete {@link ConfigData} type this implementation interpolates
 */
public interface ConfigDataInterpolation<T extends ConfigData> {

    /**
     * Interpolates every field of {@code target} and returns a new instance.
     *
     * @param target the object to interpolate
     * @param interpolation the interpolation implementation
     * @param runtimeData the runtime data
     * @return a new instance with every field interpolated; {@link ConfigData#getOutcomes()} on
     *         the result holds every field's {@link InterpolationOutcome}, keyed by field name,
     *         in field-declaration order
     * @throws BratException if {@code target} is already an interpolated copy
     */
    T interpolated(T target, Interpolation interpolation, RuntimeData runtimeData);
}
