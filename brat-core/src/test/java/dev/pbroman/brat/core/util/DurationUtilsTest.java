package dev.pbroman.brat.core.util;

import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DurationUtilsTest {

    @Test
    void nonNegativeMillis_readsAWholeNumber() {
        // when
        var millis = DurationUtils.nonNegativeMillis("250", "waitAfter");

        // then
        assertThat(millis).isEqualTo(250L);
    }

    @Test
    void nonNegativeMillis_isZeroWhenNothingWasDeclared() {
        // when
        var millis = DurationUtils.nonNegativeMillis(null, "waitAfter");

        // then
        assertThat(millis).isZero();
    }

    @Test
    void nonNegativeMillis_acceptsZero() {
        // when - no pause is an ordinary pace, not an error
        var millis = DurationUtils.nonNegativeMillis("0", "waitAfter");

        // then
        assertThat(millis).isZero();
    }

    @Test
    void nonNegativeMillis_ignoresSurroundingWhitespace() {
        // when
        var millis = DurationUtils.nonNegativeMillis(" 40 ", "waitAfter");

        // then
        assertThat(millis).isEqualTo(40L);
    }

    @Test
    void nonNegativeMillis_throwsNamingTheFieldAndValueForSomethingNotAWholeNumber() {
        // when / then
        assertThatThrownBy(() -> DurationUtils.nonNegativeMillis("soon", "waitAfter"))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("waitAfter")
                .hasMessageContaining("soon");
    }

    @Test
    void nonNegativeMillis_throwsForABlankValue() {
        // when / then - blank is declared but says nothing, unlike null
        assertThatThrownBy(() -> DurationUtils.nonNegativeMillis("  ", "waitAfter"))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("waitAfter");
    }

    @Test
    void nonNegativeMillis_throwsNamingTheFieldAndValueForANegativeNumber() {
        // when / then
        assertThatThrownBy(() -> DurationUtils.nonNegativeMillis("-1", "waitBetweenAttempts"))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("waitBetweenAttempts")
                .hasMessageContaining("-1");
    }
}
