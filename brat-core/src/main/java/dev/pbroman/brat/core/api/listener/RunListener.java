package dev.pbroman.brat.core.api.listener;

/**
 * Receives a run's events as they happen.
 * <p>
 * One method, because {@link RunEvent} is a sealed type: an implementation switches on the events it
 * cares about and ignores the rest, and a new event never breaks an existing listener.
 * <p>
 * <strong>Delivery is synchronous and inside the run's wall time</strong> — a slow listener slows the
 * run — and <strong>serialized</strong>: core calls one listener at a time even once execution runs in
 * parallel, rather than handing third-party code concurrent events. An implementation therefore needs
 * no synchronization of its own.
 * <p>
 * <strong>{@code RunStarted} may never arrive; if it does, {@code RunFinished} will.</strong> A run
 * that fails at launch delivers no event at all. So a listener that needs a resource acquires it on
 * {@code RunStarted} and releases it on {@code RunFinished}, and is never left holding one.
 */
public interface RunListener {

    /**
     * Handles one event.
     * <p>
     * <strong>Throwing does not fail the run.</strong> An exception here is caught and logged at WARN
     * naming the listener and the event, and the run continues — a reporting bug must never turn a
     * green suite red. The cost is that a silently broken listener stays silent apart from the log.
     *
     * @param event what happened; never {@code null}
     */
    void on(RunEvent event);
}
