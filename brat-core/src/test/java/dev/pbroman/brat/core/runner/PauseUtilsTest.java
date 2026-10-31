package dev.pbroman.brat.core.runner;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PauseUtilsTest {

    @Test
    void pause_sleepsTheWholeDurationAndSaysSo() {
        // given
        var started = System.nanoTime();

        // when
        var completed = PauseUtils.pause(250, () -> false, "pausing");

        // then
        assertThat(completed).isTrue();
        assertThat((System.nanoTime() - started) / 1_000_000).isGreaterThanOrEqualTo(250L);
    }

    @Test
    void pause_endsWithinASliceWhenTheCheckStartsHolding() throws InterruptedException {
        // given - asked to stop ten seconds before the pause would end
        var stop = new AtomicBoolean();
        var stopper = new Thread(() -> {
            try {
                Thread.sleep(150);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            stop.set(true);
        });
        var started = System.nanoTime();
        stopper.start();

        // when
        var completed = PauseUtils.pause(10_000, stop::get, "pausing");
        stopper.join();

        // then
        assertThat(completed).isFalse();
        assertThat((System.nanoTime() - started) / 1_000_000).isLessThan(2_000L);
    }

    @Test
    void pause_doesNotBeginWhenTheCheckAlreadyHolds() {
        // given
        var started = System.nanoTime();

        // when
        var completed = PauseUtils.pause(10_000, () -> true, "pausing");

        // then
        assertThat(completed).isFalse();
        assertThat((System.nanoTime() - started) / 1_000_000).isLessThan(1_000L);
    }

    @Test
    void pause_consultsTheCheckOnceForANonPositiveDuration() {
        // given
        var checks = new AtomicInteger();

        // when
        var zero = PauseUtils.pause(0, () -> checks.incrementAndGet() < 0, "pausing");
        var negative = PauseUtils.pause(-5, () -> true, "pausing");

        // then - zero sleeps nothing and completes; a holding check still wins
        assertThat(zero).isTrue();
        assertThat(checks).hasValue(1);
        assertThat(negative).isFalse();
    }

    @Test
    void pause_throwsWithTheGivenMessageAndRestoresTheFlagWhenInterrupted() {
        // given
        Thread.currentThread().interrupt();

        // when / then
        try {
            assertThatThrownBy(() -> PauseUtils.pause(5_000, () -> false, "Interrupted while pausing"))
                    .isInstanceOf(BratException.class)
                    .hasMessage("Interrupted while pausing");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }
}
