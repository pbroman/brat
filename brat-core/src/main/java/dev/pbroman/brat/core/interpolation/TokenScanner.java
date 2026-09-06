package dev.pbroman.brat.core.interpolation;

import java.util.ArrayList;
import java.util.List;

import dev.pbroman.brat.core.exception.BratException;
import org.apache.commons.lang3.StringUtils;

import static dev.pbroman.brat.core.interpolation.InterpolationPatterns.TOKEN_PREFIX;
import static dev.pbroman.brat.core.interpolation.InterpolationPatterns.TOKEN_SUFFIX;
import static dev.pbroman.brat.core.util.Require.nonNull;

/**
 * Finds the interpolation tokens in a field and answers what counts as one, counting braces so that
 * a token holding another token is returned whole.
 * <p>
 * Three questions, one mechanism: where a field's tokens are ({@link #tokensIn}), whether a string
 * is exactly one of them ({@link #isToken}), and whether a token holds another
 * ({@link #holdsNestedToken}). The last is the one a rule asks before claiming.
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
    public record Token(int start, int end, String text) {

        /**
         * Whether this token spans the whole of {@code input} — it begins at the first character
         * and ends at the last.
         * <p>
         * The boundary half of "is this string exactly one token". A caller holding the token list
         * already asks this instead of re-scanning the field, which is why the arithmetic lives
         * here rather than only inside {@link TokenScanner#isToken(String)}: two callers
         * spelling the same index comparison is the part that can drift.
         * <p>
         * Position only. {@code input} is assumed to be the field this token was found in; the text
         * is not re-examined, so asking about an unrelated string of the same length answers
         * {@code true}.
         *
         * @param input the field this token was found in
         * @return whether {@link #start()} is {@code 0} and {@link #end()} is {@code input}'s length
         * @throws BratException if {@code input} is {@code null}
         */
        public boolean spans(String input) {
            nonNull(input, "The input must not be null");
            return start == 0 && end == input.length();
        }
    }

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
     * Whether {@code input} is exactly one interpolation token — the delimiters at both ends, and
     * nothing outside them.
     * <p>
     * <strong>Counted, not matched.</strong> A regex for a token is lazy and stops at the first
     * closing brace, so it would answer {@code false} for {@code ${__upper(${vars.name})}}; this
     * counts braces through {@link TokenScanner} and answers {@code true}. A token holding another
     * token is one token.
     * <p>
     * <strong>"Is a token", not "holds a token."</strong> Text with a token somewhere inside it —
     * {@code "id: ${vars.id}"} — is {@code false}, and so are two adjacent tokens: the question is
     * whether the whole string is a single token, which is what decides whether a resolved value may
     * keep its own type instead of being spliced into surrounding text.
     * <p>
     * Syntax only. {@code ${}} is a well-formed token carrying no name, so this answers {@code true}
     * for it; whether any rule can resolve such a token is not this method's question.
     *
     * @param input the string to test, or {@code null}
     * @return whether {@code input} is exactly one token. {@code false} for {@code null}, for empty
     *         or blank text, for a token with anything around it, for two adjacent tokens, and for a
     *         token opener with no closing brace. Never throws — a {@code null} is answered rather
     *         than rejected, unlike {@link #tokensIn(String)}
     */
    public static boolean isToken(String input) {
        if (input == null || input.isBlank()) {
            return false;
        }
        var tokens = tokensIn(input);
        return tokens.size() == 1 && tokens.getFirst().spans(input);
    }

    /**
     * Whether {@code input} holds a token of its own inside it, e.g. {@code ${vars.${vars.inner}}}.
     * <p>
     * The ownership question a rule asks after {@link #isToken(String)} has answered the syntax one.
     * The two disagree deliberately: a token holding another token <em>is</em> one token by brace
     * counting, and is nonetheless not a {@code vars} token, because nothing resolves its key. A rule
     * declines such a token rather than claiming it, so the field passes through as written instead of
     * being claimed by whichever rule's pattern matched first.
     * <p>
     * <strong>Nesting is supported inside a function call and nowhere else.</strong> A call's
     * arguments are handed back through interpolation by the function evaluator; a namespace key has
     * no such recursion and no sub-expression position to descend into. This method therefore answers
     * {@code true} for {@code ${__upper(${vars.name})}} as well — correctly, and harmlessly, since a
     * call is routed to the evaluator and never reaches a rule.
     * <p>
     * <strong>A token opener, not a bare {@code $}.</strong> {@code ${response.json.$.id}} holds a
     * JSONPath, not a nested token, and must keep resolving.
     * <p>
     * Positional: the opener is looked for from the second character onwards, so text merely
     * <em>containing</em> a token ({@code "id: ${vars.id}"}) also answers {@code true}. Every caller
     * asks this only of a string {@link #isToken(String)} has already accepted, where "holds an opener
     * beyond its start" and "holds a nested token" are the same question.
     *
     * @param input the string to test, or {@code null}
     * @return whether a token opener occurs anywhere after {@code input}'s first character.
     *         {@code false} for {@code null}, for empty or blank text, for a token with no token
     *         inside it, and for the empty token {@code ${}}. Never throws
     */
    public static boolean holdsNestedToken(String input) {
        return !StringUtils.isBlank(input) && input.indexOf(TOKEN_PREFIX, 1) >= 0;
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
