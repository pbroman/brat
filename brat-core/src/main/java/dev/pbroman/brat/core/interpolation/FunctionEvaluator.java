package dev.pbroman.brat.core.interpolation;

import java.util.ArrayList;
import java.util.List;

import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;
import org.apache.commons.lang3.StringUtils;

import static dev.pbroman.brat.core.interpolation.InterpolationChecks.requireNamespaces;
import static dev.pbroman.brat.core.interpolation.InterpolationPatterns.FUNCTION_CALL_PREFIX;
import static dev.pbroman.brat.core.interpolation.InterpolationPatterns.TOKEN_SUFFIX;
import static dev.pbroman.brat.core.util.Require.nonNull;

/**
 * Resolves a {@code ${__name(arg, …)}} function call by resolving its arguments and invoking the
 * named function.
 * <p>
 * A call is not an {@link dev.pbroman.brat.core.api.interpolation.InterpolationRule}: a rule is a
 * pure lookup on one token, while a call must evaluate sub-templates of its own, and a rule that
 * needed the dispatcher owning it would be a cycle. {@link InterpolationScanner} therefore routes a
 * token beginning {@code ${__} } here instead of to the dispatcher, and hands over the interpolation
 * to resolve arguments with — as a parameter, so nothing is wired circularly.
 * <p>
 * <strong>Recursion is over the template, never over output.</strong> An argument is a substring of
 * the field as authored. A function's return value is never re-scanned, so a function returning the
 * text {@code ${secrets.apiKey}} yields exactly that text.
 * <p>
 * <strong>Argument boundaries come from the template, before any value exists.</strong> Arguments are
 * split on the authored text, so a resolved value containing a comma cannot change how many arguments
 * a call has.
 */
public final class FunctionEvaluator {

    private static final char QUOTE = '\'';

    private final FunctionRegistry registry;

    /**
     * Constructs an evaluator over the functions a run has available.
     *
     * @param registry the functions this evaluator can call
     * @throws BratException if {@code registry} is {@code null}
     */
    public FunctionEvaluator(FunctionRegistry registry) {
        nonNull(registry, "The function registry of an evaluator must be set");
        this.registry = registry;
    }

    /**
     * Resolves one {@code ${__name(arg, …)}} call.
     * <p>
     * The name is everything between the {@code __} marker and either the opening parenthesis or the
     * closing brace, and is matched case-insensitively. Both {@code ${__uuid}} and {@code ${__uuid()}}
     * are no-argument calls: an absent argument list and an empty one mean the same thing.
     * <p>
     * Arguments are split on commas <strong>at the top level only</strong>. A comma inside a nested
     * {@code ${…}} token, inside parentheses, or inside quotes belongs to the argument it sits in — so
     * {@code ${__join(${__list(a, b)}, c)}} passes two arguments, not three. Each argument is then
     * trimmed of surrounding whitespace and resolved through {@code interpolation}, exactly as a field
     * would be; an argument that is empty or only whitespace resolves to the empty string.
     * <p>
     * <strong>Quotes protect commas; they do not suppress interpolation.</strong> A single-quoted
     * argument is one argument however many commas it contains, and the quotes are stripped before the
     * function sees it — but a {@code ${…}} inside it still resolves. Quoting only applies when it
     * wraps a whole argument; a quote anywhere else is an ordinary character. Whitespace between a
     * closing quote and the comma or parenthesis that follows is ignored, so a quoted argument reaches
     * the function exactly as written, including any leading or trailing spaces inside the quotes.
     * <p>
     * <strong>A secret-bearing argument makes the result secret-bearing.</strong> Arguments resolve to
     * outcomes rather than values, and their {@code containsSecret} flags are OR-ed into the returned
     * outcome, whose {@code reportingString} then masks the value — so
     * {@code ${__base64(${secrets.x})}} cannot leak the encoded secret into a report.
     *
     * @param call the whole call token, delimiters included, as found by {@link TokenScanner}
     * @param interpolation resolves each argument as if it were a field; normally the
     *        {@link InterpolationScanner} that routed this call, which is what lets an argument hold
     *        a nested token or another call
     * @param runtimeData the values the arguments resolve against
     * @return the function's result, with {@code containsSecret} set if any argument was
     *         secret-bearing, and a {@code reportingString} of {@code call → result} with the result
     *         masked as {@code ***} when it is secret-bearing
     * @throws BratException if {@code call} is {@code null}, or is not a call token — one opening
     *         with {@link InterpolationPatterns#FUNCTION_CALL_PREFIX} and closing with
     *         {@link InterpolationPatterns#TOKEN_SUFFIX}; if {@code interpolation} is {@code null};
     *         if the argument list is unclosed or is not followed immediately by the closing brace;
     *         if a quoted argument has no closing quote; if no function is registered under the
     *         name; if the function throws; if the function returns {@code null}; or if
     *         {@code runtimeData} is {@code null}
     */
    public InterpolationOutcome evaluate(String call, Interpolation interpolation, RuntimeData runtimeData) {
        nonNull(call, "Cannot evaluate a null function call");
        nonNull(interpolation, "Cannot resolve a function's arguments without an interpolation");
        requireNamespaces(runtimeData);
        if (!call.startsWith(FUNCTION_CALL_PREFIX)
                || !call.endsWith(TOKEN_SUFFIX)
                || call.length() <= FUNCTION_CALL_PREFIX.length() + TOKEN_SUFFIX.length()) {
            throw new BratException("'" + call + "' is not a function call");
        }

        var closingBrace = call.length() - TOKEN_SUFFIX.length();
        var openingParen = call.indexOf('(', FUNCTION_CALL_PREFIX.length());
        // No bound check against closingBrace is needed: indexOf cannot reach past the last
        // character, and that character is the closing brace the guard above already required.
        var nameEnd = openingParen < 0 ? closingBrace : openingParen;
        var name = call.substring(FUNCTION_CALL_PREFIX.length(), nameEnd).trim();
        if (StringUtils.isBlank(name)) {
            throw new BratException("The function call '" + call + "' names no function");
        }
        var arguments = nameEnd == closingBrace ? List.<String>of() : argumentsOf(call, openingParen + 1);

        var values = new ArrayList<String>(arguments.size());
        var containsSecret = false;
        for (var argument : arguments) {
            var outcome = interpolation.outcome(argument, runtimeData);
            containsSecret |= outcome.containsSecret();
            values.add(outcome.asString());
        }

        var result = registry.get(name).apply(List.copyOf(values));
        nonNull(result, "The function '" + name + "' returned null; a function with no answer must throw");
        return new InterpolationOutcome(result, call + " → " + (containsSecret ? "***" : result), containsSecret);
    }

