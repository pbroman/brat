package dev.pbroman.brat.core.interpolation.rules;

import static dev.pbroman.brat.core.util.CheckUtils.checkInterpolationArgs;
import static dev.pbroman.brat.core.util.Constants.CONSTANTS;
import static dev.pbroman.brat.core.util.Constants.ENV;
import static java.util.Objects.requireNonNull;

import dev.pbroman.brat.core.exception.ValidationException;
import dev.pbroman.brat.core.data.result.ValidationType;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.tools.InterpolationTools;

public class EnvInterpolationRule extends AbstractInterpolationRule {

    public EnvInterpolationRule(InterpolationTools tools) {
        super(ENV, tools);
    }

    @Override
    public String interpolate(String input, RuntimeData runtimeData) throws ValidationException {
        checkInterpolationArgs(input, runtimeData, ENV);
        return simpleInterpolation(input, runtimeData.getEnv());
    }

    @Override
    protected String onMissingReplacement(String placeholder, String input) throws ValidationException {
        throw new ValidationException("The environment variable '" + placeholder + "' is not set.", ValidationType.FAIL);
    }
}
