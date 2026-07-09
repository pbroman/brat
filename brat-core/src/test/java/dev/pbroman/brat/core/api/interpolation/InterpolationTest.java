package dev.pbroman.brat.core.api.interpolation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InterpolationTest {

    @Test
    void interpolate_returnsOutcomeValue() {
        // given
        Interpolation underTest = (input, runtimeData) -> new InterpolationOutcome(input + "-resolved", "reportingString");

        // when
        var result = underTest.interpolate("token", null);

        // then
        assertThat(result).isEqualTo("token-resolved");
    }

}
