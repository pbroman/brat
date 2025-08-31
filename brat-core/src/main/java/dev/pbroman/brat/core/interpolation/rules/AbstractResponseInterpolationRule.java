package dev.pbroman.brat.core.interpolation.rules;

import static dev.pbroman.brat.core.util.Constants.VARIABLE_GROUP_NAME;
import static java.util.Objects.requireNonNull;

import java.util.Map;

import dev.pbroman.brat.core.api.interpolation.InterpolationRule;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.ValidationException;
import dev.pbroman.brat.core.tools.InterpolationTools;

public abstract class AbstractResponseInterpolationRule extends AbstractInterpolationRule {

    protected AbstractResponseInterpolationRule(String interpolationKey, InterpolationTools tools) {
        super(interpolationKey, tools);
    }

    protected boolean isRequirementsNotMet(String input, RuntimeData runtimeData) {
        requireNonNull(input, "The input must not be null");
        requireNonNull(runtimeData, "The runtimeData must not be null");
        if (runtimeData.getResponseVars() == null) {
            throw new IllegalStateException("The response variables must not be null");
        }
        return !input.matches(tools.getRegexForVariable(interpolationKey));
    }

}
