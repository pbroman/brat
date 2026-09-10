package dev.pbroman.brat.core.interpolation.configdata;

import java.util.HashMap;
import java.util.Map;

import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.data.ChainedCondition;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChainedConditionInterpolatorTest {

    private final ChainedConditionInterpolator interpolator = new ChainedConditionInterpolator();

    private Interpolation interpolation;
    private RuntimeData runtimeData;

    @BeforeEach
    void setUp() {
        interpolation = mock(Interpolation.class);
        runtimeData = mock(RuntimeData.class);
        when(interpolation.outcome(anyString(), any()))
                .thenAnswer(i -> new InterpolationOutcome("i:" + i.getArgument(0), "r:" + i.getArgument(0)));
    }

    @Test
    void interpolated_interpolatesBArgsAndMessage() {
        // given
        var link = new ChainedCondition("contains", "${vars.x}", "missing ${vars.x}", Map.of("offset", "${vars.o}"));

        // when
        var result = interpolator.interpolated(link, interpolation, runtimeData);

        // then
        assertThat(result.getB()).isEqualTo("i:${vars.x}");
        assertThat(result.getMessage()).isEqualTo("i:missing ${vars.x}");
        assertThat(result.getArgs()).containsExactly(Map.entry("offset", "i:${vars.o}"));
    }

    @Test
    void interpolated_copiesFuncThrough() {
        // given
        var link = new ChainedCondition("startsWith", "Jo");

        // when
        var result = interpolator.interpolated(link, interpolation, runtimeData);

        // then
        assertThat(result.getFunc()).isEqualTo("startsWith");
    }

    @Test
    void interpolated_keysOutcomesByFieldAndArgName() {
        // given
        var link = new ChainedCondition("contains", "b-value", "a message", Map.of("offset", "0.01"));

        // when
        var result = interpolator.interpolated(link, interpolation, runtimeData);

        // then
        assertThat(result.getOutcomes()).containsOnlyKeys("b", "args.offset", "message");
    }

    @Test
    void interpolated_marksTheCopyInterpolated() {
        // given
        var link = new ChainedCondition("contains", "b-value");

        // when
        var result = interpolator.interpolated(link, interpolation, runtimeData);

        // then
        assertThat(result.isInterpolated()).isTrue();
        assertThat(link.isInterpolated()).isFalse();
    }

    @Test
    void interpolated_recordsNoOutcomeForAbsentOptionalFields() {
        // given — no b, no message, no args
        var link = new ChainedCondition("isNotNull", null);

        // when
        var result = interpolator.interpolated(link, interpolation, runtimeData);

        // then
        assertThat(result.getB()).isNull();
        assertThat(result.getMessage()).isNull();
        assertThat(result.getOutcomes()).isEmpty();
    }

    @Test
    void interpolated_throwsExceptionIfCopy() {
        // given
        var link = new ChainedCondition("contains", "b-value");
        var interpolated = interpolator.interpolated(link, interpolation, runtimeData);

        // then
        assertThatThrownBy(() -> interpolator.interpolated(interpolated, interpolation, runtimeData))
                .isInstanceOf(BratException.class);
    }

    @Test
    void interpolated_throwsExceptionOnAnArgWithoutAValue() {
        // given
        var args = new HashMap<String, String>();
        args.put("offset", null);
        var link = new ChainedCondition("isCloseTo", "5", null, args);

        // then
        assertThatThrownBy(() -> interpolator.interpolated(link, interpolation, runtimeData))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("offset");
    }
}
