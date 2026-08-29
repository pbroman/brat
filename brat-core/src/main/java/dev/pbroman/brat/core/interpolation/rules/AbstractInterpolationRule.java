package dev.pbroman.brat.core.interpolation.rules;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.api.interpolation.InterpolationRule;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.interpolation.InterpolationPatterns;
import org.apache.commons.lang3.StringUtils;

import static dev.pbroman.brat.core.util.Constants.CONSTANTS;
import static dev.pbroman.brat.core.util.Constants.ENV;
import static dev.pbroman.brat.core.util.Constants.FALLBACK_DELIMITER;
import static dev.pbroman.brat.core.util.Constants.PARAMS;
import static dev.pbroman.brat.core.util.Constants.VARIABLE_GROUP_NAME;
import static dev.pbroman.brat.core.util.Constants.VARS;
import static dev.pbroman.brat.core.util.Require.nonNull;

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

    /**
     * Constructs an {@link InterpolationRule} for one namespace.
     *
     * @param interpolationKey the interpolation key
     */
    protected AbstractInterpolationRule(String interpolationKey) {
        nonNull(interpolationKey, "The pattern of the interpolation must be set");
        this.interpolationKey = interpolationKey;
    }

    /**
     * Resolves a single interpolation token against this rule's namespace.
     * <p>
     * <strong>Only ever called for a token this rule owns</strong> — {@link #claims(String)} has
     * already matched it — so an implementation never has to recognize a token, only resolve one. It
     * must return a value; declining is not available here and is not needed.
     *
     * @param input the token to resolve, e.g. {@code "${vars.userId}"}, already known to be in this
     *        rule's namespace
     * @param runtimeData the object containing values
     * @return the resolved value; never {@code null}. Returning {@code input} means "resolved to its
     *         own text", <strong>not</strong> "not mine" — the token is claimed either way
     * @throws BratException if no value can be found and this namespace's policy is to fail rather
     *         than substitute a default
     */
    protected abstract String resolve(String input, RuntimeData runtimeData);

    /**
     * Whether {@code input} is a token of this rule's namespace.
     * <p>
     * This is the decline decision, and it lives here rather than in {@link #resolve} so that no
     * subclass has to spell "not mine" as a return value.
     * <p>
     * The default answers {@code true} for a string that is <em>one whole token</em>
     * ({@link InterpolationPatterns#isToken(String)}) in this rule's {@code interpolationKey}
     * namespace. Both halves matter: a rule is handed one token at a time, so text merely
     * <em>containing</em> one is not this rule's to resolve. A subclass whose namespace is not a
     * single {@code ${key.rest}} shape — one whose token carries no key at all, say — overrides this.
     *
     * @param input the token to test; never {@code null}
     * @return whether this rule owns {@code input}
     */
    protected boolean claims(String input) {
        return InterpolationPatterns.isToken(input)
                && InterpolationPatterns.groupingPatternForVariable(interpolationKey)
                        .matcher(input)
                        .find();
    }

    /**
     * Whether {@code input} is this rule's namespace and nothing else — the {@link #claims(String)}
     * a namespace carrying no key needs, e.g. {@code ${response.body}}.
     * <p>
     * The whole string must be the token, so {@code ${response.bodyish}} and
     * {@code "x ${response.body}"} are both rejected, and the namespace is matched literally rather
     * than as a regex.
     *
     * @param input the token to test; never {@code null}
     * @return whether {@code input} is exactly this rule's namespace in token form
     */
    protected final boolean claimsExactToken(String input) {
        return input.matches(InterpolationPatterns.regexForVariable(interpolationKey));
    }

    /**
     * Declines a token of another namespace, and otherwise wraps {@link #resolve}'s result.
     * <p>
     * Ownership is decided by {@link #claims(String)} <em>before</em> {@link #resolve} is called, so
     * a resolved value equal to the token is an ordinary answer rather than a decline — the
     * ambiguity that a pass-through convention would create cannot arise.
     * <p>
     * The reporting string is just the resolved value where it equals {@code input}, and
     * {@code input + " → " + resolved} otherwise; a value that happens to equal its own token is
     * reported as unchanged.
     *
     * @param input the token to resolve
     * @param runtimeData the object containing values
     * @return the outcome of resolving {@code input}, or {@link java.util.Optional#empty()} if
     *         {@link #claims(String)} rejects it
     * @throws BratException if {@code input} is {@code null}, or under the same condition as
     *         {@link #resolve(String, RuntimeData)}
     */
    @Override
    public final Optional<InterpolationOutcome> outcome(String input, RuntimeData runtimeData) {
        nonNull(input, "Cannot interpolate a null input");
        if (!claims(input)) {
            return Optional.empty();
        }
        var resolved = resolve(input, runtimeData);
        return Optional.of(
                resolved.equals(input)
                        ? new InterpolationOutcome(resolved, resolved)
                        : new InterpolationOutcome(resolved, input + " → " + resolved));
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
     * <p>
     * Three cases short-circuit before any lookup is attempted, each returning {@code input}
     * untouched: {@code input} is {@code null}, empty or blank; {@code values} is {@code null}; or
     * {@code input} does not match this rule's namespace pattern. Per
     * {@link #resolve(String, RuntimeData)}, returning {@code input} resolves the token to its own
     * text — the token stays claimed and no other rule is consulted. In none of the three is
     * {@link #onMissingReplacement(String, String)} reached.
     * <p>
     * <strong>Only the {@code values} case is reachable by way of {@link #outcome}.</strong> The
     * other two guard a subclass that calls this method itself: {@link #claims(String)} runs first
     * and rejects a blank string for not being a token, and the default {@code claims} matches on
     * the very pattern the third case tests, so neither can survive to here on the dispatched path.
     * They are kept because this method is part of the extension surface, not because core reaches
     * them.
     *
     * @param input the input to be interpolated, or {@code null}
     * @param runtimeData the object containing values, used to resolve fallback segments that
     *        reference another namespace
     * @param values a map of replacements for this rule's own namespace, or {@code null} to return
     *        {@code input} unchanged
     * @return the replacement string, or {@code input} unchanged in each of the three
     *         short-circuiting cases above
     * @throws BratException under the same condition as {@link #onMissingReplacement(String, String)},
     *         if the fallback chain is exhausted without resolving to a value
     */
    protected String simpleInterpolation(String input, RuntimeData runtimeData, Map<String, ?> values) {
        if (StringUtils.isBlank(input)) {
            return input;
        }
        var matcher = InterpolationPatterns.groupingPatternForVariable(interpolationKey)
                .matcher(input);
        if (values == null || !matcher.find()) {
            return input;
        }
        var chain = matcher.group(VARIABLE_GROUP_NAME).split(Pattern.quote(FALLBACK_DELIMITER), -1);
        var key = chain[0];
        if (values.containsKey(key)) {
            return values.get(key).toString();
        }
        // orElseGet, not orElse: an override of onMissingReplacement may throw or log, so it must
        // run only when no segment resolved.
        return Arrays.stream(chain, 1, chain.length)
                .map(segment -> resolveFallbackSegment(segment, runtimeData))
                .filter(Objects::nonNull)
                .findFirst()
                .orElseGet(() -> onMissingReplacement(key, input));
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
