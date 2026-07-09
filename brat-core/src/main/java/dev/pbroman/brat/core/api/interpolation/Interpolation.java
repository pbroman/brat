package dev.pbroman.brat.core.api.interpolation;

import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;

/**
 * Resolves {@code ${...}} variable references within a string against a {@link RuntimeData}.
 */
public interface Interpolation {

    /**
     * Interpolates a string (of the format "${.+}") and reports what happened. If the string
     * matches the pattern of the rule, the rule replaces it with a value using the
     * {@link RuntimeData}. If the pattern matches but no value can be found, it may either
     * return the input unchanged (for another rule to process), substitute a default, or throw a
     * {@link BratException}.
     *
     * @param input the variable to be interpolated
     * @param runtimeData the object containing values
     * @return the resolved value together with a reporting string describing the substitution
     * @throws BratException if {@code input} is {@code null}, or if the pattern matches but no
     *         value can be found and the implementation chooses to fail rather than pass through
     *         or substitute a default
     */
    InterpolationOutcome outcome(String input, RuntimeData runtimeData);

    /**
     * Convenience for callers that only need the resolved value, not the reporting string.
     *
     * @param input the variable to be interpolated
     * @param runtimeData the object containing values
     * @return {@link InterpolationOutcome#value()} of {@link #outcome(String, RuntimeData)}
     * @throws BratException under the same condition as {@link #outcome(String, RuntimeData)}
     */
    default Object interpolate(String input, RuntimeData runtimeData) {
        return outcome(input, runtimeData).value();
    }
}