package dev.pbroman.brat.core.data.result;

import java.util.List;

import dev.pbroman.brat.core.exception.BratException;

import static dev.pbroman.brat.core.util.Require.nonNull;

/**
 * The full record of one run: every request that executed, how long the run took, and whether it was
 * cancelled.
 * <p>
 * <strong>A run is not necessarily a suite.</strong> Running a single request is a run, and a runner
 * may execute a flat list of requests with no tree at all — which is why this is named for the run
 * rather than for the tree.
 * <p>
 * <strong>It holds results, not events.</strong> A run emits an event stream and this is not a
 * recording of it: a listener that wants every event registers for them. Keeping the two apart is
 * what stops this type growing without bound in a performance run, and a consumer that never observed
 * an event still finds everything here.
 *
 * @param requestResults every request that ran, in execution order; never {@code null}, possibly
 *        empty for a run whose every request was filtered out. Copied and unmodifiable, so a caller
 *        holding the list it passed cannot change a finished run
 * @param elapsedMs the whole run's wall-clock duration in milliseconds, measured by the runner rather
 *        than derived from event timestamps — event delivery is serialized, so a derived figure
 *        measures delivery instead of work
 * @param cancelled whether the run stopped because it was cancelled rather than because it finished.
 *        A cancelled run still carries the results of every request that completed before the stop
 */
public record RunResult(List<RequestResult> requestResults, long elapsedMs, boolean cancelled) {

    /**
     * Freezes the results, matching every other data type in the model.
     *
     * @throws BratException if {@code requestResults} is {@code null}
     */
    public RunResult {
        nonNull(requestResults, "The request results must not be null");
        requestResults = List.copyOf(requestResults);
    }

    /**
     * Whether this run failed — any request failed, or the run was cancelled.
     * <p>
     * Derived, never stored, for the same reason {@link RequestResult#failed()} is: a verdict that
     * can disagree with the results it summarises is worse than one computed on demand.
     * <p>
     * <strong>Cancellation counts as failure.</strong> A run stopped part-way did not establish that
     * the system under test is healthy, and reporting it green is how a cancelled CI job passes.
     *
     * @return whether any request failed or the run was cancelled; {@code false} for an empty,
     *         uncancelled run, which failed nothing
     */
    public boolean failed() {
        return cancelled || requestResults.stream().anyMatch(RequestResult::failed);
    }
}
