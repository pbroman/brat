package dev.pbroman.brat.core.data;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;

import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;

abstract class AbstractConfigDataTest {

    Interpolation interpolation;
    RuntimeData runtimeData;

    @BeforeEach
    void setUp() {
        interpolation = mock(Interpolation.class);
        when(interpolation.outcome(anyString(), any()))
                .thenReturn(new InterpolationOutcome("interpolated", "interpolated"));
        runtimeData = mock(RuntimeData.class);
    }

    protected void assertInterpolatedThrowsExceptionIfCopy(ConfigData configData) {
        // given
        var interpolated = configData.interpolated(interpolation, runtimeData);

        // then
        assertThatThrownBy(() -> interpolated.interpolated(interpolation, runtimeData))
                .isInstanceOf(BratException.class);
    }

}
