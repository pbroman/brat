package dev.pbroman.brat.core.data;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConditionTest extends AbstractConfigDataTest {

    Condition validCondition = new Condition("func", "a", "b");
    ConditionInterpolation conditionInterpolation = new ConditionInterpolation();

    @BeforeEach
    @Test
    void interpolated_isCorrect() {
        // when
        var interpolated = conditionInterpolation.interpolated(validCondition, interpolation, runtimeData);

        // then
        assertThat(interpolated.getFunc()).isEqualTo("func");
        assertThat(interpolated.getA()).isEqualTo("interpolated");
        assertThat(interpolated.getB()).isEqualTo("interpolated");
        assertThat(interpolated.getOutcomes()).isNotNull();
    }

    @Test
    void interpolated_throwsExceptionIfCopy() {
        assertInterpolatedThrowsExceptionIfCopy(validCondition, conditionInterpolation);
    }

    @Test
    void interpolated_skipsNullB() {
        // given
        var unaryCondition = new Condition("isNull", "a");

        // when
        var interpolated = conditionInterpolation.interpolated(unaryCondition, interpolation, runtimeData);

        // then
        assertThat(interpolated.getB()).isNull();
        assertThat(interpolated.getOutcomes()).doesNotContainKey("b");
    }

}
