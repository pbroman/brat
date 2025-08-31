package dev.pbroman.brat.core.interpolation.rules;

import static dev.pbroman.brat.core.util.Constants.BODY;
import static dev.pbroman.brat.core.util.Constants.RESPONSE_STATUS_SHORTHAND;
import static dev.pbroman.brat.core.util.Constants.STATUS_CODE;
import static java.util.Objects.requireNonNull;

import dev.pbroman.brat.core.exception.ValidationException;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.tools.InterpolationTools;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ResponseStatusCodeInterpolationRule extends AbstractResponseInterpolationRule {

    public ResponseStatusCodeInterpolationRule(InterpolationTools tools) {
        super(RESPONSE_STATUS_SHORTHAND,  tools);
    }

    @Override
    public String interpolate(String input, RuntimeData runtimeData) throws ValidationException {
        if (isRequirementsNotMet(input, runtimeData)) {
            return input;
        }
        return runtimeData.getResponseVars().get(STATUS_CODE) == null
                ? null
                : runtimeData.getResponseVars().get(STATUS_CODE).toString();
    }

}
