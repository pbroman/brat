package dev.pbroman.brat.core.interpolation.rules;

import static dev.pbroman.brat.core.util.CheckUtils.checkInterpolationArgs;
import static dev.pbroman.brat.core.util.Constants.CONSTANTS;

import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.tools.InterpolationTools;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ConstantsInterpolationRule extends AbstractInterpolationRule {

    public ConstantsInterpolationRule(InterpolationTools tools) {
        super(CONSTANTS, tools);
    }

    @Override
    public String interpolate(String input, RuntimeData runtimeData) {
        checkInterpolationArgs(input, runtimeData, CONSTANTS);
        return simpleInterpolation(input, runtimeData.getConstants());
    }

    @Override
    protected String onMissingReplacement(String placeholder, String input) {
        throw new BratException("The constant '" + placeholder + "' is not set.");
    }

}