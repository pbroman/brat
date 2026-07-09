package dev.pbroman.brat.core.api.interpolation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import dev.pbroman.brat.core.exception.BratException;

class InterpolationOutcomeTest {

    @Test
    void constructor_storesAllFields() {
        // when
        var outcome = new InterpolationOutcome("resolved", "raw → resolved", true);

        // then
        assertThat(outcome.value()).isEqualTo("resolved");
        assertThat(outcome.reportingString()).isEqualTo("raw → resolved");
        assertThat(outcome.containsSecret()).isTrue();
    }

    @Test
    void constructor_throwsExceptionWhenValueIsNull() {
        // then
        assertThatThrownBy(() -> new InterpolationOutcome(null, "reportingString", false))
                .isInstanceOf(BratException.class);
    }

    @Test
    void constructor_throwsExceptionWhenReportingStringIsNull() {
        // then
        assertThatThrownBy(() -> new InterpolationOutcome("value", null, false))
                .isInstanceOf(BratException.class);
    }

    @Test
    void constructor_defaultsContainsSecretToFalse() {
        // when
        var outcome = new InterpolationOutcome("value", "reportingString");

        // then
        assertThat(outcome.containsSecret()).isFalse();
    }

    @Test
    void asString_returnsValueAsString() {
        // given
        var outcome = new InterpolationOutcome(42, "reportingString");

        // when
        var result = outcome.asString();

        // then
        assertThat(result).isEqualTo("42");
    }

}
