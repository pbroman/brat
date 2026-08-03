package dev.pbroman.brat.core.interpolation.rules;

import static dev.pbroman.brat.core.util.CheckUtils.checkInterpolationArgs;
import static dev.pbroman.brat.core.util.Constants.RESPONSE_VARS;

import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.interpolation.InterpolationPatterns;
import org.apache.commons.lang3.StringUtils;

public abstract class AbstractResponseInterpolationRule extends AbstractInterpolationRule {

    protected AbstractResponseInterpolationRule(String interpolationKey, InterpolationPatterns patterns) {
        super(interpolationKey, patterns);
    }

    protected boolean isRequirementsNotMet(String input, RuntimeData runtimeData) {
        checkInterpolationArgs(input, runtimeData, RESPONSE_VARS);
        return StringUtils.isBlank(input) || !input.matches(patterns.getRegexForVariable(interpolationKey));
    }

}
