package dev.pbroman.brat.core.api.interpolation;

import java.util.List;

import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FunctionArgsTest {

    // --- requireArgCount ---

    @Test
    void requireArgCount_acceptsACountWithinTheRange() {
        // when / then — the bounds themselves are inside the range
        assertThatCode(() -> FunctionArgs.requireArgCount("f", List.of("a"), 1, 2))
                .doesNotThrowAnyException();
        assertThatCode(() -> FunctionArgs.requireArgCount("f", List.of("a", "b"), 1, 2))
                .doesNotThrowAnyException();
        assertThatCode(() -> FunctionArgs.requireArgCount("f", List.of(), 0, 0)).doesNotThrowAnyException();
    }

    @Test
    void requireArgCount_throwsForTooFewAndTooMany() {
        // when / then
        assertThatThrownBy(() -> FunctionArgs.requireArgCount("f", List.of(), 1, 2))
                .isInstanceOf(BratException.class);
        assertThatThrownBy(() -> FunctionArgs.requireArgCount("f", List.of("a", "b", "c"), 1, 2))
                .isInstanceOf(BratException.class);
    }

    @Test
    void requireArgCount_namesTheFunctionAsAnAuthorWroteIt() {
        // when / then — the __ prefix is what appears in the suite, so it appears in the message
        assertThatThrownBy(() -> FunctionArgs.requireArgCount("slugify", List.of(), 1, 1))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("__slugify")
                .hasMessageContaining("exactly 1");
    }

    @Test
    void requireArgCount_describesARangeWhenTheBoundsDiffer() {
        // when / then
        assertThatThrownBy(() -> FunctionArgs.requireArgCount("f", List.of(), 1, 3))
                .hasMessageContaining("1 to 3");
    }

    // --- decimal / integer ---

    @Test
    void decimal_parsesANumberIgnoringSurroundingWhitespace() {
        // when / then
        assertThat(FunctionArgs.decimal("f", " 1.5 ")).isEqualByComparingTo("1.5");
        assertThat(FunctionArgs.decimal("f", "-2")).isEqualByComparingTo("-2");
    }

    @Test
    void decimal_throwsForSomethingThatIsNotANumber() {
        // when / then
        assertThatThrownBy(() -> FunctionArgs.decimal("f", "one"))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("one");
    }

    @Test
    void integer_parsesAWholeNumber() {
        // when / then
        assertThat(FunctionArgs.integer("f", " 42 ")).isEqualTo(42L);
    }

    @Test
    void integer_throwsForADecimal() {
        // when / then — a whole number is a whole number
        assertThatThrownBy(() -> FunctionArgs.integer("f", "1.5")).isInstanceOf(BratException.class);
    }

    // --- integerWithin ---

    @Test
    void integerWithin_returnsAValueInsideTheRange() {
        // when / then — the bounds themselves are inside it
        assertThat(FunctionArgs.integerWithin("f", "5", 0, 10)).isEqualTo(5);
        assertThat(FunctionArgs.integerWithin("f", "0", 0, 10)).isZero();
        assertThat(FunctionArgs.integerWithin("f", "10", 0, 10)).isEqualTo(10);
    }

    @Test
    void integerWithin_throwsOutsideTheRange() {
        // when / then
        assertThatThrownBy(() -> FunctionArgs.integerWithin("f", "-1", 0, 10))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("between 0 and 10");
        assertThatThrownBy(() -> FunctionArgs.integerWithin("f", "11", 0, 10)).isInstanceOf(BratException.class);
    }

    @Test
    void integerWithin_rejectsRatherThanWrappingAValueBeyondTheIntRange() {
        // when / then — the whole point: a plain (int) cast turns this into 0
        assertThatThrownBy(() -> FunctionArgs.integerWithin("f", "4294967296", 0, Integer.MAX_VALUE))
                .isInstanceOf(BratException.class);
    }

    @Test
    void integerWithin_throwsForSomethingThatIsNotAWholeNumber() {
        // when / then
        assertThatThrownBy(() -> FunctionArgs.integerWithin("f", "1.5", 0, 10)).isInstanceOf(BratException.class);
    }

    // --- requireRange ---

    @Test
    void requireRange_acceptsALowerBoundAtOrBelowTheUpper() {
        // when / then
        assertThatCode(() -> FunctionArgs.requireRange("f", 1, 3)).doesNotThrowAnyException();
        assertThatCode(() -> FunctionArgs.requireRange("f", 3, 3)).doesNotThrowAnyException();
    }

    @Test
    void requireRange_throwsWhenTheBoundsAreInverted() {
        // when / then
        assertThatThrownBy(() -> FunctionArgs.requireRange("f", 5, 1))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("5")
                .hasMessageContaining("1");
    }
}
