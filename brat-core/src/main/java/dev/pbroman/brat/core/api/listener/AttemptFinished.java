package dev.pbroman.brat.core.api.listener;

import dev.pbroman.brat.core.data.result.RequestCoordinates;

/**
 * One attempt of a polling request has finished, reported as it happens rather than at the end.
 * <p>
 * It exists so a poll is visible while it runs: a request waiting five times for a job to reach
 * {@code COMPLETE} otherwise shows nothing for half a minute, and thirty identical connection
 * failures otherwise look the same as silence. It carries the request's identity rather than its
 * result, because no result exists yet.
 * <p>
 * <strong>Only a polling request emits these.</strong> A request with no {@code repeatUntil} runs
 * once and emits none: there is no loop to report progress within, and a synthetic attempt would have
 * to invent a {@code conditionMet} that nothing evaluated. What a request did in total is on its
 * result.
 * <p>
 * A member of {@link RunEvent} declared on its own rather than nested inside it, because it predates
 * the interface and is referenced by name in the narrowed {@code Consumer<AttemptFinished>} the
 * request processor receives — narrowed so no processor can forge a run-level event.
 *
 * @param coordinates which request this attempt belongs to; never {@code null}
 * @param attempt which attempt this was, counting from {@code 1}
 * @param maxAttempts the ceiling this attempt counted against, after interpolation and defaulting
 * @param roundTripTimeMs this attempt's protocol call in milliseconds; {@code 0} where the attempt
 *        errored before a response arrived
 * @param conditionMet whether the {@code repeatUntil} condition held after this attempt, and so
 *        whether the loop stops here. Always {@code false} for an errored attempt, which cannot
 *        evaluate the condition because there is no response to evaluate it against
 * @param error why this attempt failed to reach the server, or {@code null} where it got a response.
 *        A non-{@code null} value does not end the loop: an errored attempt is a retry that counts
 *        against {@code maxAttempts} like any other
 */
public record AttemptFinished(
        RequestCoordinates coordinates,
        int attempt,
        int maxAttempts,
        long roundTripTimeMs,
        boolean conditionMet,
        String error)
        implements RunEvent {}
