package dev.pbroman.brat.core.interpolation.rules;

import dev.pbroman.brat.core.api.interpolation.InterpolationRule;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;

import static dev.pbroman.brat.core.interpolation.InterpolationChecks.requireNamespaces;
import static dev.pbroman.brat.core.util.Constants.ENV;

/**
 * An {@link InterpolationRule} for environment values.
 */
public final class EnvInterpolationRule extends AbstractInterpolationRule {

    /**
     * Constructs an {@link InterpolationRule} for environment values.
     *
     */
    public EnvInterpolationRule() {
        super(ENV);
    }

    @Override
    public String resolve(String input, RuntimeData runtimeData) {
        requireNamespaces(runtimeData, ENV);
        return simpleInterpolation(input, runtimeData, runtimeData.getEnv());
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
