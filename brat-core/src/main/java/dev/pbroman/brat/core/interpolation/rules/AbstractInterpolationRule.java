package dev.pbroman.brat.core.interpolation.rules;

import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.api.interpolation.InterpolationRule;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.tools.InterpolationTools;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static dev.pbroman.brat.core.util.Constants.CONSTANTS;
import static dev.pbroman.brat.core.util.Constants.ENV;
import static dev.pbroman.brat.core.util.Constants.FALLBACK_DELIMITER;
import static dev.pbroman.brat.core.util.Constants.PARAMS;
import static dev.pbroman.brat.core.util.Constants.VARIABLE_GROUP_NAME;
import static dev.pbroman.brat.core.util.Constants.VARS;
import static dev.pbroman.brat.core.util.ExceptionUtil.bratExceptionOnNull;

/**
 * Base class for priority-dispatched {@link InterpolationRule} implementations that resolve a
 * single {@code ${namespace.key}}-shaped token against a fixed namespace of values, with support
 * for a chained {@code :-} fallback (e.g. {@code ${params.threadCount:-env.threadCount:-10}}).
 */
public abstract class AbstractInterpolationRule implements InterpolationRule {

    /**
     * Namespaces a fallback-chain segment may reference by {@code namespace.key} — any other
     * shape (including a segment naming an unrecognized namespace) is treated as a literal.
     */
    private static final Set<String> FALLBACK_NAMESPACES = Set.of(CONSTANTS, ENV, VARS, PARAMS);

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
     * Performs a simple interpolation, replacing input keys with values from a map, following a
     * {@code :-} fallback chain if the key is missing.
     * <p>
     * The key captured after {@code namespace.} may be a chain of {@code :-}-separated segments,
     * e.g. {@code threadCount:-env.threadCount:-10} for input
     * {@code ${params.threadCount:-env.threadCount:-10}}. Resolution:
     * <ol>
     *     <li>The first segment is looked up in {@code values}. If present, its value is
     *         returned — no other segment is considered.</li>
     *     <li>Otherwise, each remaining segment is tried in order. A segment shaped
     *         {@code namespace.key} where {@code namespace} is one of {@code constants}/
     *         {@code env}/{@code vars}/{@code params} is resolved by looking {@code key} up in
     *         that namespace via {@code runtimeData}; if found, that value is returned and no
     *         further segment is considered. If that namespace doesn't have {@code key} either,
     *         the next segment is tried.</li>
     *     <li>Any other segment shape (including one naming an unrecognized namespace) is a
     *         literal default — it is returned as-is, terminating the chain. Since a literal
     *         always resolves, a chain only reaches {@link #onMissingReplacement(String, String)}
     *         if every segment was a namespace reference and none of them had the key.</li>
     * </ol>
     * A chain with only one segment (no {@code :-} present) behaves exactly as a plain,
     * fallback-free lookup.
     *
     * @param input the input to be interpolated
     * @param runtimeData the object containing values, used to resolve fallback segments that
     *        reference another namespace
     * @param values a map of replacements for this rule's own namespace
     * @return the replacement string
     * @throws BratException under the same condition as {@link #onMissingReplacement(String, String)},
     *         if the fallback chain is exhausted without resolving to a value
     */
    protected String simpleInterpolation(String input, RuntimeData runtimeData, Map<String, ?> values) {
        if (StringUtils.isBlank(input)) {
            return input;
        }
        var matcher = tools.getGroupingPatternForVariable(interpolationKey).matcher(input);
        if (values == null || !matcher.find()) {
            return input;
        }
        var chain = matcher.group(VARIABLE_GROUP_NAME).split(Pattern.quote(FALLBACK_DELIMITER), -1);
        var key = chain[0];
        if (values.containsKey(key)) {
            return values.get(key).toString();
        }
        for (var i = 1; i < chain.length; i++) {
            var resolved = resolveFallbackSegment(chain[i], runtimeData);
            if (resolved != null) {
                return resolved;
            }
        }
        return onMissingReplacement(key, input);
    }

    /**
     * Resolves one {@code :-}-separated fallback segment.
     *
     * @param segment one segment of a fallback chain
     * @param runtimeData the object containing values
     * @return the resolved value, if {@code segment} is a {@code namespace.key} reference to a
     *         fallback-eligible namespace and that namespace has {@code key}; the literal
     *         {@code segment} itself if it doesn't have that shape; {@code null} if it has that
     *         shape but the referenced namespace doesn't have {@code key} (try the next segment)
     */
    private static String resolveFallbackSegment(String segment, RuntimeData runtimeData) {
        var dotIndex = segment.indexOf('.');
        if (dotIndex > 0) {
            var namespace = segment.substring(0, dotIndex);
            if (FALLBACK_NAMESPACES.contains(namespace)) {
                var key = segment.substring(dotIndex + 1);
                var namespaceValues = runtimeData.getData(namespace);
                return namespaceValues != null && namespaceValues.containsKey(key)
                        ? namespaceValues.get(key).toString()
                        : null;
            }
        }
        return segment;
    }

    /**
     * Returns a value in case there is no replacement in the
     * {@link #simpleInterpolation(String, RuntimeData, Map)} values map and the fallback chain
     * (if any) is exhausted.
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
