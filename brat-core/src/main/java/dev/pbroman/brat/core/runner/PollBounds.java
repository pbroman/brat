package dev.pbroman.brat.core.runner;

import java.util.Optional;

import dev.pbroman.brat.core.data.Condition;
import dev.pbroman.brat.core.data.FlowControl;
import dev.pbroman.brat.core.data.RepeatUntil;
import dev.pbroman.brat.core.exception.BratException;

import static dev.pbroman.brat.core.util.Constants.DEFAULT_MAX_ATTEMPTS;

/**
 * What a {@code repeatUntil} loop needs to run, once its text has been resolved to numbers.
 * <p>
 * {@link RepeatUntil} is the authored shape: {@code maxAttempts} and {@code waitBetweenAttempts} are
 * text there, because either may be a {@code ${...}} token. This is the same thing after
 * interpolation and parsing — the form the loop can actually use, with every value already known to
 * be sane. Separating the two means a bad bound is rejected once, before any attempt is spent, rather
 * than discovered in the middle of the run.
 *
 * @param condition the condition that ends the loop when it holds
 * @param maxAttempts the attempt ceiling; always positive
 * @param waitBetweenAttempts the pause between attempts in milliseconds; never negative, and
 *        {@code 0} where the author declared none
 * @param messageOnFail what to report when the attempts are exhausted; never {@code null}
 */
record PollBounds(Condition condition, int maxAttempts, long waitBetweenAttempts, String messageOnFail) {

    /** Reported when the attempts run out and the author named no message of their own. */
    static final String DEFAULT_MESSAGE_ON_FAIL = "Max attempts exhausted";

    /**
     * Defaults {@code messageOnFail}, so nothing downstream has to.
     */
    PollBounds {
        messageOnFail = messageOnFail == null ? DEFAULT_MESSAGE_ON_FAIL : messageOnFail;
    }

    /**
     * The bounds declared by an already-interpolated flow control, if it declares a loop at all.
     * <p>
     * <strong>A loop with nothing to wait for is rejected too</strong>, not only a malformed number:
     * a {@code repeatUntil} without a condition is not a shorter way of saying "repeat N times", and
     * catching it here is what keeps the promise above — no attempt is spent before it is found.
     * <p>
     * Both numbers are rejected rather than defaulted when they are malformed, for the reason the
     * request handler rejects a malformed timeout: a ceiling the author believes is in effect and is
     * not is worse than a run that stops and says so. Note the two differ at zero — no attempts is
     * not a request, while no wait is an ordinary pace.
     *
     * @param flowControl the interpolated flow control, or {@code null} where the request declares
     *        none
     * @return the bounds, or {@link Optional#empty()} where nothing declares a {@code repeatUntil} —
     *         a request that runs exactly once
     * @throws BratException if the {@code repeatUntil} declares no condition, if {@code maxAttempts}
     *         is not a whole number or is not positive, or if {@code waitBetweenAttempts} is not a
     *         whole number of milliseconds or is negative
     */
    static Optional<PollBounds> of(FlowControl flowControl) {
        if (flowControl == null || flowControl.getRepeatUntil() == null) {
            return Optional.empty();
        }
        var repeatUntil = flowControl.getRepeatUntil();
        if (repeatUntil.getCondition() == null) {
            throw new BratException("A repeatUntil must declare the condition it waits for");
        }
        return Optional.of(new PollBounds(
                repeatUntil.getCondition(),
                maxAttempts(repeatUntil.getMaxAttempts()),
                waitBetweenAttempts(repeatUntil.getWaitBetweenAttempts()),
                repeatUntil.getMessageOnFail()));
    }

    private static int maxAttempts(String declared) {
        if (declared == null) {
            return DEFAULT_MAX_ATTEMPTS;
        }
        int parsed;
        try {
            parsed = Integer.parseInt(declared.trim());
        } catch (NumberFormatException e) {
            throw new BratException("The maxAttempts '" + declared + "' is not a whole number", e);
        }
        if (parsed <= 0) {
            throw new BratException("The maxAttempts '" + declared + "' must be a positive number of attempts");
        }
        return parsed;
    }

    private static long waitBetweenAttempts(String declared) {
        if (declared == null) {
            return 0;
        }
        long parsed;
        try {
            parsed = Long.parseLong(declared.trim());
        } catch (NumberFormatException e) {
            throw new BratException(
                    "The waitBetweenAttempts '" + declared + "' is not a whole number of milliseconds", e);
        }
        if (parsed < 0) {
            throw new BratException("The waitBetweenAttempts '" + declared + "' must not be negative");
        }
        return parsed;
    }
}
