package dev.pbroman.brat.core.interpolation.rules;

import static dev.pbroman.brat.core.util.Constants.RESPONSE_STATUS_SHORTHAND;
import static dev.pbroman.brat.core.util.Constants.STATUS_CODE;

import dev.pbroman.brat.core.api.interpolation.InterpolationRule;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.tools.InterpolationTools;
import lombok.extern.slf4j.Slf4j;

/**
 * An {@link InterpolationRule} for status codes.
 */
@Slf4j
public final class ResponseStatusCodeInterpolationRule extends AbstractResponseInterpolationRule {

    /**
     * Constructs an {@link InterpolationRule} for status codes.
     *
     * @param tools the {@link InterpolationTools}
     */
    public ResponseStatusCodeInterpolationRule(InterpolationTools tools) {
        super(RESPONSE_STATUS_SHORTHAND,  tools);
    }

    @Override
    public String resolve(String input, RuntimeData runtimeData) {
        if (isRequirementsNotMet(input, runtimeData)) {
            return input;
        }
        var statusCode = runtimeData.getResponseVars().get(STATUS_CODE);
        if (statusCode == null) {
            throw new BratException("The response status code is not present.");
        }
        return statusCode.toString();
    }

}
