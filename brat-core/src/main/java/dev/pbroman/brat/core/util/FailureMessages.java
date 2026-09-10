package dev.pbroman.brat.core.util;

import dev.pbroman.brat.core.exception.BratException;
import lombok.extern.slf4j.Slf4j;

/**
 * Turns a caught exception into the text a suite author reads on a result.
 * <p>
 * It exists because more than one site converts a failure into data — a failing assertion, a failing
 * capture, a request that could not be performed — and they must answer the same way. The distinction
 * they share is the point: a {@link BratException} was phrased <em>for</em> an author and its message
 * is used as written, while anything else is unplanned and must stay diagnosable rather than being
 * quietly reclassified as an authoring mistake.
 */
@Slf4j
public final class FailureMessages {

    private FailureMessages() {
        // utility class
    }

    /**
     * The cause as a suite author should read it: {@code "<what> failed, reason: <cause>"}.
     * <p>
     * The two kinds of exception differ in the <em>cause</em> half, not in the framing:
     * <ul>
     *   <li>a {@link BratException}'s message is the cause, used as written — it was phrased for an
     *       author, so nothing is added to it beyond the framing;</li>
     *   <li>anything else is a defect in core or in a plugin, so the cause names the exception's type
     *       before its message, and <strong>the exception is logged with its stack trace as a side
     *       effect</strong> — the only place that trace survives once the failure has become data.</li>
     * </ul>
     * Callers name {@code what} without repeating identity their result already carries: a capture
     * failure records the variable name on itself, so {@code "The capture"} reads better there than
     * naming the variable twice.
     *
     * @param e the exception a failure site caught; never {@code null}
     * @param what what failed, as it should read at the start of the message, e.g.
     *        {@code "The capture"} or {@code "Resolving the assertion 'x is 5'"}; never {@code null}
     * @return {@code what}, {@code " failed, reason: "} and the cause; never {@code null}, though the
     *         cause reads {@code null} where the exception carried no message
     * @throws BratException if {@code e} or {@code what} is {@code null}
     */
    public static String causeOf(Exception e, String what) {
        Require.nonNull(e, "The exception must not be null");
        Require.nonNull(what, "The 'what failed' must not be null");
        if (e instanceof BratException) {
            return buildMessage(what, e.getMessage());
        }
        log.error("{} failed with an unplanned {}", what, e.getClass().getSimpleName(), e);
        return buildMessage(what, e.getClass().getSimpleName() + ": " + e.getMessage());
    }

    private static String buildMessage(String what, String message) {
        return what + " failed, reason: " + message;
    }
}
