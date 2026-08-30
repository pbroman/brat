package dev.pbroman.brat.core.runner;

import dev.pbroman.brat.core.data.Condition;
import dev.pbroman.brat.core.data.FlowControl;
import dev.pbroman.brat.core.data.RepeatUntil;
import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.Test;

import static dev.pbroman.brat.core.util.Constants.DEFAULT_MAX_ATTEMPTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PollBoundsTest {

    private static final Condition CONDITION = new Condition("isEqualTo", "${response.statusCode}", "200");

    private FlowControl flowControl(String maxAttempts, String wait, String messageOnFail) {
        return new FlowControl(null, new RepeatUntil(CONDITION, maxAttempts, wait, messageOnFail));
    }

    @Test
    void of_isEmptyForARequestDeclaringNoFlowControl() {
        // when / then - a request that runs exactly once
        assertThat(PollBounds.of(null)).isEmpty();
    }

    @Test
    void of_isEmptyForAFlowControlDeclaringNoRepeatUntil() {
        // given - waitAfter alone is pacing, not polling
        var flowControl = new FlowControl("500", null);

        // when / then
        assertThat(PollBounds.of(flowControl)).isEmpty();
    }

    @Test
    void of_readsEveryDeclaredValue() {
        // when
        var bounds = PollBounds.of(flowControl("5", "250", "still processing")).orElseThrow();

        // then
        assertThat(bounds.condition()).isEqualTo(CONDITION);
        assertThat(bounds.maxAttempts()).isEqualTo(5);
        assertThat(bounds.waitBetweenAttempts()).isEqualTo(250);
        assertThat(bounds.messageOnFail()).isEqualTo("still processing");
    }

    @Test
    void of_defaultsMaxAttemptsSoThatALoopBailsWhoeverWroteIt() {
        // when
        var bounds = PollBounds.of(flowControl(null, "0", null)).orElseThrow();

        // then
        assertThat(bounds.maxAttempts()).isEqualTo(DEFAULT_MAX_ATTEMPTS);
    }

    @Test
    void of_defaultsTheWaitToNone() {
        // when - a wait the author did not ask for is not ours to invent
        var bounds = PollBounds.of(flowControl("3", null, null)).orElseThrow();

        // then
        assertThat(bounds.waitBetweenAttempts()).isZero();
    }

    @Test
    void of_defaultsTheMessageOnFail() {
        // when - a report needs a sentence even where the author wrote none
        var bounds = PollBounds.of(flowControl("3", "0", null)).orElseThrow();

        // then
        assertThat(bounds.messageOnFail()).isEqualTo(PollBounds.DEFAULT_MESSAGE_ON_FAIL);
    }

    @Test
    void of_acceptsAZeroWaitAsAnOrdinaryPace() {
        // when / then - unlike maxAttempts, zero is meaningful here
        assertThat(PollBounds.of(flowControl("3", "0", null)).orElseThrow().waitBetweenAttempts())
                .isZero();
    }

    @Test
    void of_toleratesSurroundingWhitespace() {
        // given - an interpolated value may arrive padded
        var bounds = PollBounds.of(flowControl(" 4 ", " 100 ", null)).orElseThrow();

        // then
        assertThat(bounds.maxAttempts()).isEqualTo(4);
        assertThat(bounds.waitBetweenAttempts()).isEqualTo(100);
    }

    @Test
    void of_throwsForARepeatUntilWithNoCondition() {
        // given - a loop with nothing to wait for is not a shorter way of saying "repeat N times"
        var flowControl = new FlowControl(null, new RepeatUntil(null, "3", "0", null));

        // when / then - caught here, so no attempt is spent before it is found
        assertThatThrownBy(() -> PollBounds.of(flowControl))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("must declare the condition");
    }

    @Test
    void of_throwsForAnUnparseableMaxAttempts() {
        // when / then
        assertThatThrownBy(() -> PollBounds.of(flowControl("soon", "0", null)))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("maxAttempts")
                .hasMessageContaining("soon");
    }

    @Test
    void of_throwsForANonPositiveMaxAttempts() {
        // when / then - no attempts is not a request
        assertThatThrownBy(() -> PollBounds.of(flowControl("0", "0", null)))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("must be a positive number");
        assertThatThrownBy(() -> PollBounds.of(flowControl("-1", "0", null)))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("must be a positive number");
    }

    @Test
    void of_throwsForAnUnparseableWait() {
        // when / then
        assertThatThrownBy(() -> PollBounds.of(flowControl("3", "soon", null)))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("waitBetweenAttempts")
                .hasMessageContaining("milliseconds");
    }

    @Test
    void of_throwsForANegativeWait() {
        // when / then
        assertThatThrownBy(() -> PollBounds.of(flowControl("3", "-1", null)))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("must not be negative");
    }
}
