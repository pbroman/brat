package dev.pbroman.brat.core.api.listener;

/**
 * The channel for stopping a run in progress, handed to every {@link RunListener} and to whoever
 * launched the run.
 * <p>
 * <strong>Stopping is cooperative, not an interrupt.</strong> The runner checks before entering each
 * suite and before starting each request, so an in-flight request finishes rather than being killed
 * mid-call — a half-sent request is not a state anything downstream can reason about.
 * <p>
 * <strong>Two levels, and a stop can be escalated to a cancel:</strong>
 * <ul>
 *   <li><strong>{@link #stop()}</strong> — graceful. No further suite is entered and no further setup or
 *       main request starts; every suite still open runs its teardown requests, innermost first, and
 *       exits {@code Cancelled}. A suite in which no request had yet started has nothing to undo, and
 *       runs none.</li>
 *   <li><strong>{@link #cancel()}</strong> — immediate. Nothing further starts, teardown included; every
 *       suite still open exits {@code Cancelled}, innermost first. A cancel while a stop is unwinding cuts
 *       the teardown short at its next check.</li>
 * </ul>
 * Either way the run then delivers {@link RunEvent.RunFinished} with {@code cancelled} set.
 * <p>
 * Both levels latch: neither can be taken back, and calling either more than once is harmless. Neither
 * takes a reason — a caller wanting the stop explained logs it before calling.
 * <p>
 * <strong>An implementation must keep the two consistent</strong>: once {@link #cancel()} has been
 * called, {@link #isStopping()} answers {@code true} as well. {@link #create()} returns one that does,
 * and is safe to call from any thread.
 */
public interface RunControl {

    /**
     * Returns a new control, neither stopping nor cancelled, that is safe to use from any thread.
     *
     * @return a new control; never {@code null}
     */
    static RunControl create() {
        return new LatchingRunControl();
    }

    /**
     * Asks the run to stop gracefully: start no new work, and unwind the suites still open through their
     * teardown requests.
     * <p>
     * Returns immediately. Has no effect once {@link #cancel()} has been called.
     */
    void stop();

    /**
     * Asks the run to stop at once, before its next suite or request, teardown included.
     * <p>
     * Returns immediately; the run ends when it next reaches a check. Called after {@link #stop()}, it
     * cuts the unwinding short.
     */
    void cancel();

    /**
     * Whether the run has been asked to stop at either level.
     *
     * @return whether {@link #stop()} or {@link #cancel()} has been called
     */
    boolean isStopping();

    /**
     * Whether the run has been asked to stop at once.
     *
     * @return whether {@link #cancel()} has been called
     */
    boolean isCancelled();
}
