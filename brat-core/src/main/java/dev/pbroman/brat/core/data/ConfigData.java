package dev.pbroman.brat.core.data;

import java.util.Map;

import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;

/**
 * Base class for BRAT's interpolatable, YAML-authored domain objects.
 */
public abstract class ConfigData {

    private final Map<String, InterpolationOutcome> outcomes;

    /**
     * Constructs a config data instance.
     *
     * @param outcomes the named interpolation outcomes of an interpolated copy, or {@code null} on
     *        an as-authored instance
     */
    protected ConfigData(Map<String, InterpolationOutcome> outcomes) {
        this.outcomes = outcomes;
    }

    /**
     * Whether this instance is an interpolated copy rather than the authored original.
     *
     * @return whether this instance is already an interpolated copy
     */
    public boolean isInterpolated() {
        return outcomes != null;
    }

    /**
     * The interpolation outcomes recorded on this copy, keyed by field name.
     *
     * @return this instance's named outcomes, or {@code null} if not yet interpolated
     */
    public Map<String, InterpolationOutcome> getOutcomes() {
        return outcomes;
    }
}
