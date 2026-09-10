package dev.pbroman.brat.core.interpolation.configdata;

import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.data.Condition;
import dev.pbroman.brat.core.data.FlowControl;
import dev.pbroman.brat.core.data.RepeatUntil;
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

class FlowControlInterpolatorTest {

    private final RepeatUntilInterpolator repeatUntilInterpolator = new RepeatUntilInterpolator();
    private final FlowControlInterpolator interpolator = new FlowControlInterpolator(repeatUntilInterpolator);

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
    void interpolated_interpolatesWaitAfter() {
        // given
        var flowControl = new FlowControl("${vars.pause}", null);

        // when
        var result = interpolator.interpolated(flowControl, interpolation, runtimeData);

        // then
        assertThat(result.getWaitAfter()).isEqualTo("i:${vars.pause}");
        assertThat(result.getOutcomes()).containsOnlyKeys("waitAfter");
    }

    @Test
    void interpolated_interpolatesTheNestedRepeatUntil() {
        // given
        var repeatUntil = new RepeatUntil(new Condition("isEqualTo", "a", "b"), "${vars.max}", "2000", null);
        var flowControl = new FlowControl("500", repeatUntil);

        // when
        var result = interpolator.interpolated(flowControl, interpolation, runtimeData);

        // then
        assertThat(result.getRepeatUntil().isInterpolated()).isTrue();
        assertThat(result.getRepeatUntil().getMaxAttempts()).isEqualTo("i:${vars.max}");
    }

    @Test
    void interpolated_keepsTheNestedBlocksOutcomesOnTheNestedBlock() {
        // given
        var repeatUntil = new RepeatUntil(new Condition("isEqualTo", "a", "b"), "10", "2000", "nope");
        var flowControl = new FlowControl("500", repeatUntil);

        // when
        var result = interpolator.interpolated(flowControl, interpolation, runtimeData);

        // then — this block's key set does not grow with what the nested one declares
        assertThat(result.getOutcomes()).containsOnlyKeys("waitAfter");
        assertThat(result.getRepeatUntil().getOutcomes())
                .containsOnlyKeys("maxAttempts", "waitBetweenAttempts", "messageOnFail");
    }

    @Test
    void interpolated_recordsNoOutcomeForAbsentFields() {
        // given
        var flowControl = new FlowControl(null, null);

        // when
        var result = interpolator.interpolated(flowControl, interpolation, runtimeData);

        // then
        assertThat(result.getWaitAfter()).isNull();
        assertThat(result.getRepeatUntil()).isNull();
        assertThat(result.getOutcomes()).isEmpty();
    }

    @Test
    void interpolated_marksTheCopyInterpolated() {
        // given
        var flowControl = new FlowControl("500", null);

        // when
        var result = interpolator.interpolated(flowControl, interpolation, runtimeData);

        // then
        assertThat(result.isInterpolated()).isTrue();
        assertThat(flowControl.isInterpolated()).isFalse();
    }

    @Test
    void interpolated_throwsExceptionIfCopy() {
        // given
        var flowControl = new FlowControl("500", null);
        var interpolated = interpolator.interpolated(flowControl, interpolation, runtimeData);

        // when / then
        assertThatThrownBy(() -> interpolator.interpolated(interpolated, interpolation, runtimeData))
                .isInstanceOf(BratException.class);
    }
}
