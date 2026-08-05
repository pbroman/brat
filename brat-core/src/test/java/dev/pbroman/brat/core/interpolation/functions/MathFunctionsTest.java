package dev.pbroman.brat.core.interpolation.functions;

import java.util.List;

import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MathFunctionsTest {

    private static String call(String name, String... args) {
        return MathFunctions.functions().stream()
                .filter(function -> function.name().equals(name))
                .findFirst()
                .orElseThrow()
                .apply(List.of(args));
    }

    @Test
    void add_subtract_multiply_areExact() {
        // when / then
        assertThat(call("add", "1", "2")).isEqualTo("3");
        assertThat(call("subtract", "10", "4")).isEqualTo("6");
        assertThat(call("multiply", "3", "4")).isEqualTo("12");
    }

    @Test
    void add_doesNotSufferBinaryFloatingPointError() {
        // when — 0.1 + 0.2 is 0.30000000000000004 as a double
        var result = call("add", "0.1", "0.2");

        // then
        assertThat(result).isEqualTo("0.3");
    }

    @Test
    void add_returnsAWholeNumberWithoutADecimalPart() {
        // when — the common case is a page number going straight into a URL
        var result = call("add", "1.5", "1.5");

        // then — "3", not "3.0"
        assertThat(result).isEqualTo("3");
    }

    @Test
    void multiply_writesLargeResultsInFullRatherThanAsAnExponent() {
        // when
        var result = call("multiply", "100", "100");

        // then
        assertThat(result).isEqualTo("10000");
    }

    @Test
    void divide_returnsAnExactResultWhenThereIsOne() {
        // when
        var result = call("divide", "10", "4");

        // then
        assertThat(result).isEqualTo("2.5");
    }

    @Test
    void divide_roundsANonTerminatingResult() {
        // when — 10/3 has no exact decimal form, so this must not throw
        var result = call("divide", "10", "3");

        // then
        assertThat(result).startsWith("3.333333333333333");
    }

    @Test
    void divide_throwsForAZeroDivisor() {
        // when / then
        assertThatThrownBy(() -> call("divide", "1", "0"))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("zero");
    }

    @Test
    void mod_returnsTheRemainder() {
        // when / then
        assertThat(call("mod", "10", "3")).isEqualTo("1");
        assertThat(call("mod", "9", "3")).isEqualTo("0");
    }

    @Test
    void mod_takesTheSignOfTheDividend() {
        // when
        var result = call("mod", "-10", "3");

        // then
        assertThat(result).isEqualTo("-1");
    }

    @Test
    void mod_throwsForAZeroDivisor() {
        // when / then
        assertThatThrownBy(() -> call("mod", "1", "0")).isInstanceOf(BratException.class);
    }

    @Test
    void math_throwsForANonNumericOperand() {
        // when / then
        assertThatThrownBy(() -> call("add", "one", "2"))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("one");
    }

    @Test
    void math_throwsForTheWrongArgumentCount() {
        // when / then
        assertThatThrownBy(() -> call("add", "1")).isInstanceOf(BratException.class);
        assertThatThrownBy(() -> call("add", "1", "2", "3")).isInstanceOf(BratException.class);
    }
}
