package dev.pbroman.brat.core.interpolation.configdata;

import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.data.Condition;
import dev.pbroman.brat.core.data.RepeatUntil;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RepeatUntilInterpolatorTest {

    private final RepeatUntilInterpolator interpolator = new RepeatUntilInterpolator(new ConditionInterpolator());

    private static final Condition ANY_CONDITION = new Condition("isEqualTo", "a", "b");

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
    void interpolated_interpolatesEveryTextField() {
        // given
        var repeatUntil =
                new RepeatUntil(new Condition("isEqualTo", "a", "b"), "${vars.max}", "${vars.wait}", "gave up");

        // when
        var result = interpolator.interpolated(repeatUntil, interpolation, runtimeData);

        // then
        assertThat(result.getMaxAttempts()).isEqualTo("i:${vars.max}");
        assertThat(result.getWaitBetweenAttempts()).isEqualTo("i:${vars.wait}");
        assertThat(result.getMessageOnFail()).isEqualTo("i:gave up");
    }

    @Test
    void interpolated_doesNotParseNumbers() {
        // given — parsing happens at the point of use, so a bad value is reported against its request
        var repeatUntil = new RepeatUntil(ANY_CONDITION, "not a number", null, null);

        // when
        var result = interpolator.interpolated(repeatUntil, interpolation, runtimeData);

        // then
        assertThat(result.getMaxAttempts()).isEqualTo("i:not a number");
    }

    @Test
    void interpolated_interpolatesTheCondition() {
        // given
        var repeatUntil =
                new RepeatUntil(new Condition("isEqualTo", "${response.json.$.status}", "DONE"), null, null, null);

        // when
        var result = interpolator.interpolated(repeatUntil, interpolation, runtimeData);

        // then
        assertThat(result.getCondition().isInterpolated()).isTrue();
        assertThat(result.getCondition().getA()).isEqualTo("i:${response.json.$.status}");
    }

    @Test
    void interpolated_keepsTheConditionsOutcomesOnTheCondition() {
        // given
        var repeatUntil = new RepeatUntil(new Condition("isEqualTo", "a", "b"), "10", null, null);

        // when
        var result = interpolator.interpolated(repeatUntil, interpolation, runtimeData);

        // then
        assertThat(result.getOutcomes()).containsOnlyKeys("maxAttempts");
        assertThat(result.getCondition().getOutcomes()).containsOnlyKeys("a", "b");
    }

    @Test
    void interpolated_recordsNoOutcomeForAbsentFields() {
        // given - only the condition is required; the three text fields are not
        var repeatUntil = new RepeatUntil(ANY_CONDITION, null, null, null);

        // when
        var result = interpolator.interpolated(repeatUntil, interpolation, runtimeData);

        // then
        assertThat(result.getMaxAttempts()).isNull();
        assertThat(result.getWaitBetweenAttempts()).isNull();
        assertThat(result.getMessageOnFail()).isNull();
        assertThat(result.getOutcomes()).isEmpty();
    }

    @Test
    void interpolated_throwsForANullCondition() {
        // given - "repeat until" with nothing to wait for is not "repeat N times"
        var repeatUntil = new RepeatUntil(null, "10", null, null);

        // when / then - the type, not the wording: the loader is what tells an author where
        assertThatThrownBy(() -> interpolator.interpolated(repeatUntil, interpolation, runtimeData))
                .isInstanceOf(BratException.class);
    }

    @Test
    void interpolated_throwsForANullTarget() {
        // when / then - a null never leaves an interpolator as a NullPointerException
        assertThatThrownBy(() -> interpolator.interpolated(null, interpolation, runtimeData))
                .isInstanceOf(BratException.class);
    }

    @Test
    void interpolated_throwsExceptionIfCopy() {
        // given
        var repeatUntil = new RepeatUntil(ANY_CONDITION, "10", null, null);
        var interpolated = interpolator.interpolated(repeatUntil, interpolation, runtimeData);

        // when / then
        assertThatThrownBy(() -> interpolator.interpolated(interpolated, interpolation, runtimeData))
                .isInstanceOf(BratException.class);
    }

    @Test
    void interpolated_propagatesAFailureFromTheCondition() {
        // given
        var repeatUntil = new RepeatUntil(new Condition("isEqualTo", "${vars.boom}", "b"), null, null, null);
        when(interpolation.outcome(eq("${vars.boom}"), any())).thenThrow(new BratException("nope"));

        // when / then
        assertThatThrownBy(() -> interpolator.interpolated(repeatUntil, interpolation, runtimeData))
                .isInstanceOf(BratException.class);
    }
}
