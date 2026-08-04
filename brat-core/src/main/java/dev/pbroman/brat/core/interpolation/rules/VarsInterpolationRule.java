package dev.pbroman.brat.core.interpolation.rules;

import dev.pbroman.brat.core.api.interpolation.InterpolationRule;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.interpolation.InterpolationPatterns;
import lombok.extern.slf4j.Slf4j;

import static dev.pbroman.brat.core.interpolation.InterpolationChecks.requireNamespaces;
import static dev.pbroman.brat.core.util.Constants.VARS;

/**
 * An {@link InterpolationRule} for variables.
 */
@Slf4j
public final class VarsInterpolationRule extends AbstractInterpolationRule {

    /**
     * Constructs an {@link InterpolationRule} for variables.
     *
     * @param patterns the {@link InterpolationPatterns}
     */
    public VarsInterpolationRule(InterpolationPatterns patterns) {
        super(VARS, patterns);
    }

    @Override
    public String resolve(String input, RuntimeData runtimeData) {
        requireNamespaces(runtimeData, VARS);
        return simpleInterpolation(input, runtimeData, runtimeData.getVars());
    }

    /**
     * {@inheritDoc}
     *
     * Logs a missing variable and returns an empty string.
     */
    @Override
    protected String onMissingReplacement(String placeholder, String input) {
        log.warn("The variable '{}' has not been set, replacing it with an empty string", placeholder);
        return "";
    }
}
