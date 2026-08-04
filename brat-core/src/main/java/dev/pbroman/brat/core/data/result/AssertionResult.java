package dev.pbroman.brat.core.data.result;

import dev.pbroman.brat.core.data.AssertionSeverity;
import dev.pbroman.brat.core.data.Condition;
import dev.pbroman.brat.core.exception.BratException;

import static dev.pbroman.brat.core.util.Require.nonNull;

/**
 * The outcome of resolving one {@link Condition}: whether it passed, the message to report if it did
 * not, and how much that failure matters.
 *
 * @param condition the resolved condition, interpolated where interpolation succeeded
 * @param message the message to report on failure, or {@code null}
 * @param passed whether the condition resolved to {@code true}
 * @param severity the severity declared on the assertion this came from; never {@code null} on a
 *        constructed result, defaulting to {@link AssertionSeverity#FAIL} when none was given
 */
public record AssertionResult(Condition condition, String message, boolean passed, AssertionSeverity severity) {

    /**
     * Validates the condition and defaults a {@code null} severity to {@link AssertionSeverity#FAIL}.
     *
     * @throws BratException if {@code condition} is {@code null}
     */
    public AssertionResult {
        nonNull(condition, "The condition cannot be null");
        if (severity == null) {
            severity = AssertionSeverity.FAIL;
        }
    }

    /**
     * Equivalent to {@link #AssertionResult(Condition, String, boolean, AssertionSeverity)} with the
     * severity defaulted to {@link AssertionSeverity#FAIL}.
     */
    public AssertionResult(Condition condition, String message, boolean passed) {
        this(condition, message, passed, AssertionSeverity.FAIL);
    }

    @Override
    public String toString() {
        var s = String.format("%s failed.", condition);
        if (message != null) {
            s += String.format(" Message: %s", message);
        }
        return s;
    }
}
