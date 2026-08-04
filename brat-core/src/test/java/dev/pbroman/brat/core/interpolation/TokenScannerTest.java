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
}
