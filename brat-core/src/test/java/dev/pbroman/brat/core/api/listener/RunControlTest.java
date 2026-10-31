package dev.pbroman.brat.core.api.listener;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RunControlTest {

    @Test
    void create_returnsAControlNeitherStoppingNorCancelled() {
        // when
        var control = RunControl.create();

        // then
        assertThat(control.isStopping()).isFalse();
        assertThat(control.isCancelled()).isFalse();
    }

    @Test
    void create_returnsANewControlOnEveryCall() {
        // given
        var first = RunControl.create();

        // when
        first.cancel();

        // then - one run's stop never reaches another's control
        assertThat(RunControl.create().isStopping()).isFalse();
    }

    @Test
    void stop_makesTheControlStoppingButNotCancelled() {
        // given
        var control = RunControl.create();

        // when
        control.stop();

        // then
        assertThat(control.isStopping()).isTrue();
        assertThat(control.isCancelled()).isFalse();
    }

    @Test
    void cancel_makesTheControlBothCancelledAndStopping() {
        // given
        var control = RunControl.create();

        // when
        control.cancel();

        // then
        assertThat(control.isCancelled()).isTrue();
        assertThat(control.isStopping()).isTrue();
    }

    @Test
    void cancel_escalatesAStop() {
        // given
        var control = RunControl.create();
        control.stop();

        // when
        control.cancel();

        // then
        assertThat(control.isCancelled()).isTrue();
    }

    @Test
    void stop_leavesACancelledControlCancelled() {
        // given
        var control = RunControl.create();
        control.cancel();

        // when
        control.stop();

        // then
        assertThat(control.isCancelled()).isTrue();
        assertThat(control.isStopping()).isTrue();
    }

    @Test
    void stopAndCancel_latchWhenCalledRepeatedly() {
        // given
        var control = RunControl.create();

        // when
        control.stop();
        control.stop();
        control.cancel();
        control.cancel();

        // then - a latch, not a counter or a toggle
        assertThat(control.isStopping()).isTrue();
        assertThat(control.isCancelled()).isTrue();
    }
}
