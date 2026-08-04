package dev.pbroman.brat.core.interpolation.configdata;

import java.util.HashMap;
import java.util.Map;

import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.data.Condition;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConditionInterpolatorTest {

    private final ConditionInterpolator underTest = new ConditionInterpolator();
    private final Interpolation interpolation = Mockito.mock(Interpolation.class);
    private final RuntimeData runtimeData = Mockito.mock(RuntimeData.class);

    @BeforeEach
    void setUp() {
        Mockito.when(interpolation.outcome(Mockito.any(), Mockito.any()))
                .thenAnswer(call -> new InterpolationOutcome(call.getArgument(0), call.getArgument(0)));
    }

    @Test
    void interpolated_carriesTheArgsOntoTheCopy() {
        // given
        var condition = new Condition("isCloseTo", "0.51", "0.5");
        condition.setArgs(Map.of("offset", "0.01"));

        // when
        var result = underTest.interpolated(condition, interpolation, runtimeData);

        // then
        assertThat(result.getArgs()).containsExactly(Map.entry("offset", "0.01"));
    }

    @Test
    void interpolated_keysArgOutcomesByDottedPath() {
        // given
        var condition = new Condition("isCloseTo", "0.51", "0.5");
        condition.setArgs(Map.of("offset", "0.01"));

        // when
        var result = underTest.interpolated(condition, interpolation, runtimeData);

        // then — `args.offset`, never `params.offset`, which would collide with the ${params.x} namespace
        assertThat(result.getOutcomes()).containsKey("args.offset");
    }

    @Test
    void interpolated_defaultsAbsentArgsToAnEmptyMap() {
        // when
        var result = underTest.interpolated(new Condition("isNull", "a"), interpolation, runtimeData);

        // then
        assertThat(result.getArgs()).isEmpty();
    }

    @Test
    void interpolated_throwsForAnArgumentDeclaredWithoutAValue() {
        // given — `args: { offset: }` in YAML binds a null value
        var args = new HashMap<String, String>();
        args.put("offset", null);
        var condition = new Condition("isCloseTo", "0.51", "0.5");
        condition.setArgs(args);

        // when / then
        assertThatThrownBy(() -> underTest.interpolated(condition, interpolation, runtimeData))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("offset");
    }

    @Test
    void setArgs_treatsNullAsNoArguments() {
        // given
        var condition = new Condition("isNull", "a");

        // when
        condition.setArgs(null);

        // then
        assertThat(condition.getArgs()).isEmpty();
    }
}
