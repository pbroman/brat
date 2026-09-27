package dev.pbroman.brat.core.data;

import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlowControlTest {

    @Test
    void waitAfterMs_isTheDeclaredPause() {
        // when
        var millis = new FlowControl("500", null).waitAfterMs();

        // then
        assertThat(millis).isEqualTo(500L);
    }

    @Test
    void waitAfterMs_isZeroWhenNoneIsDeclared() {
        // when
        var millis = new FlowControl(null, null).waitAfterMs();

        // then
        assertThat(millis).isZero();
    }

    @Test
    void waitAfterMs_throwsOnAnUninterpolatedToken() {
        // given - the value an authored block holds before the interpolator has run
        var flowControl = new FlowControl("${vars.pause}", null);

        // when / then
        assertThatThrownBy(flowControl::waitAfterMs)
                .isInstanceOf(BratException.class)
                .hasMessageContaining("${vars.pause}");
    }

    @Test
    void waitAfterMs_throwsForANegativePause() {
        // given
        var flowControl = new FlowControl("-5", null);

        // when / then
        assertThatThrownBy(flowControl::waitAfterMs)
                .isInstanceOf(BratException.class)
                .hasMessageContaining("-5");
    }
}
