package dev.pbroman.brat.cli;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import dev.pbroman.brat.core.api.listener.RunControl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class InterruptStopperTest {

    private final RunControl control = RunControl.create();
    private final StringWriter err = new StringWriter();
    private final List<Integer> exits = new ArrayList<>();
    private final InterruptStopper underTest = new InterruptStopper(control, new PrintWriter(err, true), exits::add);

    @Test
    void interrupted_stopsTheRunGracefullyOnTheFirstPress() {
        // when
        underTest.interrupted();

        // then
        assertThat(control.isStopping()).isTrue();
        assertThat(control.isCancelled()).isFalse();
        assertThat(exits).isEmpty();
    }

    @Test
    void interrupted_cancelsTheRunOnTheSecondPress() {
        // when
        underTest.interrupted();
        underTest.interrupted();

        // then
        assertThat(control.isCancelled()).isTrue();
        assertThat(exits).isEmpty();
    }

    @Test
    void interrupted_exitsWith1OnTheThirdPress() {
        // when
        underTest.interrupted();
        underTest.interrupted();
        underTest.interrupted();

        // then
        assertThat(exits).containsExactly(1);
    }

    @Test
    void interrupted_exitsOnEveryPressAfterTheSecond() {
        // when - an exit that returned, as the test's does, must not fall back to stopping
        for (int press = 0; press < 4; press++) {
            underTest.interrupted();
        }

        // then
        assertThat(exits).containsExactly(1, 1);
    }

    @Test
    void interrupted_reportsEachPressOnOneLineOfItsOwn() {
        // when
        underTest.interrupted();
        underTest.interrupted();
        underTest.interrupted();

        // then
        assertThat(err.toString().lines()).hasSize(3).doesNotContain("");
    }

    @Test
    void interrupted_saysWhatTheNextPressWouldDo() {
        // when
        underTest.interrupted();

        // then - the first line is the only hint that a second press exists
        assertThat(err.toString()).containsIgnoringCase("teardown").containsIgnoringCase("again");
    }

    @Test
    void close_doesNothingForAStopperNeverInstalled() {
        // when / then
        assertThatCode(() -> {
                    underTest.close();
                    underTest.close();
                })
                .doesNotThrowAnyException();
    }
}
