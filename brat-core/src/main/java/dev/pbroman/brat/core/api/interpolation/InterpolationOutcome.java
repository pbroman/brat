package dev.pbroman.brat.core.api.interpolation;

import dev.pbroman.brat.core.exception.BratException;

import static dev.pbroman.brat.core.util.Require.nonNull;

/**
 * The result of interpolating a single string: the resolved value together with a
 * human-readable, secret-masked description of what was substituted.
 *
 * @param value the resolved value, typed as the rule that produced it produced it. <strong>May be
 *        {@code null}</strong>, and only for one reason: a token resolved onto a JSON {@code null},
 *        which is a value the response really holds rather than a failure to resolve. A token that
 *        resolves to nothing at all does not reach here — each namespace's missing-value policy
 *        decides that first
 * @param reportingString a display string describing the substitution, always populated —
 *        for a literal with no {@code ${...}} tokens, this is simply the literal itself
 * @param containsSecret whether this outcome (or any outcome it was composed from) involved a
 *        secret; internal to the interpolation engine — not intended for use outside it
 */
public record InterpolationOutcome(Object value, String reportingString, boolean containsSecret) {

    /**
     * Validates that {@code reportingString} is present.
     * <p>
     * {@code value} is deliberately unchecked: see its own documentation for the one case that
     * makes it nullable.
     *
     * @throws BratException if {@code reportingString} is {@code null}
     */
    public InterpolationOutcome {
        nonNull(reportingString, "InterpolationOutcome reportingString must not be null");
    }

    /**
     * Equivalent to {@link #InterpolationOutcome(Object, String, boolean)} with
     * {@code containsSecret} defaulted to {@code false}.
     */
    public InterpolationOutcome(Object value, String reportingString) {
        this(value, reportingString, false);
    }

    /**
     * Converts {@link #value()} to a string, for a caller that needs text.
     * <p>
     * A {@code null} value is a failure here rather than the string {@code "null"} or a {@code null}
     * return: every caller of this method is building something that has to be text — a URL, a
     * header, a function argument, a body — and both alternatives put a wrong value on the wire
     * instead of reporting the problem. A caller that can represent a null, such as a condition
     * operand, reads {@link #value()} directly; a caller that only needs to display one renders it
     * itself rather than coming here.
     *
     * @return {@link #value()} converted to a string
     * @throws BratException if {@link #value()} is {@code null}. The message quotes
     *         {@link #reportingString()}, which names the token that resolved to it
     */
    public String asString() {
        if (value == null) {
            throw new BratException("A null value cannot be used as text: " + reportingString);
        }
        return value.toString();
    }
}
