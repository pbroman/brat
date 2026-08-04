package dev.pbroman.brat.core.interpolation;

import java.util.List;

import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

class InterpolationScannerTest extends AbstractInterpolationTest {

    @BeforeEach
    void setUp() {
        underTest = new InterpolationScanner(mockRule, patterns);
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
        when(mockRule.outcome(Mockito.anyString(), Mockito.any())).thenThrow(new BratException("mock"));
        var input = "this is a ${mock}";

        // when / then
        assertThatThrownBy(() -> underTest.interpolate(input, runtimeData)).isInstanceOf(BratException.class);
    }

    // --- a field that is nothing but one token keeps the resolved value's type ---

    @Test
    void interpolate_keepsTheResolvedTypeWhenTheFieldIsASingleToken() {
        // given — the rule resolves to a structure, not to text
        var items = List.of("a", "b");
        when(mockRule.outcome("${mock}", runtimeData)).thenReturn(new InterpolationOutcome(items, "${mock} → [a, b]"));

        // when
        var result = underTest.interpolate("${mock}", runtimeData);

        // then
        assertThat(result).isInstanceOf(List.class).isEqualTo(items);
    }

    @Test
    void interpolate_stringifiesATokenEmbeddedInSurroundingText() {
        // given — the same structured value, but now it has to be spliced into a string
        var items = List.of("a", "b");
        when(mockRule.outcome("${mock}", runtimeData)).thenReturn(new InterpolationOutcome(items, "${mock} → [a, b]"));

        // when
        var result = underTest.interpolate("items: ${mock}", runtimeData);

        // then
        assertThat(result).isEqualTo("items: [a, b]");
    }

    @Test
    void interpolate_treatsTwoAdjacentTokensAsTwoTokens() {
        // given — the token pattern is lazy, so a `matches()` check would take "${mock}${mock}"
        // for a single token and pass the first value straight through
        var input = "${mock}${mock}";

        // when
        var result = underTest.interpolate(input, runtimeData);

        // then
        assertThat(result).isEqualTo(mockResult + mockResult);
    }
}
