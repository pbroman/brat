package dev.pbroman.brat.core.interpolation.configdata;

import java.util.Map;

import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.data.RequestOptions;
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

class RequestOptionsInterpolatorTest {

    private final RequestOptionsInterpolator interpolator = new RequestOptionsInterpolator();

    private Interpolation interpolation;
    private RuntimeData runtimeData;

    @BeforeEach
    void setUp() {
        interpolation = mock(Interpolation.class);
        runtimeData = mock(RuntimeData.class);
        when(interpolation.outcome(anyString(), any()))
                .thenAnswer(i -> new InterpolationOutcome("5000", "r:" + i.getArgument(0)));
    }

    @Test
    void interpolated_interpolatesTheTimeout() {
        // given
        var options = new RequestOptions("${vars.timeout}");

        // when
        var result = interpolator.interpolated(options, interpolation, runtimeData);

        // then
        assertThat(result.getTimeout()).isEqualTo("5000");
        assertThat(result.isInterpolated()).isTrue();
    }

    @Test
    void interpolated_recordsTheOutcomeUnderABareKey() {
        // given — the options carry their own outcomes map, so nothing needs disambiguating
        var options = new RequestOptions("${vars.timeout}");

        // when
        var result = interpolator.interpolated(options, interpolation, runtimeData);

        // then
        assertThat(result.getOutcomes()).containsOnlyKeys("timeout");
    }

    @Test
    void interpolated_leavesAnUndeclaredTimeoutAlone() {
        // given
        var options = new RequestOptions(null);

        // when
        var result = interpolator.interpolated(options, interpolation, runtimeData);

        // then — the default is applied when the value is read, not baked in here
        assertThat(result.getTimeout()).isNull();
        assertThat(result.getOutcomes()).isEmpty();
        assertThat(result.isInterpolated()).isTrue();
    }

    @Test
    void interpolated_validatesTheResultSoAFailureLandsHereRatherThanInTheRetryLoop() {
        // given
        when(interpolation.outcome(anyString(), any()))
                .thenAnswer(i -> new InterpolationOutcome("soon", "r:" + i.getArgument(0)));
        var options = new RequestOptions("${vars.timeout}");

        // when / then
        assertThatThrownBy(() -> interpolator.interpolated(options, interpolation, runtimeData))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("soon");
    }

    @Test
    void interpolated_acceptsATimeoutResolvingToBlankAndLeavesItToTheDefault() {
        // given
        when(interpolation.outcome(anyString(), any()))
                .thenAnswer(i -> new InterpolationOutcome("", "r:" + i.getArgument(0)));
        var options = new RequestOptions("${vars.timeout}");

        // when
        var result = interpolator.interpolated(options, interpolation, runtimeData);

        // then
        assertThat(result.getOutcomes()).containsOnlyKeys("timeout");
        assertThat(result.timeoutMs()).isEqualTo(30000L);
    }

    @Test
    void interpolated_throwsIfTheTimeoutResolvesToZeroOrLess() {
        // given
        when(interpolation.outcome(anyString(), any()))
                .thenAnswer(i -> new InterpolationOutcome("0", "r:" + i.getArgument(0)));
        var options = new RequestOptions("${vars.timeout}");

        // when / then
        assertThatThrownBy(() -> interpolator.interpolated(options, interpolation, runtimeData))
                .isInstanceOf(BratException.class);
    }

    @Test
    void interpolated_throwsIfTargetIsNull() {
        // when / then
        assertThatThrownBy(() -> interpolator.interpolated(null, interpolation, runtimeData))
                .isInstanceOf(BratException.class);
    }

    @Test
    void interpolated_throwsIfTargetIsAlreadyACopy() {
        // given
        var copy = new RequestOptions("5000", Map.of("timeout", new InterpolationOutcome("5000", "5000")));

        // when / then
        assertThatThrownBy(() -> interpolator.interpolated(copy, interpolation, runtimeData))
                .isInstanceOf(BratException.class);
    }
}
