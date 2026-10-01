package dev.pbroman.brat.core.api.listener;

import java.time.Instant;

import dev.pbroman.brat.core.data.result.RequestCoordinates;
import dev.pbroman.brat.core.data.result.RequestResult;
import dev.pbroman.brat.core.data.result.RunResult;
import dev.pbroman.brat.core.data.result.SuiteStatus;

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
public sealed interface RunEvent
        permits RunEvent.RunStarted,
                RunEvent.SuiteEntered,
                RunEvent.RequestStarted,
                AttemptFinished,
                RunEvent.RequestFinished,
                RunEvent.SuiteExited,
                RunEvent.RunFinished {

    /**
     * The run has begun, delivered before any other event.
     *
     * @param at when the run started
     */
    record RunStarted(Instant at) implements RunEvent {}

    /**
     * A suite is being entered.
     * <p>
     * Identified by its path and name alone: a suite has no {@code id}, so it has no
     * {@link RequestCoordinates}. Every suite that is entered is exited, with a {@link SuiteExited}
     * carrying the same path, and every event for something beneath it falls between the two.
     *
     * @param path the suite's address, built from the names from the root down and joined with
     *        {@code /} — the prefix of every path beneath it; never {@code null}
     * @param name the suite's own name, the last segment of the path; never {@code null}
     */
    record SuiteEntered(String path, String name) implements RunEvent {}

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
     * A suite has been left, in any terminal state.
     * <p>
     * Emitted for a skipped suite too, directly after its {@link SuiteEntered}, so a suite that ran
     * nothing is still a node a listener can show — the status is what says why it ran nothing.
     *
     * @param path the same path its {@link SuiteEntered} carried; never {@code null}
     * @param status why the walk of this suite ended; never {@code null}
     * @param elapsedMs wall clock from entering the suite to leaving it, in milliseconds, including
     *        everything beneath it
     */
    record SuiteExited(String path, SuiteStatus status, long elapsedMs) implements RunEvent {}

    /**
     * The run has ended, delivered <strong>exactly once</strong> after {@link RunStarted} — on success,
     * on cancellation and on a fatal error alike.
     * <p>
     * That guarantee is what makes a file-writing listener possible: without a terminal event
     * promised in every ending, a listener holding a handle has no point at which to flush and close.
     * A run that fails at launch delivers neither event, so a listener that acquires on
     * {@code RunStarted} holds nothing to release.
     * <p>
     * It carries the result alone, which already answers whether the run was cancelled — spelling
     * that beside a result holding it would be the same value twice, free to disagree.
     *
     * @param result the whole run's record; never {@code null}, even when the run was cancelled or ended
     *        on a structural failure — then {@link RunResult#error()} names it
     */
    record RunFinished(RunResult result) implements RunEvent {}
}
