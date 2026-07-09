package dev.pbroman.brat.core.interpolation.rules;

import static dev.pbroman.brat.core.util.CheckUtils.checkInterpolationArgs;
import static dev.pbroman.brat.core.util.Constants.ENV;

import dev.pbroman.brat.core.api.interpolation.InterpolationRule;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.tools.InterpolationTools;

/**
 * An {@link InterpolationRule} for environment values.
 */
public final class EnvInterpolationRule extends AbstractInterpolationRule {

    /**
     * Constructs an {@link InterpolationRule} for environment values.
     *
     * @param tools the {@link InterpolationTools}
     */
    public EnvInterpolationRule(InterpolationTools tools) {
        super(ENV, tools);
    }

    @Override
    public String resolve(String input, RuntimeData runtimeData) {
        checkInterpolationArgs(input, runtimeData, ENV);
        return simpleInterpolation(input, runtimeData.getEnv());
    }

    /**
     * {@inheritDoc}
     *
     * @throws BratException on missing replacement for an environment value.
     */
    @Override
    protected String onMissingReplacement(String placeholder, String input) {
        throw new BratException("The environment variable '" + placeholder + "' is not set.");
    }
}