package dev.pbroman.brat.core.interpolation;

import java.util.ArrayList;
import java.util.List;

import dev.pbroman.brat.core.exception.BratException;

import static dev.pbroman.brat.core.interpolation.InterpolationPatterns.TOKEN_PREFIX;
import static dev.pbroman.brat.core.interpolation.InterpolationPatterns.TOKEN_SUFFIX;
import static dev.pbroman.brat.core.util.Require.nonNull;

/**
 * Finds the interpolation tokens in a field, counting braces so that a token holding another token
 * is returned whole.
 * <p>
 * A regex cannot do this: the token pattern is lazy and stops at the first closing brace, so
 * {@code ${__upper(${vars.name})}} would be cut short. Counting is what
 * makes a function's arguments able to contain tokens of their own.
 * <p>
 * <strong>Scanning is over the field as authored, never over resolved output.</strong> Nothing here
 * re-examines a value a rule produced, so a response body or a secret that happens to contain the
 * text {@code ${secrets.apiKey}} is never treated as a token. Resolving a nested token is the
 * resolving rule's job, by passing the argument substring back through interpolation — recursion
 * over the template, not over data.
 */
public final class TokenScanner {

    private static final char OPENING_BRACE = TOKEN_PREFIX.charAt(TOKEN_PREFIX.length() - 1);
    private static final char CLOSING_BRACE = TOKEN_SUFFIX.charAt(0);

    private TokenScanner() {
        // no instances
    }

    /**
     * One token's position in the field it was found in.
     *
     * @param start index of the token's opening {@code $}
     * @param end index just past the token's closing brace
     * @param text the token itself, delimiters included
     */
    public record Token(int start, int end, String text) {}

    /**
     * Finds every top-level token in {@code input}, in order.
     * <p>
     * A token starts at a {@code $} followed by an opening brace and ends at the brace that closes
     * it, counting every opening brace as one level deeper and every closing brace as one level
     * back — so both a nested token and a literal brace inside the token (a JSON object in a
     * function argument, say) are spanned rather than ending it early.
     * <p>
     * Tokens nested inside another are not returned separately; the outer token is returned whole
     * and its contents are the resolving rule's to interpret.
     * <p>
     * Text outside a token is ignored, including a stray closing brace. An unbalanced token start
     * — one with no closing brace before the end of the field — is <strong>not</strong> a token and
     * is left as literal text, because such a field is far more likely to be a body BRAT should
     * pass through untouched than a mistyped token.
     * <p>
     * A consequence of counting rather than parsing: a stray token opener pairs with the
     * <em>next</em> closing brace, whatever the author meant it for. In
     * {@code "a": "${unclosed"} } the JSON object's brace closes the token. Nothing can tell the two
     * apart from the text alone; in practice no rule resolves the nonsense token that results, so
     * the field passes through unchanged.
     *
     * @param input the field to scan
     * @return the tokens found, in the order they appear; empty if there are none
     * @throws BratException if {@code input} is {@code null}
     */
    public static List<Token> tokensIn(String input) {
        nonNull(input, "Cannot scan a null input");
        var tokens = new ArrayList<Token>();
        var index = 0;
        while (index < input.length() - 1) {
            if (!input.startsWith(TOKEN_PREFIX, index)) {
                index++;
                continue;
            }
            var end = endOfToken(input, index);
            if (end < 0) {
                // Unbalanced: no closing brace in the rest of the field, so this is not a token.
                return tokens;
            }
            tokens.add(new Token(index, end, input.substring(index, end)));
            index = end;
        }
        return tokens;
    }

    /**
     * The index just past the brace closing the token starting at {@code start}, or {@code -1} if
     * no brace closes it.
     */
    private static int endOfToken(String input, int start) {
        var depth = 0;
        for (var index = start + 1; index < input.length(); index++) {
            // if/else rather than a switch: the delimiters are derived from
            // InterpolationPatterns, and a switch label must be a compile-time constant.
            var character = input.charAt(index);
            if (character == OPENING_BRACE) {
                depth++;
            } else if (character == CLOSING_BRACE) {
                depth--;
                if (depth == 0) {
                    return index + 1;
                }
            }
        }
        return -1;
    }
}
