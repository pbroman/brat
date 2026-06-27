package dev.pbroman.brat.core.data;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConditionTest extends AbstractConfigDataTest {

    Condition validCondition = new Condition("func", "a", "b");

    @BeforeEach
    @Test
    void interpolated_isCorrect() {
        // when
        var interpolated = validCondition.interpolated(interpolation, runtimeData);

        // then
        assertThat(interpolated.getFunc()).isEqualTo("func");
        assertThat(interpolated.getA()).isEqualTo("interpolated");
        assertThat(interpolated.getB()).isEqualTo("interpolated");
        assertThat(interpolated.getNonInterpolated()).isEqualTo(validCondition);
        assertThat(interpolated.getNonInterpolated().getA()).isEqualTo("a");
        assertThat(interpolated.getNonInterpolated().getB()).isEqualTo("b");
    }

    @Test
    void interpolated_throwsExceptionIfCopy() {
        assertInterpolatedThrowsExceptionIfCopy(validCondition);
    }

}