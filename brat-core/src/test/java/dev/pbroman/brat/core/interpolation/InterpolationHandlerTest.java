package dev.pbroman.brat.core.interpolation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.pbroman.brat.core.exception.BratException;

class InterpolationHandlerTest extends AbstractInterpolationTest {

    @BeforeEach
    void setUp() {
        underTest = new InterpolationHandler(mockRule, tools);
    }

    @Test
    void interpolate_inputWithNoVariables() {
        // given
        var input = "this is a test";

        // when
        var result = underTest.interpolate(input, runtimeData);

        // then
        assertThat(result).isEqualTo(input);
    }

    @Test
    void interpolate_inputWithOneVariables() {
        // given
        var input = "this is a ${mock}";
        var expected = "this is a " + mockResult;

        // when
        var result = underTest.interpolate(input, runtimeData);

        // then
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void interpolate_inputWithTwoVariables() {
        // given
        var input = "this ${some.var} is a ${mock}";
        var expected = String.format("this %s is a %s", mockResult, mockResult);

        // when
        var result = underTest.interpolate(input, runtimeData);

        // then
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void interpolate_inputWithException() {
        // given
        when(mockRule.outcome(Mockito.anyString(), Mockito.any()))
                .thenThrow(new BratException("mock"));
        var input = "this is a ${mock}";

        // when / then
        assertThatThrownBy(() -> underTest.interpolate(input, runtimeData))
                .isInstanceOf(BratException.class);
    }

}