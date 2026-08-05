package dev.pbroman.brat.core.interpolation.functions;

import java.util.List;
import java.util.Locale;

import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TextFunctionsTest {

    private static String call(String name, String... args) {
        return TextFunctions.functions().stream()
                .filter(function -> function.name().equals(name))
                .findFirst()
                .orElseThrow()
                .apply(List.of(args));
    }

    // --- upper / lower ---

    @Test
    void upper_andLower_convertCase() {
        // when / then
        assertThat(call("upper", "aBc")).isEqualTo("ABC");
        assertThat(call("lower", "aBc")).isEqualTo("abc");
    }

    @Test
    void upper_andLower_ignoreTheDefaultLocale() {
        // given — Turkish maps i to İ, so a locale-sensitive conversion differs
        var original = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr"));
        try {
            // when / then
            assertThat(call("upper", "i")).isEqualTo("I");
            assertThat(call("lower", "I")).isEqualTo("i");
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    void upper_andLower_throwForTheWrongArgumentCount() {
        // when / then
        assertThatThrownBy(() -> call("upper")).isInstanceOf(BratException.class);
        assertThatThrownBy(() -> call("lower", "a", "b")).isInstanceOf(BratException.class);
    }

    // --- trim / length ---

    @Test
    void trim_removesWhitespaceFromBothEnds() {
        // when
        var result = call("trim", "  a b  ");

        // then — inner whitespace is untouched
        assertThat(result).isEqualTo("a b");
    }

    @Test
    void length_countsCharacters() {
        // when / then
        assertThat(call("length", "abcd")).isEqualTo("4");
        assertThat(call("length", "")).isEqualTo("0");
    }

    // --- substring ---

    @Test
    void substring_takesFromStartToEndExclusive() {
        // when
        var result = call("substring", "abcdefgh", "0", "4");

        // then
        assertThat(result).isEqualTo("abcd");
    }

    @Test
    void substring_runsToTheEndWhenNoEndIsGiven() {
        // when
        var result = call("substring", "abcdefgh", "5");

        // then
        assertThat(result).isEqualTo("fgh");
    }

    @Test
    void substring_throwsForAnEndBeyondTheText() {
        // when / then — an error rather than a silent clamp
        assertThatThrownBy(() -> call("substring", "abc", "0", "99")).isInstanceOf(BratException.class);
    }

    @Test
    void substring_throwsForANegativeStart() {
        // when / then
        assertThatThrownBy(() -> call("substring", "abc", "-1")).isInstanceOf(BratException.class);
    }

    @Test
    void substring_throwsWhenStartIsAfterEnd() {
        // when / then
        assertThatThrownBy(() -> call("substring", "abc", "2", "1")).isInstanceOf(BratException.class);
    }

    @Test
    void substring_throwsForAnIndexBeyondTheIntRange() {
        // when / then — narrowing this silently could wrap it back into a valid-looking index
        assertThatThrownBy(() -> call("substring", "abc", "4294967296")).isInstanceOf(BratException.class);
    }

    @Test
    void substring_throwsForANonNumericIndex() {
        // when / then
        assertThatThrownBy(() -> call("substring", "abc", "first")).isInstanceOf(BratException.class);
    }

    // --- replace ---

    @Test
    void replace_replacesEveryOccurrence() {
        // when
        var result = call("replace", "a/b/c", "/", "_");

        // then
        assertThat(result).isEqualTo("a_b_c");
    }

    @Test
    void replace_treatsTheSearchAsLiteralNotARegularExpression() {
        // when — a dot would match everything if this were a regex
        var result = call("replace", "a.b.c", ".", "-");

        // then
        assertThat(result).isEqualTo("a-b-c");
    }

    @Test
    void replace_returnsTheTextUnchangedWhenNothingMatches() {
        // when
        var result = call("replace", "abc", "z", "y");

        // then
        assertThat(result).isEqualTo("abc");
    }

    @Test
    void replace_throwsForTheWrongArgumentCount() {
        // when / then
        assertThatThrownBy(() -> call("replace", "abc", "a")).isInstanceOf(BratException.class);
    }

    // --- default ---

    @Test
    void default_returnsTheValueWhenItIsNotEmpty() {
        // when
        var result = call("default", "actual", "fallback");

        // then
        assertThat(result).isEqualTo("actual");
    }

    @Test
    void default_returnsTheFallbackForAnEmptyValue() {
        // when — what a missing vars entry resolves to
        var result = call("default", "", "fallback");

        // then
        assertThat(result).isEqualTo("fallback");
    }

    @Test
    void default_treatsWhitespaceAsAValue() {
        // when — only empty triggers the fallback, so a deliberate space survives
        var result = call("default", " ", "fallback");

        // then
        assertThat(result).isEqualTo(" ");
    }

    @Test
    void default_throwsForTheWrongArgumentCount() {
        // when / then
        assertThatThrownBy(() -> call("default", "a")).isInstanceOf(BratException.class);
    }
}
