package dev.pbroman.brat.core.api.interpolation;

import dev.pbroman.brat.core.exception.BratException;

/**
 * The result of interpolating a single string: the resolved value together with a
 * human-readable, secret-masked description of what was substituted.
 *
 * @param value the resolved value; never {@code null}
 * @param reportingString a display string describing the substitution, always populated —
 *        for a literal with no {@code ${...}} tokens, this is simply the literal itself
 * @param containsSecret whether this outcome (or any outcome it was composed from) involved a
 *        secret; internal to the interpolation engine — not intended for use outside it
 */
public record InterpolationOutcome(Object value, String reportingString, boolean containsSecret) {

    /**
     * Validates that neither field is {@code null}.
     *
     * @throws BratException if {@code value} or {@code reportingString} is {@code null}
     */
    public InterpolationOutcome {
        if (value == null) {
            throw new BratException("InterpolationOutcome value must not be null");
        }
        if (reportingString == null) {
            throw new BratException("InterpolationOutcome reportingString must not be null");
        }
    }

    /**
     * Equivalent to {@link #InterpolationOutcome(Object, String, boolean)} with
     * {@code containsSecret} defaulted to {@code false}.
     */
    public InterpolationOutcome(Object value, String reportingString) {
        this(value, reportingString, false);
    }

    /**
     * Converts {@link #value()} to a string.
     *
     * @return {@link #value()} converted to a string
     */
    public String asString() {
        return value.toString();
    }
}