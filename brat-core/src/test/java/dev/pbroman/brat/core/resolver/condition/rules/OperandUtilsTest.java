package dev.pbroman.brat.core.resolver.condition.rules;

import java.util.List;
import java.util.Map;

import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OperandUtilsTest {

    private static final String SUBJECT = "the Test func 'isShiny'";

    // --- requireText ---

    @Test
    void requireText_acceptsAScalar() {
        // when / then
        assertThatCode(() -> OperandUtils.requireText("text", SUBJECT)).doesNotThrowAnyException();
        assertThatCode(() -> OperandUtils.requireText(42, SUBJECT)).doesNotThrowAnyException();
    }

    @Test
    void requireText_throwsForASequenceNamingTheFuncButNotTheContent() {
        // when / then
        assertThatThrownBy(() -> OperandUtils.requireText(List.of("s3cr3t"), SUBJECT))
                .isInstanceOf(BratException.class)
                .hasMessageStartingWith("The Test func 'isShiny'")
                .hasMessageContaining("a sequence")
                .hasMessageNotContaining("s3cr3t");
    }

    @Test
    void requireText_throwsForAMappingNamingTheFuncButNotTheContent() {
        // when / then
        assertThatThrownBy(() -> OperandUtils.requireText(Map.of("key", "s3cr3t"), SUBJECT))
                .isInstanceOf(BratException.class)
                .hasMessageStartingWith("The Test func 'isShiny'")
                .hasMessageContaining("a mapping")
                .hasMessageNotContaining("s3cr3t");
    }

    // --- size ---

    @Test
    void size_readsANumberOrItsText() {
        // when / then
        assertThat(OperandUtils.size(3)).isEqualTo(3);
        assertThat(OperandUtils.size("3")).isEqualTo(3);
        assertThat(OperandUtils.size(" 3 ")).isEqualTo(3);
        assertThat(OperandUtils.size("3.0")).isEqualTo(3);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "2.5", ""})
    void size_throwsForWhatIsNotAWholeNumber(String value) {
        // when / then
        assertThatThrownBy(() -> OperandUtils.size(value))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("whole number");
    }

    @Test
    void size_throwsForNull() {
        // when / then
        assertThatThrownBy(() -> OperandUtils.size(null)).isInstanceOf(BratException.class);
    }

    // --- sizeBetween ---

    @Test
    void sizeBetween_isInclusiveAtBothEnds() {
        // given
        var args = Map.of("min", "2", "max", "4");

        // when / then
        assertThat(OperandUtils.sizeBetween(1, args, SUBJECT)).isFalse();
        assertThat(OperandUtils.sizeBetween(2, args, SUBJECT)).isTrue();
        assertThat(OperandUtils.sizeBetween(4, args, SUBJECT)).isTrue();
        assertThat(OperandUtils.sizeBetween(5, args, SUBJECT)).isFalse();
    }

    @Test
    void sizeBetween_throwsForAnUnknownArgument() {
        // when / then
        assertThatThrownBy(() -> OperandUtils.sizeBetween(3, Map.of("min", "1", "max", "4", "mix", "2"), SUBJECT))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("mix");
    }

    @Test
    void sizeBetween_throwsForAMissingBound() {
        // when / then
        assertThatThrownBy(() -> OperandUtils.sizeBetween(3, Map.of("min", "1"), SUBJECT))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("max");
    }

    @Test
    void sizeBetween_throwsForABoundThatIsNotAWholeNumber() {
        // when / then
        assertThatThrownBy(() -> OperandUtils.sizeBetween(3, Map.of("min", "1", "max", "four"), SUBJECT))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("whole number");
    }
}
