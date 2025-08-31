package dev.pbroman.brat.core.interpolation.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.ValidationException;
import dev.pbroman.brat.core.interpolation.AbstractInterpolationTest;

class EnvInterpolationRuleTest extends AbstractInterpolationTest {

    @BeforeEach
    void setUp() {
        underTest = new EnvInterpolationRule(tools);
    }

    protected RuntimeData setUpRuntimeData() {
        return new RuntimeData(Map.of(), Map.of("moo", "baa"));
    }

    @Test
    void happyPath() throws Exception {
        // given
        var input = "${env.moo}";
        var expected = "baa";

        // when
        var result = underTest.interpolate(input, runtimeData);

        //then
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void onMissingReplacement_throwsValidationException() throws Exception {
        // given
        var input = "${env.missing}";

        // then
        assertThatThrownBy(() -> underTest.interpolate(input, runtimeData))
                .isInstanceOf(ValidationException.class);
    }

}