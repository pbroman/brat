package dev.pbroman.brat.core.api.reporting;

import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValueMaskTest {

    private final ValueMask underTest = new ValueMask();

    // --- adding ---

    @Test
    void addSecret_throwsForNull() {
        // when / then
        assertThatThrownBy(() -> underTest.addSecret(null)).isInstanceOf(BratException.class);
    }

    @Test
    void addGuessed_throwsForNull() {
        // when / then
        assertThatThrownBy(() -> underTest.addGuessed(null)).isInstanceOf(BratException.class);
    }

    // --- mask ---

    @Test
    void mask_returnsTextUnchangedWhenNothingWasAdded() {
        // when
        var masked = underTest.mask("Bearer abcd1234", false);

        // then
        assertThat(masked).isEqualTo("Bearer abcd1234");
    }

    @Test
    void mask_replacesEveryOccurrenceOfASecret() {
        // given
        underTest.addSecret("hunter2");

        // when
        var masked = underTest.mask("password=hunter2&again=hunter2", false);

        // then
        assertThat(masked).isEqualTo("password=***&again=***");
    }

    @Test
    void mask_replacesAGuessedValue() {
        // given
        underTest.addGuessed("eyJhbGciOi");

        // when
        var masked = underTest.mask("{\"access_token\": \"eyJhbGciOi\"}", false);

        // then
        assertThat(masked).isEqualTo("{\"access_token\": \"***\"}");
    }

    @Test
    void mask_leavesAGuessedValueWhenShowingSecrets() {
        // given
        underTest.addGuessed("eyJhbGciOi");

        // when
        var masked = underTest.mask("token eyJhbGciOi", true);

        // then
        assertThat(masked).isEqualTo("token eyJhbGciOi");
    }

    @Test
    void mask_replacesASecretEvenWhenShowingSecrets() {
        // given
        underTest.addSecret("hunter2");

        // when
        var masked = underTest.mask("password=hunter2", true);

        // then
        assertThat(masked).isEqualTo("password=***");
    }

    @Test
    void mask_treatsAValueAddedAsBothAsASecret() {
        // given
        underTest.addGuessed("hunter2");
        underTest.addSecret("hunter2");

        // when
        var masked = underTest.mask("password=hunter2", true);

        // then
        assertThat(masked).isEqualTo("password=***");
    }

    @Test
    void mask_leavesAValueShorterThanTheMinimumLength() {
        // given
        underTest.addSecret("abc");
        underTest.addGuessed("xyz");

        // when
        var masked = underTest.mask("abc xyz", false);

        // then
        assertThat(masked).isEqualTo("abc xyz");
    }

    @Test
    void mask_replacesAValueOfExactlyTheMinimumLength() {
        // given
        underTest.addSecret("a".repeat(ValueMask.MIN_LENGTH));

        // when
        var masked = underTest.mask("[aaaa]", false);

        // then
        assertThat(masked).isEqualTo("[***]");
    }

    @Test
    void mask_replacesTheLongerOfTwoOverlappingValuesWhole() {
        // given - added shortest first, so order of adding cannot be what decides
        underTest.addSecret("abcd");
        underTest.addGuessed("abcdefgh");

        // when
        var masked = underTest.mask("x abcdefgh y", false);

        // then
        assertThat(masked).isEqualTo("x *** y");
    }

    @Test
    void mask_matchesExactlyIncludingCase() {
        // given
        underTest.addSecret("Hunter2");

        // when
        var masked = underTest.mask("hunter2 HUNTER2 Hunter2", false);

        // then
        assertThat(masked).isEqualTo("hunter2 HUNTER2 ***");
    }

    @Test
    void mask_appliesAValueAddedAfterAnEarlierMask() {
        // given
        underTest.mask("nothing yet", false);
        underTest.addSecret("hunter2");

        // when
        var masked = underTest.mask("hunter2", false);

        // then
        assertThat(masked).isEqualTo("***");
    }

    @Test
    void mask_throwsForNullText() {
        // when / then
        assertThatThrownBy(() -> underTest.mask(null, false)).isInstanceOf(BratException.class);
    }
}
