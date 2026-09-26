package dev.pbroman.brat.core.data;

import java.util.Map;

import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestOptionsTest {

    @Test
    void timeoutMs_isTheDeclaredValue() {
        // given
        var options = new RequestOptions("5000");

        // when
        var millis = options.timeoutMs();

        // then
        assertThat(millis).isEqualTo(5000L);
    }

    @Test
    void timeoutMs_defaultsWhenNoTimeoutWasDeclared() {
        // when
        var millis = new RequestOptions(null).timeoutMs();

        // then
        assertThat(millis).isEqualTo(30000L);
    }

    @Test
    void timeoutMs_defaultsWhenTheTimeoutIsBlank() {
        // when
        var millis = new RequestOptions("   ").timeoutMs();

        // then
        assertThat(millis).isEqualTo(30000L);
    }

    @Test
    void timeoutMs_toleratesSurroundingWhitespace() {
        // when
        var millis = new RequestOptions(" 750 ").timeoutMs();

        // then
        assertThat(millis).isEqualTo(750L);
    }

    @Test
    void timeoutMs_throwsIfNotAWholeNumber() {
        // given
        var options = new RequestOptions("soon");

        // when / then
        assertThatThrownBy(options::timeoutMs).isInstanceOf(BratException.class).hasMessageContaining("soon");
    }

    @Test
    void timeoutMs_throwsIfZero() {
        // given
        var options = new RequestOptions("0");

        // when / then
        assertThatThrownBy(options::timeoutMs).isInstanceOf(BratException.class).hasMessageContaining("0");
    }

    @Test
    void timeoutMs_throwsIfNegative() {
        // given
        var options = new RequestOptions("-1");

        // when / then
        assertThatThrownBy(options::timeoutMs).isInstanceOf(BratException.class).hasMessageContaining("-1");
    }

    @Test
    void timeoutMs_throwsOnAnUninterpolatedToken() {
        // given — the value a walk assembles before the interpolator has run
        var options = new RequestOptions("${vars.timeout}");

        // when / then
        assertThatThrownBy(options::timeoutMs)
                .isInstanceOf(BratException.class)
                .hasMessageContaining("${vars.timeout}");
    }

    @Test
    void isInterpolated_isFalseForAnAssembledInstance() {
        // when
        var options = new RequestOptions("5000");

        // then
        assertThat(options.isInterpolated()).isFalse();
        assertThat(options.getOutcomes()).isNull();
    }

    @Test
    void isInterpolated_isTrueForACopyCarryingOutcomes() {
        // given
        var outcomes = Map.of("timeout", new InterpolationOutcome("5000", "${vars.t} -> 5000"));

        // when
        var options = new RequestOptions("5000", outcomes);

        // then
        assertThat(options.isInterpolated()).isTrue();
        assertThat(options.getOutcomes()).containsOnlyKeys("timeout");
    }
}
