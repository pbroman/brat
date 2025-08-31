package dev.pbroman.brat.core.interpolation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import dev.pbroman.brat.core.exception.ValidationException;
import dev.pbroman.brat.core.data.result.ValidationType;

class InterpolationHandlerTest extends AbstractInterpolationTest {

    @BeforeEach
    void setUp() {
        underTest = new InterpolationHandler(mockRule, tools);
    }

    @Test
    void interpolate_inputWithNoVariables() throws Exception {
        // given
        var input = "this is a test";

        // when
        var result = underTest.interpolate(input, runtimeData);

        // then
        assertThat(result).isEqualTo(input);
    }

    @Test
    void interpolate_inputWithOneVariables() throws Exception {
        // given
        var input = "this is a ${mock}";
        var expected = "this is a " + mockResult;

        // when
        var result = underTest.interpolate(input, runtimeData);

        // then
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void interpolate_inputWithTwoVariables() throws Exception {
        // given
        var input = "this ${some.var} is a ${mock}";
        var expected = String.format("this %s is a %s", mockResult, mockResult);

        // when
        var result = underTest.interpolate(input, runtimeData);

        // then
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void interpolate_inputWithException() throws Exception {
        // given
        when(mockRule.interpolate(Mockito.anyString(), Mockito.any()))
                .thenThrow(new ValidationException("mock", ValidationType.FAIL));
        var input = "this is a ${mock}";

        // when
        var result = underTest.interpolate(input, runtimeData);

        // then
        assertThat(result).isEqualTo(input);
        /* TODO
        assertThat(runtimeData.validations())
                .isNotNull()
                .hasSize(1);

         */
    }


}