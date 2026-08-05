package dev.pbroman.brat.core.interpolation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import static dev.pbroman.brat.core.util.Constants.VARIABLE_GROUP_NAME;

/**
 * The fixed syntax of an interpolation token, and the patterns matching it.
 * <p>
 * The delimiters are not configurable, deliberately: {@link TokenScanner} finds tokens by counting
 * braces, which only works against literal delimiters, so a configurable regex here would be a
 * promise the scanner could not keep.
 * <p>
 * Patterns are compiled once. The per-namespace patterns are cached rather than precompiled,
 * since the set of namespaces is open — a plugin brings its own.
 */
public final class InterpolationPatterns {

    /**
     * Opens a token.
     */
    public static final String TOKEN_PREFIX = "${";

    /**
     * Closes a token.
     */
    public static final String TOKEN_SUFFIX = "}";

    /**
     * Marks a token as a function call rather than a namespace lookup. Syntax, not part of the
     * function's name — a registry holds {@code uuid} and answers {@code ${__uuid}}.
     */
    public static final String FUNCTION_PREFIX = "__";

    /**
     * Opens a function call: the token prefix followed by the function marker. A token starting with
     * this is routed to the function evaluator rather than to the rule dispatcher.
     */
    public static final String FUNCTION_CALL_PREFIX = TOKEN_PREFIX + FUNCTION_PREFIX;

    /**
     * Matches a single token, lazily — so it stops at the first closing brace and does <em>not</em>
     * handle nesting. Use {@link TokenScanner} to find the tokens in a field; this answers the
     * simpler question of whether a string is, or holds, a token at all.
     */
    public static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\{.*?}");

    private static final Map<String, Pattern> GROUPING_PATTERNS = new ConcurrentHashMap<>();

    private InterpolationPatterns() {
        // no instances
    }

    /**
     * Wraps a bare name into token form, e.g. {@code rj.$.name} into {@code ${rj.$.name}}.
     *
     * @param variable the bare name
     * @return the name in token form
     */
    public static String wrapAsVariable(String variable) {
        return TOKEN_PREFIX + variable + TOKEN_SUFFIX;
    }

    /**
     * The regex matching one named token exactly, e.g. {@code ${sc}}.
     *
     * @param variable the token name
     * @return the regex
     */
    public static String regexForVariable(String variable) {
        return "\\$\\{" + variable + "}";
    }

    /**
     * The regex matching a {@code ${namespace.key}} token, capturing the key as a named group.
     */
    private static String groupingRegexForVariable(String variable) {
        return "\\$\\{" + variable + "\\.(?<" + VARIABLE_GROUP_NAME + ">.+)?}";
    }

    /**
     * The pattern matching a {@code ${namespace.key}} token, capturing the key as a named group.
     * <p>
     * Compiled once per namespace and memoised: this is called for every token by every rule the
     * dispatcher tries, so compiling per call cost roughly one compile per rule per token.
     *
     * @param variable the namespace
     * @return the pattern
     */
    public static Pattern groupingPatternForVariable(String variable) {
        return GROUPING_PATTERNS.computeIfAbsent(variable, key -> Pattern.compile(groupingRegexForVariable(key)));
    }
}
