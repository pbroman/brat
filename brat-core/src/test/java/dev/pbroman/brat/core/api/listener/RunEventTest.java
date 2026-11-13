package dev.pbroman.brat.core.api.listener;

import java.time.Instant;

import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RunEventTest {

    @Test
    void runStarted_throwsForANullMask() {
        // when / then
        assertThatThrownBy(() -> new RunEvent.RunStarted(Instant.now(), null)).isInstanceOf(BratException.class);
    }
}
