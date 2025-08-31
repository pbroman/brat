package dev.pbroman.brat.core.interpolation.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.pbroman.brat.core.exception.ValidationException;
import dev.pbroman.brat.core.interpolation.AbstractInterpolationTest;

class ConstantsInterpolationRuleTest extends AbstractInterpolationTest {

    @BeforeEach
    void setUp() {
        underTest = new ConstantsInterpolationRule(tools);
    }

    @Test
    void happyPath() throws Exception {
        // given
        var input = "${constants.moo}";
        var expected = "baa";

        // when
        var result = underTest.interpolate(input, runtimeData);

        //then
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void onMissingReplacement_throwsValidationException() throws Exception {
        // given
        var input = "${constants.missing}";

        // then
        assertThatThrownBy(() -> underTest.interpolate(input, runtimeData))
                .isInstanceOf(ValidationException.class);
    }

}