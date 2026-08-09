package dev.pbroman.brat.core.api.rendering;

import java.util.Map;

import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.exception.BratException;

import static dev.pbroman.brat.core.util.Require.nonNull;

/**
 * What an {@link OutcomeRenderer} renders: one object's named interpolation outcomes, together with
 * an optional label naming what they describe.
 *
 * @param label a short name for what these outcomes describe, typically the rendered type's simple
 *        name ({@code "Auth"}, {@code "HttpRequestDefinition"}), or {@code null} when the caller has
 *        no name to give; a rule decides whether and how to show it
 * @param outcomes the named outcomes, in field order; never {@code null}, empty when there is
 *        nothing to render
 */
public record RenderTarget(String label, Map<String, InterpolationOutcome> outcomes) {

    /**
     * Validates that {@code outcomes} is present, so that no rule is ever handed a {@code null} map.
     *
     * @throws BratException if {@code outcomes} is {@code null}
     */
    public RenderTarget {
        nonNull(outcomes, "The outcomes of a RenderTarget must not be null");
    }

    /**
     * Equivalent to {@link #RenderTarget(String, Map)} with {@code label} defaulted to {@code null}.
     *
     * @param outcomes the named outcomes, in field order
     */
    public RenderTarget(Map<String, InterpolationOutcome> outcomes) {
        this(null, outcomes);
    }
}
