package dev.pbroman.brat.core.interpolation;

import java.util.List;

import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenScannerTest {

    private static List<String> textsIn(String input) {
        return TokenScanner.tokensIn(input).stream()
                .map(TokenScanner.Token::text)
                .toList();
    }

    @Test
    void tokensIn_findsASingleToken() {
        // when / then
        assertThat(textsIn("${vars.name}")).containsExactly("${vars.name}");
    }

    @Test
    void tokensIn_findsATokenInSurroundingText() {
        // when / then
        assertThat(textsIn("hello ${vars.name}, welcome")).containsExactly("${vars.name}");
    }

    @Test
    void tokensIn_findsSeveralTokensInOrder() {
        // when / then
        assertThat(textsIn("${a}${b}")).containsExactly("${a}", "${b}");
        assertThat(textsIn("${a} and ${b}")).containsExactly("${a}", "${b}");
    }

    @Test
    void tokensIn_returnsANestedTokenWholeRatherThanTruncated() {
        // when / then — the case a lazy regex cuts at the inner closing brace
        assertThat(textsIn("${__upper(${vars.name})}")).containsExactly("${__upper(${vars.name})}");
    }

    @Test
    void tokensIn_spansLiteralBracesInsideAToken() {
        // when / then — a JSON object as a function argument must not end the token early
        assertThat(textsIn("${__json({\"a\": 1})}")).containsExactly("${__json({\"a\": 1})}");
    }

    @Test
    void tokensIn_handlesNestingSeveralDeep() {
        // when / then
        assertThat(textsIn("${__upper(${__trim(${vars.name})})}"))
                .containsExactly("${__upper(${__trim(${vars.name})})}");
    }

    @Test
    void tokensIn_doesNotReturnNestedTokensSeparately() {
        // when
        var tokens = TokenScanner.tokensIn("${__upper(${vars.name})} and ${vars.other}");

        // then — the outer token is one unit; its contents are the resolving rule's to interpret
        assertThat(textsIn("${__upper(${vars.name})} and ${vars.other}"))
                .containsExactly("${__upper(${vars.name})}", "${vars.other}");
        assertThat(tokens).hasSize(2);
    }

    @Test
    void tokensIn_findsNothingInPlainText() {
        // when / then
        assertThat(textsIn("no tokens here")).isEmpty();
        assertThat(textsIn("")).isEmpty();
    }

    @Test
    void tokensIn_ignoresBracesThatStartNoToken() {
        // when / then — a JSON body is not a token, and a stray closing brace is literal text
        assertThat(textsIn("{\"a\": 1}")).isEmpty();
        assertThat(textsIn("closing } brace")).isEmpty();
    }

    @Test
    void tokensIn_findsATokenInsideAJsonBody() {
        // when / then
        assertThat(textsIn("{\"name\": \"${vars.name}\"}")).containsExactly("${vars.name}");
    }

    @Test
    void tokensIn_leavesAnUnbalancedTokenAsLiteralText() {
        // when / then — likelier a body BRAT should pass through than a mistyped token
        assertThat(textsIn("${vars.name")).isEmpty();
        assertThat(textsIn("${__upper(${vars.name}")).isEmpty();
    }

    @Test
    void tokensIn_pairsAStrayOpenerWithTheNextClosingBrace() {
        // when / then — inherent to counting braces: the scanner cannot know the author meant the
        // final brace to close the JSON object rather than the token. In practice no rule resolves
        // the nonsense token that results, so the field passes through unchanged.
        assertThat(textsIn("{\"a\": \"${unclosed\"}")).containsExactly("${unclosed\"}");
    }

    @Test
    void tokensIn_findsTokensBeforeAnUnbalancedOne() {
        // when / then
        assertThat(textsIn("${a} then ${broken")).containsExactly("${a}");
    }

    @Test
    void tokensIn_reportsPositionsThatSliceTheInput() {
        // given
        var input = "hello ${vars.name}!";

        // when
        var token = TokenScanner.tokensIn(input).getFirst();

        // then
        assertThat(input.substring(token.start(), token.end())).isEqualTo("${vars.name}");
        assertThat(token.text()).isEqualTo("${vars.name}");
    }

    @Test
    void tokensIn_treatsAnEmptyTokenAsAToken() {
        // when / then — malformed, but it is the resolving rules' business to reject it
        assertThat(textsIn("${}")).containsExactly("${}");
    }

    @Test
    void tokensIn_throwsForNullInput() {
        // when / then
        assertThatThrownBy(() -> TokenScanner.tokensIn(null)).isInstanceOf(BratException.class);
    }

    @Test
    void spans_isTrueForATokenFillingTheWholeField() {
        // given
        var token = TokenScanner.tokensIn("${vars.name}").getFirst();

        // when / then
        assertThat(token.spans("${vars.name}")).isTrue();
    }

    @Test
    void spans_isTrueForANestedTokenFillingTheWholeField() {
        // given — one token by brace counting, and it spans the field
        var token = TokenScanner.tokensIn("${__upper(${vars.name})}").getFirst();

        // when / then
        assertThat(token.spans("${__upper(${vars.name})}")).isTrue();
    }

    @Test
    void spans_isFalseForATokenWithTextAroundIt() {
        // given
        var leading = "hello ${vars.name}";
        var trailing = "${vars.name}, welcome";

        // when / then
        assertThat(TokenScanner.tokensIn(leading).getFirst().spans(leading)).isFalse();
        assertThat(TokenScanner.tokensIn(trailing).getFirst().spans(trailing)).isFalse();
    }

    @Test
    void spans_isFalseForEitherOfTwoAdjacentTokens() {
        // given
        var input = "${a}${b}";

        // when
        var tokens = TokenScanner.tokensIn(input);

        // then — the first starts at 0 but ends early, the second ends at the end but starts late
        assertThat(tokens.getFirst().spans(input)).isFalse();
        assertThat(tokens.getLast().spans(input)).isFalse();
    }

    @Test
    void spans_throwsForANullInput() {
        // given
        var token = TokenScanner.tokensIn("${vars.name}").getFirst();

        // when / then
        assertThatThrownBy(() -> token.spans(null)).isInstanceOf(BratException.class);
    }

    @Test
    void holdsNestedToken_answersTrueForATokenWhoseKeyHoldsAnotherToken() {
        // when / then — the shape nothing resolves: no rule owns it, so it passes through
        assertThat(TokenScanner.holdsNestedToken("${vars.${vars.inner}}")).isTrue();
    }

    @Test
    void holdsNestedToken_answersFalseForAPlainToken() {
        // when / then
        assertThat(TokenScanner.holdsNestedToken("${vars.userId}")).isFalse();
    }

    @Test
    void holdsNestedToken_answersFalseForAJsonPathDollar() {
        // when / then — a token opener is "${", not a bare "$": these paths must keep resolving
        assertThat(TokenScanner.holdsNestedToken("${response.json.$.id}")).isFalse();
        assertThat(TokenScanner.holdsNestedToken("${response.json.$..book[?(@.price<10)]}"))
                .isFalse();
    }

    @Test
    void holdsNestedToken_answersTrueForAFunctionCallWithATokenArgument() {
        // when / then — correct, and harmless: a call is routed to the evaluator and never reaches
        // a rule, so nothing asks this about one
        assertThat(TokenScanner.holdsNestedToken("${__upper(${vars.name})}")).isTrue();
    }

    @Test
    void holdsNestedToken_answersFalseForAFunctionCallWithNoTokenArgument() {
        // when / then
        assertThat(TokenScanner.holdsNestedToken("${__upper(abc)}")).isFalse();
    }

    @Test
    void holdsNestedToken_answersFalseForAnEmptyToken() {
        // when / then — the opener at index 0 is the token's own, not a nested one
        assertThat(TokenScanner.holdsNestedToken("${}")).isFalse();
    }

    @Test
    void holdsNestedToken_answersFalseForTextThatIsNotAToken() {
        // when / then
        assertThat(TokenScanner.holdsNestedToken("vars.userId")).isFalse();
        assertThat(TokenScanner.holdsNestedToken("")).isFalse();
        assertThat(TokenScanner.holdsNestedToken("   ")).isFalse();
    }

    @Test
    void holdsNestedToken_answersFalseForNullRatherThanThrowing() {
        // when / then — the same policy as isToken, so the pair can be asked in either order
        assertThat(TokenScanner.holdsNestedToken(null)).isFalse();
    }

    @Test
    void holdsNestedToken_answersPositionallyForTextMerelyContainingAToken() {
        // when / then — documented: the opener is looked for from the second character on. Every
        // caller asks this only of a string isToken has accepted, where the two coincide
        assertThat(TokenScanner.holdsNestedToken("id: ${vars.id}")).isTrue();
    }

    @Test
    void isToken_acceptsASingleToken() {
        // when / then
        assertThat(TokenScanner.isToken("${vars.userId}")).isTrue();
    }

    @Test
    void isToken_acceptsANestedToken() {
        // when / then — counted rather than matched, which a lazy token regex cannot do
        assertThat(TokenScanner.isToken("${__upper(${vars.name})}")).isTrue();
    }

    @Test
    void isToken_rejectsATokenWithTextAroundIt() {
        // when / then — holding a token is not being one
        assertThat(TokenScanner.isToken("id: ${vars.id}")).isFalse();
        assertThat(TokenScanner.isToken("${vars.id} trailing")).isFalse();
    }

    @Test
    void isToken_rejectsTwoAdjacentTokens() {
        // when / then
        assertThat(TokenScanner.isToken("${vars.a}${vars.b}")).isFalse();
    }

    @Test
    void isToken_rejectsAnUnterminatedToken() {
        // when / then
        assertThat(TokenScanner.isToken("${vars.unclosed")).isFalse();
    }

    @Test
    void isToken_rejectsTextThatIsNotAToken() {
        // when / then
        assertThat(TokenScanner.isToken("vars.userId")).isFalse();
        assertThat(TokenScanner.isToken("")).isFalse();
        assertThat(TokenScanner.isToken("   ")).isFalse();
    }

    @Test
    void isToken_answersFalseForNullRatherThanThrowing() {
        // when / then — unlike TokenScanner.tokensIn, which rejects a null
        assertThat(TokenScanner.isToken(null)).isFalse();
    }

    @Test
    void isToken_acceptsAnEmptyTokenAsSyntacticallyOne() {
        // when / then — well-formed but nameless; resolvability is not this method's question
        assertThat(TokenScanner.isToken("${}")).isTrue();
    }
}
