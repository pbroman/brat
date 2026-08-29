package dev.pbroman.brat.core.interpolation;

import java.util.Map;

import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.api.interpolation.InterpolationRule;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

public abstract class AbstractInterpolationTest {

    protected static final String mockResult = "mockResult";

    protected static final String nonMatchingPattern = "${bollocks}";

    protected RuntimeData runtimeData;

    protected final Interpolation mockRule = Mockito.mock(Interpolation.class);

    protected InterpolationRule underTest;

    /**
     * Resolves {@code input} the way the dispatcher will: the rule's value if it claims the token,
     * the input unchanged if it declines.
     */
    protected Object interpolate(String input, RuntimeData data) {
        return claimed(input, data).value();
    }

    /** The claiming rule's outcome, or one equal to the input where it declined. */
    protected InterpolationOutcome claimed(String input, RuntimeData data) {
        return underTest.outcome(input, data).orElseGet(() -> new InterpolationOutcome(input, input));
    }

    @BeforeEach
    void basicSetUp() {
        when(mockRule.outcome(Mockito.anyString(), Mockito.any()))
                .thenReturn(new InterpolationOutcome(mockResult, mockResult));
        when(mockRule.outcome(eq(nonMatchingPattern), Mockito.any()))
                .thenReturn(new InterpolationOutcome(nonMatchingPattern, nonMatchingPattern));
        runtimeData = setUpRuntimeData();
    }

    protected RuntimeData setUpRuntimeData() {
        return new RuntimeData(Map.of("moo", "baa"), Map.of());
    }

    @Test
    void inputNull_throwsException() {
        // then
        assertThatThrownBy(() -> interpolate(null, runtimeData)).isInstanceOf(BratException.class);
    }

    /**
     * A token the subject owns, so that it resolves rather than declining.
     * <p>
     * A rule declines a token of another namespace without looking at {@code runtimeData} at all, so
     * a test needing the resolving path must hand it one of its own.
     */
    protected abstract String ownToken();

    @Test
    void runtimeDataNull_throwsBratException() {
        assertThatThrownBy(() -> interpolate(ownToken(), null)).isInstanceOf(BratException.class);
    }

    @Test
    void noMatchingPattern_returnsInput() {
        // given
        var input = "${bollocks}";

        // when
        var result = interpolate(input, runtimeData);

        // then
        assertThat(result).isEqualTo(input);
    }
}
