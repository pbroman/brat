package dev.pbroman.brat.core.interpolation.rules;

import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.api.interpolation.InterpolationRule;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.tools.InterpolationTools;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;

import static dev.pbroman.brat.core.util.Constants.VARIABLE_GROUP_NAME;
import static dev.pbroman.brat.core.util.ExceptionUtil.bratExceptionOnNull;

/**
 * Base class for priority-dispatched {@link InterpolationRule} implementations that resolve a
 * single {@code ${namespace.key}}-shaped token against a fixed namespace of values.
 */
public abstract class AbstractInterpolationRule implements InterpolationRule {

    protected final String interpolationKey;

    protected final InterpolationTools tools;

    /**
     * Constructs an {@link InterpolationRule} with a key and {@link InterpolationTools}.
     *
     * @param interpolationKey the interpolation key
     * @param tools the tools
     */
    protected AbstractInterpolationRule(String interpolationKey, InterpolationTools tools) {
        bratExceptionOnNull(interpolationKey, "The pattern of the interpolation must be set");
        this.interpolationKey = interpolationKey;
        this.tools = tools;
    }

    /**
     * Resolves a single interpolation token against this rule's namespace.
     *
     * @param input the token to resolve, e.g. {@code "${vars.userId}"}
     * @param runtimeData the object containing values
     * @return the resolved value, or {@code input} unchanged if this rule's pattern does not
     *         match it (letting another rule in the dispatch chain process it instead)
     * @throws BratException if the pattern matches but no value can be found and the
     *         implementation chooses to fail rather than pass through or substitute a default
     */
    protected abstract String resolve(String input, RuntimeData runtimeData);

    /**
     * Wraps {@link #resolve(String, RuntimeData)}'s result into an {@link InterpolationOutcome}:
     * a reporting string of just {@code resolved} if nothing changed (this rule passed the input
     * through), or {@code input + " → " + resolved} if it substituted a value.
     * <p>
     * Substitution is detected by comparing {@code resolved} to {@code input} — if a resolved
     * value happens to equal the input, this reports it as an unchanged passthrough even if
     * {@link #resolve(String, RuntimeData)} actually substituted a value.
     *
     * @param input the token to resolve
     * @param runtimeData the object containing values
     * @return the outcome of resolving {@code input} via {@link #resolve(String, RuntimeData)}
     * @throws BratException if {@code input} is {@code null}, or under the same condition as
     *         {@link #resolve(String, RuntimeData)}
     */
    @Override
    public final InterpolationOutcome outcome(String input, RuntimeData runtimeData) {
        bratExceptionOnNull(input, "Cannot interpolate a null input");
        var resolved = resolve(input, runtimeData);
        return resolved.equals(input)
                ? new InterpolationOutcome(resolved, resolved)
                : new InterpolationOutcome(resolved, input + " → " + resolved);
    }

    /**
     * Performs a simple interpolation, replacing input keys with values from a map.
     * <p>
     * Calls {@link #onMissingReplacement(String, String)} if the input key is not in the map.
     *
     * @param input the input to be interpolated
     * @param values a map of replacements
     * @return the replacement string
     */
    protected String simpleInterpolation(String input, Map<String, ?> values) {
        if (StringUtils.isBlank(input)) {
            return input;
        }
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
     * Returns a value in case there is no replacement in the {@link #simpleInterpolation(String, Map)} values map.
     * <p>
     * Returns the input per default. Override to change the behavior.
     *
     * @param placeholder the placeholder missing a replacement
     * @param input the original input string
     * @return the input string
     */
    protected String onMissingReplacement(String placeholder, String input) {
        return input;
    }

}