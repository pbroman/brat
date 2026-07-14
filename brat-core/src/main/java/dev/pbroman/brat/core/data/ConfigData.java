package dev.pbroman.brat.core.data;

import java.util.Map;

import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;

/**
 * Base class for BRAT's interpolatable, YAML-authored domain objects.
 */
public abstract class ConfigData {

    private final Map<String, InterpolationOutcome> outcomes;

    protected ConfigData(Map<String, InterpolationOutcome> outcomes) {
        this.outcomes = outcomes;
    }

    /**
     * @return whether this instance is already an interpolated copy
     */
    public boolean isInterpolated() {
        return outcomes != null;
    }

    /**
     * @return this instance's named outcomes, or {@code null} if not yet interpolated
     */
    public Map<String, InterpolationOutcome> getOutcomes() {
        return outcomes;
    }
}
