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
        var token = InterpolationPatterns.wrapAsVariable("sc");

        // then — the delimiters must agree across every member of this class
        assertThat(token).isEqualTo("${sc}");
        assertThat(token).matches(InterpolationPatterns.regexForVariable("sc"));
        assertThat(InterpolationPatterns.VARIABLE_PATTERN.matcher(token).matches())
                .isTrue();
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

    @Test
    void variablePattern_stopsAtTheFirstClosingBrace() {
        // when
        var matcher = InterpolationPatterns.VARIABLE_PATTERN.matcher("${__upper(${vars.name})}");

        // then — documented as not handling nesting; that is TokenScanner's job
        assertThat(matcher.find()).isTrue();
        assertThat(matcher.group()).isEqualTo("${__upper(${vars.name}");
    }
}
