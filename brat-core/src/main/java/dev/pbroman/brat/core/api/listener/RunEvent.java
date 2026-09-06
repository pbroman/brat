package dev.pbroman.brat.core.api.listener;

import java.time.Instant;

import dev.pbroman.brat.core.data.result.RequestCoordinates;
import dev.pbroman.brat.core.data.result.RequestResult;
import dev.pbroman.brat.core.data.result.RunResult;

/**
 * Something that happened during a run, delivered to every {@link RunListener} as it happens.
 * <p>
 * Events are <strong>data, not callbacks</strong>: one sealed type and one method on the listener,
 * rather than a method per event. A listener switches on what it cares about and ignores the rest, so
 * adding an event does not break every implementation.
 * <p>
 * <strong>What is not here is deliberate.</strong> There is no counts-so-far, no progress percentage
 * and no suite totals — anything derivable from the stream is the consumer's to derive, and a second
 * source of truth for a count is a source of disagreement.
 */
// SuiteEntered/SuiteExited are deliberately not members yet: they mirror a tree walk that does not
// exist, and SuiteExited would need a SuiteStatus nothing has defined. They join when the walk that
// emits them does.
public sealed interface RunEvent
        permits RunEvent.RunStarted,
                RunEvent.RequestStarted,
                AttemptFinished,
                RunEvent.RequestFinished,
                RunEvent.RunFinished {

    /**
     * The run has begun, delivered before any other event.
     *
     * @param at when the run started
     */
    record RunStarted(Instant at) implements RunEvent {}

    /**
     * A request is about to execute.
     * <p>
     * It carries the identity alone because no result exists yet — this and {@link AttemptFinished}
     * are the consumers that justify {@link RequestCoordinates} being a type at all.
     *
     * @param coordinates which request is starting; never {@code null}
     */
    record RequestStarted(RequestCoordinates coordinates) implements RunEvent {}

    /**
     * A request has finished, in any terminal state.
     * <p>
     * It carries the result alone, which knows which request it belongs to — spelling the identity
     * beside a result that already holds it would be the same value twice, free to disagree.
     *
     * @param result what the request did; never {@code null}
     */
    record RequestFinished(RequestResult result) implements RunEvent {}

    /**
     * The run has ended, delivered <strong>exactly once</strong> — on success, on cancellation and on
     * a fatal error alike.
     * <p>
     * That guarantee is what makes a file-writing listener possible: without a terminal event
     * promised in every ending, a listener holding a handle has no point at which to flush and close.
     *
     * It carries the result alone, which already answers whether the run was cancelled — spelling
     * that beside a result holding it would be the same value twice, free to disagree.
     *
     * @param result the whole run's record; never {@code null}, even when the run was cancelled
     */
    record RunFinished(RunResult result) implements RunEvent {}
}
