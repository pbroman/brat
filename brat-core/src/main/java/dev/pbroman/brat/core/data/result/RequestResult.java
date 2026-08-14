package dev.pbroman.brat.core.data.result;

import dev.pbroman.brat.core.api.data.RequestDefinition;
import dev.pbroman.brat.core.data.AssertionSeverity;

/**
 * The full record of one request execution: where it sat, what was sent, how it ended, what it cost,
 * and what its response actions produced.
 *
 * @param coordinates which request this was, and where in the run
 * @param requestDefinition what was sent — the interpolated copy where interpolation succeeded, and
 *        the authored one where interpolation is what failed. Ask
 *        {@link dev.pbroman.brat.core.data.ConfigData#isInterpolated()} which one arrived; the type
 *        answers the question itself rather than carrying a second field for it
 * @param status how the protocol call ended. <strong>Not the verdict</strong> — see {@link #failed()}
 * @param elapsedMs wall clock from the request starting to it finishing, in milliseconds: every
 *        attempt, the waits between them, interpolation and assertion evaluation. Excludes
 *        {@code waitAfter}, which is the suite's pacing rather than this request's cost. What a test
 *        report shows, and for a polled request it is dominated by the waits between attempts rather
 *        than by any single round trip
 * @param responseActionsResult what the assertions and captures produced; never {@code null}, and
 *        {@link ResponseActionsResult#NONE} for a request whose response actions never ran
 */
public record RequestResult(
        RequestCoordinates coordinates,
        RequestDefinition requestDefinition,
        RequestStatus status,
        long elapsedMs,
        ResponseActionsResult responseActionsResult) {

    /**
     * Defaults {@code responseActionsResult} to {@link ResponseActionsResult#NONE}.
     */
    public RequestResult {
        responseActionsResult = responseActionsResult == null ? ResponseActionsResult.NONE : responseActionsResult;
    }

    /**
     * Whether this request failed — the verdict, which no single field carries.
     * <p>
     * Derived rather than stored because three unrelated things fail a request and the status is only
     * one of them. It is {@code true} when any of these holds:
     * <ul>
     *   <li>the status is {@link RequestStatus.Errored} — it could not be performed at all;</li>
     *   <li>any assertion result did not pass <em>and</em> carries
     *       {@link AssertionSeverity#FAIL} — a failed {@code WARN} assertion is recorded and does not
     *       fail the request;</li>
     *   <li>any capture failed — a capture has no severity, so any failure counts.</li>
     * </ul>
     * A {@link RequestStatus.Skipped} request has not failed: it did not run.
     *
     * @return whether this request counts as failed
     */
    public boolean failed() {
        boolean assertionFailed = responseActionsResult().assertionResults().stream()
                .anyMatch(result -> !result.passed() && AssertionSeverity.FAIL.equals(result.severity()));
        return status instanceof RequestStatus.Errored
                || !responseActionsResult().captureFailures().isEmpty()
                || assertionFailed;
    }
}
