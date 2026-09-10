package dev.pbroman.brat.core.util;

import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FailureMessagesTest {

    @Test
    void causeOf_usesABratExceptionsMessageAsWritten() {
        // when
        var message = FailureMessages.causeOf(new BratException("The constant 'x' is not set."), "A request");

        // then - the framing is added, the message itself is not touched
        assertThat(message).isEqualTo("A request failed, reason: The constant 'x' is not set.");
    }

    @Test
    void causeOf_namesTheTypeOfAnUnplannedException() {
        // when
        var message = FailureMessages.causeOf(new IllegalStateException("broke"), "A request");

        // then - a core or plugin defect must stay diagnosable, not read as an authoring mistake
        assertThat(message).isEqualTo("A request failed, reason: IllegalStateException: broke");
    }

    @Test
    void causeOf_readsNullWhereAnUnplannedExceptionCarriedNoMessage() {
        // when
        var message = FailureMessages.causeOf(new IllegalStateException(), "A request");

        // then
        assertThat(message).isEqualTo("A request failed, reason: IllegalStateException: null");
    }

    @Test
    void causeOf_readsNullWhereABratExceptionCarriedNoMessage() {
        // when - the message is returned as written, and "as written" includes absent
        var message = FailureMessages.causeOf(new BratException(null), "A request");

        // then - "as written" includes absent, so only the framing survives
        assertThat(message).isEqualTo("A request failed, reason: null");
    }

    @Test
    void causeOf_treatsASubclassOfBratExceptionAsAuthored() {
        // given
        var subclass = new BratException("authored") {};

        // when / then
        assertThat(FailureMessages.causeOf(subclass, "A request")).isEqualTo("A request failed, reason: authored");
    }

    @Test
    void causeOf_throwsForANullException() {
        // when / then
        assertThatThrownBy(() -> FailureMessages.causeOf(null, "A request")).isInstanceOf(BratException.class);
    }

    @Test
    void causeOf_throwsForANullDescription() {
        // when / then
        assertThatThrownBy(() -> FailureMessages.causeOf(new IllegalStateException("broke"), null))
                .isInstanceOf(BratException.class);
    }
}
