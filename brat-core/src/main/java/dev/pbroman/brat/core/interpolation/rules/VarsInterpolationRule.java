package dev.pbroman.brat.core.interpolation.rules;

import static dev.pbroman.brat.core.util.CheckUtils.checkInterpolationArgs;
import static dev.pbroman.brat.core.util.Constants.VARS;

import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.ValidationException;
import dev.pbroman.brat.core.tools.InterpolationTools;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class VarsInterpolationRule extends AbstractInterpolationRule {

    public VarsInterpolationRule(InterpolationTools tools) {
        super(VARS, tools);
    }

    @Override
    public String interpolate(String input, RuntimeData runtimeData) throws ValidationException {
        checkInterpolationArgs(input, runtimeData, VARS);
        return simpleInterpolation(input, runtimeData.getVars());
    }

    @Override
    protected String onMissingReplacement(String placeholder, String input) {
        log.warn("The variable '{}' has not been set, replacing it with an empty string", placeholder);
        return "";
    }
}
