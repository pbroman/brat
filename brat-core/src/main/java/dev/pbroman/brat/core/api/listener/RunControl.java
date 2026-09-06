package dev.pbroman.brat.core.api.listener;

/**
 * The channel for stopping a run in progress, handed to every {@link RunListener} and to whoever
 * launched the run.
 * <p>
 * <strong>Cancellation is cooperative, not an interrupt.</strong> The runner checks between requests,
 * so an in-flight request finishes rather than being killed mid-call — a half-sent request is not a
 * state anything downstream can reason about. The run then unwinds and delivers
 * {@link RunEvent.RunFinished} with {@code cancelled} set.
 * <p>
 * One mechanism, three users: an IDE's stop button, a {@code --bail} that stops on first failure, and
 * a timeout guard if one is ever wanted.
 */
public interface RunControl {

    /**
     * Asks the run to stop after the current request.
     * <p>
     * Returns immediately; the run ends when it next reaches a check. Calling this more than once is
     * harmless — cancellation is a latch, not a counter.
     * <p>
     * It takes no reason: nothing carries one. A caller wanting the stop explained logs it before
     * calling, which is the same place it would have had to build the message anyway.
     */
    void cancel();

    /**
     * Whether cancellation has been asked for.
     *
     * @return whether {@link #cancel()} has been called
     */
    boolean isCancelled();
}
