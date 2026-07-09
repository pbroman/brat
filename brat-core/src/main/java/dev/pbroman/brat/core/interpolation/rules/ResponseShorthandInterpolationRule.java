package dev.pbroman.brat.core.interpolation.rules;

import static dev.pbroman.brat.core.util.CheckUtils.checkInterpolationArgs;
import static dev.pbroman.brat.core.util.Constants.HEADERS;
import static dev.pbroman.brat.core.util.Constants.RESPONSE;
import static dev.pbroman.brat.core.util.Constants.RESPONSE_HEADER_SHORTHAND;
import static dev.pbroman.brat.core.util.Constants.RESPONSE_SHORTHAND;

import dev.pbroman.brat.core.api.interpolation.InterpolationRule;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.tools.InterpolationTools;
import org.apache.commons.lang3.StringUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * An {@link InterpolationRule} for shorthand response variables.
 */
@Slf4j
public final class ResponseShorthandInterpolationRule extends AbstractInterpolationRule {

    public static final int RESPONSE_SHORTHAND_PRIORITY = 10;

    /**
     * Constructs an {@link InterpolationRule} translating response variables to shorthand.
     *
     * @param tools the {@link InterpolationTools}
     */
    public ResponseShorthandInterpolationRule(InterpolationTools tools) {
        super(RESPONSE, tools);
    }

    @Override
    public int priority() {
        return RESPONSE_SHORTHAND_PRIORITY;
    }

    /**
     * Translates response variable to their respective shorthand versions, e.g. ${response.statusCode} to ${sc}
     * and ${response.json} to ${rj}. If the variable doesn't match a response variable, it is returned unaltered.
     *
     * @param input the variable
     * @param runtimeData not used for this case
     * @return a shorthand version of the variable, if present, otherwise the original variable
     */
    @Override
    public String resolve(String input, RuntimeData runtimeData) {
        if (StringUtils.isBlank(input)) {
            return input;
        }
        checkInterpolationArgs(input, runtimeData);
        var interpolation = simpleInterpolation(input, RESPONSE_SHORTHAND);
        if (tools.getVariablePattern().matcher(interpolation).find()) {
            return interpolation;
        }
        return tools.wrapAsVariable(interpolation);
    }

    @Override
    protected String onMissingReplacement(String placeholder, String input) {
        if (placeholder.startsWith(HEADERS)) {
            return placeholder.replaceFirst(HEADERS, RESPONSE_HEADER_SHORTHAND);
        }
        throw new BratException(String.format("The response variable '%s' is not defined.", placeholder));
    }
}