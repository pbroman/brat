package dev.pbroman.brat.core.interpolation.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.interpolation.AbstractInterpolationTest;

class ParamsInterpolationRuleTest extends AbstractInterpolationTest {

    @BeforeEach
    void setUp() {
        underTest = new ParamsInterpolationRule(tools);
    }

    protected RuntimeData setUpRuntimeData() {
        return new RuntimeData(Map.of(), Map.of("threadCount", "3"), Map.of(), Map.of(), Map.of("moo", "baa"));
    }

    @Test
    void happyPath() {
        // given
        var input = "${params.moo}";
        var expected = "baa";

        // when
        var result = underTest.interpolate(input, runtimeData);

        //then
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void onMissingReplacement_throwsBratExceptionWhenNoFallbackDeclared() {
        // given
        var input = "${params.missing}";

        // then
        assertThatThrownBy(() -> underTest.interpolate(input, runtimeData))
                .isInstanceOf(BratException.class);
    }

    @Test
    void onMissingReplacement_fallsBackToLiteralDefault() {
        // given
        var input = "${params.missing:-10}";
        var expected = "10";

        // when
        var result = underTest.interpolate(input, runtimeData);

        // then
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void onMissingReplacement_fallsBackToEnvBeforeReachingLiteral() {
        // given
        var input = "${params.threadCount:-env.threadCount:-1}";
        var expected = "3";

        // when
        var result = underTest.interpolate(input, runtimeData);

        // then
        assertThat(result).isEqualTo(expected);
    }

}
