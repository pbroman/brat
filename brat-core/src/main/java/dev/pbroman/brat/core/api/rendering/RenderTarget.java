package dev.pbroman.brat.core.api.rendering;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.data.ConfigData;
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
     * Validates that {@code outcomes} is present, so that no rule is ever handed a {@code null} map,
     * and copies it so that no rule can mutate what it was handed.
     *
     * @throws BratException if {@code outcomes} is {@code null}
     */
    public RenderTarget {
        nonNull(outcomes, "The outcomes of a RenderTarget must not be null");
        outcomes = Collections.unmodifiableMap(new LinkedHashMap<>(outcomes));
    }

    /**
     * Equivalent to {@link #RenderTarget(String, Map)} with {@code label} defaulted to {@code null}.
     *
     * @param outcomes the named outcomes, in field order
     */
    public RenderTarget(Map<String, InterpolationOutcome> outcomes) {
        this(null, outcomes);
    }

    /**
     * The target for what an interpolated object resolved to, labelled with its type.
     * <p>
     * An as-authored object has no outcomes to render, so it yields no target rather than an empty
     * one: the caller can tell "nothing was interpolated" from "nothing needed substituting".
     *
     * @param data the object to render; must not be {@code null}
     * @return a target over {@code data}'s outcomes, labelled with the simple name of its runtime
     *         type ({@code "HttpRequestDefinition"}), if {@code data} is an interpolated copy;
     *         otherwise {@link Optional#empty()}
     * @throws BratException if {@code data} is {@code null}
     */
    public static Optional<RenderTarget> of(ConfigData data) {
        nonNull(data, "Cannot render a null ConfigData");
        if (!data.isInterpolated()) {
            return Optional.empty();
        }
        return Optional.of(new RenderTarget(data.getClass().getSimpleName(), data.getOutcomes()));
    }
}
