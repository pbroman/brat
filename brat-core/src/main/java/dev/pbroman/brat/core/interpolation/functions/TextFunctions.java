package dev.pbroman.brat.core.interpolation.functions;

import java.util.List;
import java.util.Locale;

import dev.pbroman.brat.core.api.interpolation.BratFunction;
import dev.pbroman.brat.core.exception.BratException;

import static dev.pbroman.brat.core.api.interpolation.FunctionArgs.integerWithin;
import static dev.pbroman.brat.core.api.interpolation.FunctionArgs.requireArgCount;

/**
 * Functions that reshape text.
 * <p>
 * Case conversion is <strong>locale-independent</strong>: the machine's default locale never changes
 * the result, so a Turkish-locale machine still maps {@code i} to {@code I}.
 *
 * <dl>
 *   <dt>{@code ${__upper(text)}}, {@code ${__lower(text)}}</dt>
 *   <dd>Case conversion. One argument each.</dd>
 *   <dt>{@code ${__trim(text)}}</dt>
 *   <dd>Whitespace removed from both ends.</dd>
 *   <dt>{@code ${__length(text)}}</dt>
 *   <dd>The number of characters, as a number.</dd>
 *   <dt>{@code ${__substring(text, start)}}, {@code ${__substring(text, start, end)}}</dt>
 *   <dd>The characters from {@code start} (inclusive) to {@code end} (exclusive), or to the end of
 *       the text if {@code end} is omitted. Both are zero-based; an index outside the text is an
 *       error rather than a silent clamp, since a quietly truncated id is worse than a failed run.</dd>
 *   <dt>{@code ${__replace(text, search, replacement)}}</dt>
 *   <dd>Every occurrence of {@code search} replaced. <strong>Literal, not a regular expression</strong>,
 *       so {@code ${__replace(${vars.path}, /, _)}} needs no escaping.</dd>
 *   <dt>{@code ${__default(value, fallback)}}</dt>
 *   <dd>{@code value}, or {@code fallback} if {@code value} is empty. Distinct from the
 *       {@code ${ns.key:-fallback}} chain, which answers a <em>missing key</em> before a value
 *       exists; this answers an <em>empty value</em>, which is what a missing {@code vars} entry
 *       resolves to. Whitespace counts as a value.</dd>
 * </dl>
 */
final class TextFunctions {

    // The names these functions are registered under, which their messages repeat.
    private static final String UPPER = "upper";
    private static final String LOWER = "lower";
    private static final String TRIM = "trim";
    private static final String LENGTH = "length";
    private static final String SUBSTRING = "substring";
    private static final String REPLACE = "replace";
    private static final String DEFAULT = "default";

    private TextFunctions() {
        // no instances
    }

    /**
     * The text functions.
     *
     * @return the functions
     */
    public static List<BratFunction> functions() {
        return List.of(
                BratFunction.of(UPPER, TextFunctions::upper),
                BratFunction.of(LOWER, TextFunctions::lower),
                BratFunction.of(TRIM, TextFunctions::trim),
                BratFunction.of(LENGTH, TextFunctions::length),
                BratFunction.of(SUBSTRING, TextFunctions::substring),
                BratFunction.of(REPLACE, TextFunctions::replace),
                BratFunction.of(DEFAULT, TextFunctions::defaultTo));
    }

    private static String upper(List<String> args) {
        requireArgCount(UPPER, args, 1, 1);
        // ROOT rather than the default locale: a Turkish-locale machine would otherwise map i to İ
        return args.getFirst().toUpperCase(Locale.ROOT);
    }

    private static String lower(List<String> args) {
        requireArgCount(LOWER, args, 1, 1);
        return args.getFirst().toLowerCase(Locale.ROOT);
    }

    private static String trim(List<String> args) {
        requireArgCount(TRIM, args, 1, 1);
        return args.getFirst().trim();
    }

    private static String length(List<String> args) {
        requireArgCount(LENGTH, args, 1, 1);
        return String.valueOf(args.getFirst().length());
    }

    private static String substring(List<String> args) {
        requireArgCount(SUBSTRING, args, 2, 3);
        var text = args.getFirst();
        var start = integerWithin(SUBSTRING, args.get(1), 0, text.length());
        var end = args.size() == 3 ? integerWithin(SUBSTRING, args.get(2), 0, text.length()) : text.length();
        if (start > end) {
            throw new BratException(
                    "__substring cannot take " + start + " to " + end + " of a " + text.length() + "-character value");
        }
        return text.substring(start, end);
    }

    private static String replace(List<String> args) {
        requireArgCount(REPLACE, args, 3, 3);
        return args.getFirst().replace(args.get(1), args.get(2));
    }

    private static String defaultTo(List<String> args) {
        requireArgCount(DEFAULT, args, 2, 2);
        return args.getFirst().isEmpty() ? args.get(1) : args.getFirst();
    }
}
