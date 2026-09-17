package dev.pbroman.brat.core.api.interpolation;

import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    void constructor_acceptsANullValue() {
        // when - a token resolving onto a JSON null is a value the body holds, not a failed lookup
        var outcome = new InterpolationOutcome(null, "${response.json.$.email} → null", false);

        // then
        assertThat(outcome.value()).isNull();
        assertThat(outcome.reportingString()).isEqualTo("${response.json.$.email} → null");
    }

    @Test
    void asString_throwsForANullValue() {
        // given
        var outcome = new InterpolationOutcome(null, "${response.json.$.email} → null");

        // then - the message names the token, which only the reporting string knows
        assertThatThrownBy(outcome::asString)
                .isInstanceOf(BratException.class)
                .hasMessageContaining("${response.json.$.email}");
    }

    @Test
    void constructor_throwsExceptionWhenReportingStringIsNull() {
        // then
        assertThatThrownBy(() -> new InterpolationOutcome("value", null, false)).isInstanceOf(BratException.class);
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
