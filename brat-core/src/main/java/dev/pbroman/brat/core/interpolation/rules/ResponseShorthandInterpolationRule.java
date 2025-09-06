package dev.pbroman.brat.core.interpolation.rules;

import static dev.pbroman.brat.core.util.CheckUtils.checkInterpolationArgs;
import static dev.pbroman.brat.core.util.Constants.HEADERS;
import static dev.pbroman.brat.core.util.Constants.RESPONSE;
import static dev.pbroman.brat.core.util.Constants.RESPONSE_HEADER_SHORTHAND;
import static dev.pbroman.brat.core.util.Constants.RESPONSE_SHORTHAND;
import static dev.pbroman.brat.core.util.Constants.RESPONSE_VARS;
import static java.util.Objects.requireNonNull;

import dev.pbroman.brat.core.exception.ValidationException;
import dev.pbroman.brat.core.data.result.ValidationType;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.tools.InterpolationTools;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ResponseShorthandInterpolationRule extends AbstractInterpolationRule {

    public static final int RESPONSE_SHORTHAND_PRIORITY = 10;

    public ResponseShorthandInterpolationRule(InterpolationTools tools) {
        super(RESPONSE, tools);
    }

    @Override
    public int priority() {
        return RESPONSE_SHORTHAND_PRIORITY;
    }

    /**
     * Translates response variable to their respektive shorthand versions, e.g. ${response.statusCode} to ${sc}
     * and ${response.json} to ${rj}. If the variable doesn't match a response variable, it is returned unaltered.
     *
     * @param input the variable
     * @param runtimeData not used for this case
     * @return a shorthand versions of the variable, if present, otherwise the original variable
     */
    @Override
    public String interpolate(String input, RuntimeData runtimeData) throws ValidationException {
        checkInterpolationArgs(input, runtimeData);
        var interpolation = simpleInterpolation(input, RESPONSE_SHORTHAND);
        if (tools.getVariablePattern().matcher(interpolation).find()) {
            return interpolation;
        }
        return tools.wrapAsVariable(interpolation);
    }

    @Override
    protected String onMissingReplacement(String placeholder, String input) throws ValidationException {
        if (placeholder.startsWith(HEADERS)) {
            return placeholder.replaceFirst(HEADERS, RESPONSE_HEADER_SHORTHAND);
        }
        throw new ValidationException(String.format("The header %s is not defined.", placeholder), ValidationType.FAIL);
    }
}
