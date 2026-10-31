package dev.pbroman.brat.core.runner;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import dev.pbroman.brat.core.exception.BratException;

/**
 * Static helpers for the waits a run makes — the pause after a request and the wait between a poll's
 * attempts — which must both end promptly when the run is asked to stop.
 */
final class PauseUtils {

    /** The longest the thread sleeps between two checks, in milliseconds. */
    static final long SLICE_MILLIS = 100;

    private PauseUtils() {}

    /**
     * Sleeps for {@code millis}, in slices of at most {@value #SLICE_MILLIS} ms, ending early when
     * {@code stopRequested} holds.
     * <p>
     * The check is consulted <strong>before the first slice and after each one</strong>, so a stop is
     * noticed within a slice, a pause that would begin on a stopped run does not begin, and a pause of
     * {@code 0} or less still consults it once — and then sleeps not at all.
     *
     * @param millis how long to sleep; {@code 0} or less sleeps not at all
     * @param stopRequested whether the wait should end now; must not be {@code null}
     * @param interruptedMessage the message of the exception thrown on an interrupt, saying which wait
     *        it was
     * @return {@code true} if the whole of {@code millis} passed without the check holding;
     *         {@code false} if it held first
     * @throws BratException if the thread is interrupted while sleeping, or already was on entry with
     *         time left to sleep; the interrupt flag is restored
     */
    static boolean pause(long millis, BooleanSupplier stopRequested, String interruptedMessage) {
        var deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
        while (!stopRequested.getAsBoolean()) {
            var remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                return true;
            }
            try {
                Thread.sleep(Duration.ofNanos(Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(SLICE_MILLIS))));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BratException(interruptedMessage, e);
            }
        }
        return false;
    }
}