    /**
     * The unresolved argument substrings of {@code call}, split from the text as authored.
     * <p>
     * Splitting before anything is resolved is what stops a value containing a comma from changing
     * how many arguments a call has.
     *
     * @param call the whole call token
     * @param from the index just past the opening parenthesis
     * @return the arguments, trimmed unless quoted, with any wrapping quotes removed
     * @throws BratException if the argument list is unclosed, is not followed immediately by the
     *         token's closing brace, or holds an unclosed quote
     */
    private static List<String> argumentsOf(String call, int from) {
        var arguments = new ArrayList<String>();
        var current = new StringBuilder();
        var braceDepth = 0;
        var parenDepth = 0;
        var inQuote = false;
        var quoted = false;
        var index = from;
        var closed = false;
        while (index < call.length()) {
            var character = call.charAt(index);
            index++;
            if (inQuote) {
                if (character == QUOTE) {
                    inQuote = false;
                } else {
                    current.append(character);
                }
            } else if (character == QUOTE && current.toString().isBlank()) {
                // A quote only opens an argument; anywhere else it is an ordinary character, so
                // `it's` needs no escaping.
                inQuote = true;
                quoted = true;
                current.setLength(0);
            } else if (quoted && Character.isWhitespace(character)) {
                continue;
            } else if (braceDepth == 0 && parenDepth == 0 && character == ',') {
                arguments.add(quoted ? current.toString() : current.toString().trim());
                current.setLength(0);
                quoted = false;
            } else if (braceDepth == 0 && parenDepth == 0 && character == ')') {
                closed = true;
                break;
            } else {
                braceDepth += depthChange(character, '{', '}');
                parenDepth += depthChange(character, '(', ')');
                if (braceDepth < 0) {
                    // the token's own closing brace, reached before the argument list closed
                    break;
                }
                current.append(character);
            }
        }
        if (inQuote) {
            throw new BratException("The function call '" + call + "' has an unclosed quote");
        }
        if (!closed) {
            throw new BratException("The function call '" + call + "' has an unclosed argument list");
        }
        if (!call.substring(index).equals(TOKEN_SUFFIX)) {
            throw new BratException("The function call '" + call + "' has text after its argument list");
        }
        if (!arguments.isEmpty() || quoted || !current.toString().isBlank()) {
            arguments.add(quoted ? current.toString() : current.toString().trim());
        }
        return arguments;
    }

    /**
     * {@code +1} for an opening delimiter, {@code -1} for a closing one, {@code 0} otherwise.
     */
    private static int depthChange(char character, char opening, char closing) {
        if (character == opening) {
            return 1;
        }
        return character == closing ? -1 : 0;
    }
}
