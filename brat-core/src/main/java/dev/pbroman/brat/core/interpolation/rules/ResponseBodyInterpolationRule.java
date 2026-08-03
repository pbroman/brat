package dev.pbroman.brat.core.interpolation.rules;

import static dev.pbroman.brat.core.util.Constants.BODY;
import static dev.pbroman.brat.core.util.Constants.RESPONSE_BODY_SHORTHAND;

import dev.pbroman.brat.core.api.interpolation.InterpolationRule;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.interpolation.InterpolationPatterns;

/**
 * An {@link InterpolationRule} for a response body.
 */
public final class ResponseBodyInterpolationRule extends AbstractResponseInterpolationRule {

    /**
     * Constructs an {@link InterpolationRule} for a response body.
     *
     * @param patterns the {@link InterpolationPatterns}
     */
    public ResponseBodyInterpolationRule(InterpolationPatterns patterns) {
        super(RESPONSE_BODY_SHORTHAND, patterns);
    }

    @Override
    public String resolve(String input, RuntimeData runtimeData) {
        if (isRequirementsNotMet(input, runtimeData)) {
            return input;
        }
        var body = runtimeData.getResponseVars().get(BODY);
        if (body == null) {
            throw new BratException("The response body is not present.");
        }
        return body.toString();
    }

}
