package dev.pbroman.brat.core.interpolation.rules;

import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.interpolation.AbstractInterpolationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConstantsInterpolationRuleTest extends AbstractInterpolationTest {

    @Override
    protected String ownToken() {
        return "${constants.moo}";
    }

    @BeforeEach
    void setUp() {
        underTest = new ConstantsInterpolationRule();
    }

    @Test
    void happyPath() {
        // given
        var input = "${constants.moo}";
        var expected = "baa";

        // when
        var result = interpolate(input, runtimeData);

        // then
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void onMissingReplacement_throwsBratException() {
        // given
        var input = "${constants.missing}";

        // then
        assertThatThrownBy(() -> interpolate(input, runtimeData)).isInstanceOf(BratException.class);
    }
}
