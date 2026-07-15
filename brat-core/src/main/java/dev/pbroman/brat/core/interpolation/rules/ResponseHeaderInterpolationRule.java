package dev.pbroman.brat.core.interpolation.rules;

import static dev.pbroman.brat.core.util.CheckUtils.checkInterpolationArgs;
import static dev.pbroman.brat.core.util.Constants.HEADERS;
import static dev.pbroman.brat.core.util.Constants.RESPONSE_HEADER_SHORTHAND;
import static dev.pbroman.brat.core.util.Constants.RESPONSE_VARS;

import java.util.Map;

import dev.pbroman.brat.core.api.interpolation.InterpolationRule;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.tools.InterpolationTools;
import lombok.extern.slf4j.Slf4j;

/**
 * An {@link InterpolationRule} for response headers.
 */
@Slf4j
public final class ResponseHeaderInterpolationRule extends AbstractResponseInterpolationRule {

    /**
     * Constructs an {@link InterpolationRule} for response headers.
     *
     * @param tools the {@link InterpolationTools}
     */
    public ResponseHeaderInterpolationRule(InterpolationTools tools) {
        super(RESPONSE_HEADER_SHORTHAND, tools);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public String resolve(String input, RuntimeData runtimeData) {
        checkInterpolationArgs(input, runtimeData, RESPONSE_VARS);
        if (runtimeData.getResponseVars().get(HEADERS) instanceof Map headers) {
            return simpleInterpolation(input, runtimeData, headers);
        }
        return input;
    }

    /**
     * {@inheritDoc}
     *
     * Additionally logs expected headers not present in the response.
     */
    @Override
    protected String onMissingReplacement(String placeholder, String input) {
        log.warn("The header {} is not in the response", placeholder);
        return super.onMissingReplacement(placeholder, input);
    }

}
