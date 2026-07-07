package dev.pbroman.brat.core.api.interpolation;

import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;

/**
 * Resolves {@code ${...}} variable references within a string against a {@link RuntimeData}.
 */
public interface Interpolation {

    /**
     * An interpolation takes a string (of the format "${.+}"). If the string matches the pattern of the rule,
     * the rule replaces it with a value using the {@link RuntimeData}. If the pattern matches but no value can be
     * found, it may either return the string unchanged (for another rule to process), replace the string,
     * or throw a {@link BratException}.
     *
     * @param input the variable to be interpolated
     * @param runtimeData the object containing values
     * @return a string with the result
     * @throws BratException if the pattern matches but no value can be found and the
     *         implementation chooses to fail rather than pass through or substitute a default
     */
    String interpolate(String input, RuntimeData runtimeData);

}