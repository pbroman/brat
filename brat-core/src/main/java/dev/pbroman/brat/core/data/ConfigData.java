package dev.pbroman.brat.core.data;

import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.ValidationException;

public abstract class ConfigData {

    /**
     * Interpolates all values of the config data object that should be interpolated and saves the original values
     * in the object.
     */
    public abstract void interpolate(Interpolation interpolation, RuntimeData runtimeData) throws ValidationException;

}
