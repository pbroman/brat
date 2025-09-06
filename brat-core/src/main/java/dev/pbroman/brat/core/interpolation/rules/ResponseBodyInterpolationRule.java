package dev.pbroman.brat.core.interpolation.rules;

import static dev.pbroman.brat.core.util.Constants.BODY;
import static dev.pbroman.brat.core.util.Constants.RESPONSE_BODY_SHORTHAND;

import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.ValidationException;
import dev.pbroman.brat.core.tools.InterpolationTools;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ResponseBodyInterpolationRule extends AbstractResponseInterpolationRule {

    public ResponseBodyInterpolationRule(InterpolationTools tools) {
        super(RESPONSE_BODY_SHORTHAND, tools);
    }

    @Override
    public String interpolate(String input, RuntimeData runtimeData) throws ValidationException {
        if (isRequirementsNotMet(input, runtimeData)) {
            return input;
        }
        return runtimeData.getResponseVars().get(BODY) == null
                ? null
                : runtimeData.getResponseVars().get(BODY).toString();
    }

}
