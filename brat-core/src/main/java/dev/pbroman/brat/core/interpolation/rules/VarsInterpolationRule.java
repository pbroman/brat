package dev.pbroman.brat.core.interpolation.rules;

import static dev.pbroman.brat.core.util.CheckUtils.checkInterpolationArgs;
import static dev.pbroman.brat.core.util.Constants.VARS;

import dev.pbroman.brat.core.api.interpolation.InterpolationRule;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.tools.InterpolationTools;
import lombok.extern.slf4j.Slf4j;

/**
 * An {@link InterpolationRule} for variables.
 */
@Slf4j
public final class VarsInterpolationRule extends AbstractInterpolationRule {

    /**
     * Constructs an {@link InterpolationRule} for variables.
     *
     * @param tools the {@link InterpolationTools}
     */
    public VarsInterpolationRule(InterpolationTools tools) {
        super(VARS, tools);
    }

    @Override
    public String resolve(String input, RuntimeData runtimeData) {
        checkInterpolationArgs(input, runtimeData, VARS);
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
