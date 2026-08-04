package dev.pbroman.brat.core.interpolation.rules;

import dev.pbroman.brat.core.api.interpolation.InterpolationRule;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.interpolation.InterpolationPatterns;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import static dev.pbroman.brat.core.interpolation.InterpolationChecks.requireNamespaces;
import static dev.pbroman.brat.core.util.Constants.RESPONSE;
import static dev.pbroman.brat.core.util.Constants.RESPONSE_SHORTHAND;

/**
 * An {@link InterpolationRule} for shorthand response variables.
 */
@Slf4j
public final class ResponseShorthandInterpolationRule extends AbstractInterpolationRule {

    public static final int RESPONSE_SHORTHAND_PRIORITY = 10;

    /**
     * Constructs an {@link InterpolationRule} translating response variables to shorthand.
     *
     * @param patterns the {@link InterpolationPatterns}
     */
    public ResponseShorthandInterpolationRule(InterpolationPatterns patterns) {
        super(RESPONSE, patterns);
    }

    @Override
    public int priority() {
        return RESPONSE_SHORTHAND_PRIORITY;
    }

    /**
     * Translates a long-form response variable into its shorthand, e.g. {@code ${response.statusCode}}
     * to {@code ${sc}} and {@code ${response.json.$.name}} to {@code ${rj.$.name}} — the rule that
     * actually resolves the value then works on the shorthand alone.
     * <p>
     * A variable carrying a path after the response variable keeps it: only the leading segment is
     * translated, so {@code headers.Content-Type} and {@code json.$.items[0]} come through with
     * their path intact. If the input is not a response variable at all it is returned unaltered.
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
        requireNamespaces(runtimeData);
        var interpolation = simpleInterpolation(input, runtimeData, RESPONSE_SHORTHAND);
        if (patterns.getVariablePattern().matcher(interpolation).find()) {
            return interpolation;
        }
        return patterns.wrapAsVariable(interpolation);
    }

    /**
     * Translates a response variable carrying a path, which the exact-match lookup could not resolve:
     * the leading segment is replaced by its shorthand and the rest of the path is kept.
     *
     * @throws BratException if the leading segment names no known response variable
     */
    @Override
    protected String onMissingReplacement(String placeholder, String input) {
        var responseVariable = StringUtils.substringBefore(placeholder, ".");
        var shorthand = RESPONSE_SHORTHAND.get(responseVariable);
        if (shorthand != null) {
            return shorthand + placeholder.substring(responseVariable.length());
        }
        throw new BratException(String.format("The response variable '%s' is not defined.", placeholder));
    }
}
