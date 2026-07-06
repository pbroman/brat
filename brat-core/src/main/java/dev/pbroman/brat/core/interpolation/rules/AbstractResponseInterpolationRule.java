package dev.pbroman.brat.core.interpolation.rules;

import static dev.pbroman.brat.core.util.CheckUtils.checkInterpolationArgs;
import static dev.pbroman.brat.core.util.Constants.RESPONSE_VARS;

import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.tools.InterpolationTools;
import org.apache.commons.lang3.StringUtils;

public abstract class AbstractResponseInterpolationRule extends AbstractInterpolationRule {

    protected AbstractResponseInterpolationRule(String interpolationKey, InterpolationTools tools) {
        super(interpolationKey, tools);
    }

    protected boolean isRequirementsNotMet(String input, RuntimeData runtimeData) {
        checkInterpolationArgs(input, runtimeData, RESPONSE_VARS);
        return StringUtils.isBlank(input) || !input.matches(tools.getRegexForVariable(interpolationKey));
    }

}
