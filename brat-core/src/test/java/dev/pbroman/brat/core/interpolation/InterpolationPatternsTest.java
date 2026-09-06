package dev.pbroman.brat.core.interpolation;

import org.junit.jupiter.api.Test;

import static dev.pbroman.brat.core.util.Constants.VARIABLE_GROUP_NAME;
import static org.assertj.core.api.Assertions.assertThat;

class InterpolationPatternsTest {

    @Test
    void groupingPatternForVariable_isMemoisedPerNamespace() {
        // when
        var first = InterpolationPatterns.groupingPatternForVariable("vars");
        var second = InterpolationPatterns.groupingPatternForVariable("vars");

        // then — the same instance, not an equal one: this is called for every token by every rule
        // the dispatcher tries, so compiling per call is the cost the memoisation removes
        assertThat(first).isSameAs(second);
    }

    @Test
    void groupingPatternForVariable_isSeparatePerNamespace() {
        // when / then
        assertThat(InterpolationPatterns.groupingPatternForVariable("vars"))
                .isNotSameAs(InterpolationPatterns.groupingPatternForVariable("constants"));
    }

    @Test
    void groupingPatternForVariable_capturesTheKeyAfterTheNamespace() {
        // when
        var matcher = InterpolationPatterns.groupingPatternForVariable("vars").matcher("${vars.userId}");

        // then
        assertThat(matcher.find()).isTrue();
        assertThat(matcher.group(VARIABLE_GROUP_NAME)).isEqualTo("userId");
    }

    @Test
    void wrapAsVariable_producesATokenTheOtherPatternsMatch() {
        // when
        var token = InterpolationPatterns.wrapAsVariable("response.statusCode");

        // then — the delimiters must agree across every member of this class
        assertThat(token).isEqualTo("${response.statusCode}");
        assertThat(token).matches(InterpolationPatterns.regexForVariable("response.statusCode"));
        assertThat(TokenScanner.isToken(token)).isTrue();
    }

    @Test
    void regexForVariable_matchesADottedNamespaceLiterally() {
        // when / then — the '.' is a dot, not "any character"
        assertThat("${response.body}").matches(InterpolationPatterns.regexForVariable("response.body"));
        assertThat("${responseXbody}").doesNotMatch(InterpolationPatterns.regexForVariable("response.body"));
    }

    @Test
    void groupingPatternForVariable_matchesADottedNamespaceLiterally() {
        // given
        var pattern = InterpolationPatterns.groupingPatternForVariable("response.json");

        // when / then — only the separator before the key is syntax; the namespace itself is literal
        assertThat(pattern.matcher("${response.json.$.id}").find()).isTrue();
        assertThat(pattern.matcher("${responseXjson.$.id}").find()).isFalse();
    }

    @Test
    void groupingPatternForVariable_doesNotMatchANamespaceItMerelyPrefixes() {
        // when / then
        assertThat(InterpolationPatterns.groupingPatternForVariable("response.headers")
                        .matcher("${response.headersFoo.bar}")
                        .find())
                .isFalse();
    }

    @Test
    void tokenScanner_findsATokenBuiltByWrapAsVariable() {
        // when
        var tokens = TokenScanner.tokensIn(InterpolationPatterns.wrapAsVariable("vars.userId"));

        // then — the scanner counts braces derived from these same constants
        assertThat(tokens)
                .singleElement()
                .satisfies(token -> assertThat(token.text()).isEqualTo("${vars.userId}"));
    }
}
