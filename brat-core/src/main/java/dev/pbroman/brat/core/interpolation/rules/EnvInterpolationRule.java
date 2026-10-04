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
     * <p>
     * The message names the key and says where an {@code env} value comes from — {@code env.yaml} —
     * because {@code env} is easily mistaken for the operating system's environment variables, which
     * it does not read.
     *
     * @throws BratException always: a missing {@code env} value fails the token
     */
    @Override
    protected String onMissingReplacement(String placeholder, String input, RuntimeData runtimeData) {
        throw new BratException(String.format("No '%s' in env: set it in env.yaml", placeholder));
    }
}
