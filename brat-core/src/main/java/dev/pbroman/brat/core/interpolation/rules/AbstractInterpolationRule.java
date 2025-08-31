package dev.pbroman.brat.core.interpolation.rules;

import static dev.pbroman.brat.core.util.Constants.VARIABLE_GROUP_NAME;
import static java.util.Objects.requireNonNull;

import java.util.Map;

import dev.pbroman.brat.core.api.interpolation.InterpolationRule;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.ValidationException;
import dev.pbroman.brat.core.tools.InterpolationTools;

public abstract class AbstractInterpolationRule implements InterpolationRule {

    protected final String interpolationKey;

    protected final InterpolationTools tools;

    protected AbstractInterpolationRule(String interpolationKey, InterpolationTools tools) {
        requireNonNull(interpolationKey, "The pattern of the interpolation must be set");
        this.interpolationKey = interpolationKey;
        this.tools = tools;
    }

    protected String simpleInterpolation(String input, Map<String, ?> values) throws ValidationException {
        requireNonNull(input, "The interpolation input must not be null");
        var matcher = tools.getGroupingPatternForVariable(interpolationKey).matcher(input);
        if (values == null || !matcher.find()) {
            return input;
        }
        var placeholder = matcher.group(VARIABLE_GROUP_NAME);
        if (!values.containsKey(placeholder)) {
            return onMissingReplacement(placeholder, input);
        }
        return values.get(placeholder).toString();
    }

    /**
     * 
     * Override this for logging or exceptions if a map doesn't contain the placeholder to replace.
     */
    protected String onMissingReplacement(String placeholder, String input) throws ValidationException {
        return input;
    }

}
