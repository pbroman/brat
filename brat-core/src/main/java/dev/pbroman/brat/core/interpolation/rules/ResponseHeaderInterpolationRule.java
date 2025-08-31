package dev.pbroman.brat.core.interpolation.rules;

import static dev.pbroman.brat.core.util.Constants.HEADERS;
import static dev.pbroman.brat.core.util.Constants.RESPONSE_HEADER_SHORTHAND;
import static java.util.Objects.requireNonNull;

import java.util.Map;

import dev.pbroman.brat.core.exception.ValidationException;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.tools.InterpolationTools;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ResponseHeaderInterpolationRule extends AbstractResponseInterpolationRule {

    public ResponseHeaderInterpolationRule(InterpolationTools tools) {
        super(RESPONSE_HEADER_SHORTHAND, tools);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public String interpolate(String input, RuntimeData runtimeData) throws ValidationException {
        requireNonNull(runtimeData, "runtimeData must not be null");
        requireNonNull(runtimeData.getResponseVars(), "Response headers must not be null");
        if (runtimeData.getResponseVars().get(HEADERS) instanceof Map headers) {
            return simpleInterpolation(input, headers);
        }
        return input;
    }

    @Override
    protected String onMissingReplacement(String placeholder, String input) throws ValidationException {
        log.warn("The header {} is not in the response", placeholder);
        return super.onMissingReplacement(placeholder, input);
    }

}
