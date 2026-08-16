package dev.pbroman.brat.core.interpolation.rules;

import dev.pbroman.brat.core.api.interpolation.InterpolationRule;
import dev.pbroman.brat.core.data.runtime.RuntimeData;

import static dev.pbroman.brat.core.util.Constants.BODY;
import static dev.pbroman.brat.core.util.Constants.RESPONSE_BODY_SHORTHAND;
import static dev.pbroman.brat.core.util.Require.nonNull;

/**
 * An {@link InterpolationRule} for a response body.
 */
public final class ResponseBodyInterpolationRule extends AbstractResponseInterpolationRule {

    /**
     * Constructs an {@link InterpolationRule} for a response body.
     *
     */
    public ResponseBodyInterpolationRule() {
        super(RESPONSE_BODY_SHORTHAND);
    }

    @Override
    public String resolve(String input, RuntimeData runtimeData) {
        if (isRequirementsNotMet(input, runtimeData)) {
            return input;
        }
        var body = runtimeData.getResponseVars().get(BODY);
        nonNull(body, "The response body is not present.");
        return body.toString();
    }
}
