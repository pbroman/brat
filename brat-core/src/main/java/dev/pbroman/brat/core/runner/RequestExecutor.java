package dev.pbroman.brat.core.runner;

import java.util.Optional;
import java.util.function.Consumer;

import dev.pbroman.brat.core.api.handler.HttpRequestHandler;
import dev.pbroman.brat.core.api.listener.AttemptFinished;
import dev.pbroman.brat.core.data.HttpRequestDefinition;
import dev.pbroman.brat.core.data.result.RequestCoordinates;
import dev.pbroman.brat.core.data.result.RequestStatus;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.handler.HttpResponseVars;
import dev.pbroman.brat.core.util.FailureMessages;

/**
 * Performs a request's attempts and says how they ended.
 * <p>
 * <strong>Its whole output is a {@link RequestStatus}</strong>, which is what makes it separable: it
 * knows nothing of coordinates beyond reporting them on an event, nothing of elapsed time, and
 * nothing of response actions. Every way a request can end at this level already has a status —
 * {@link RequestStatus.Completed} when it worked, {@link RequestStatus.GaveUp} when a poll ran out of
 * attempts, {@link RequestStatus.Errored} when it could not be performed or its loop condition could
 * not be evaluated — so nothing has to be invented to carry the answer back.
 */
class RequestExecutor {

    private final HttpRequestHandler requestHandler;
    private final ConditionEvaluator conditionEvaluator;
    private final Consumer<AttemptFinished> attemptListener;

    /**
     * Constructs an executor over the collaborators it delegates to.
     *
     * @param requestHandler performs one attempt
     * @param conditionEvaluator interpolates and answers the loop condition
     * @param attemptListener receives one {@link AttemptFinished} per attempt of a polling request
     */
    RequestExecutor(
            HttpRequestHandler requestHandler,
            ConditionEvaluator conditionEvaluator,
            Consumer<AttemptFinished> attemptListener) {
        this.requestHandler = requestHandler;
        this.conditionEvaluator = conditionEvaluator;
        this.attemptListener = attemptListener;
    }

    /**
     * Performs {@code definition} once, or repeatedly until {@code bounds}' condition holds.
     * <p>
     * <strong>Without bounds this is one attempt</strong> and no {@link AttemptFinished} is emitted:
     * there is no loop to report progress within, and a synthetic attempt would have to invent a
     * {@code conditionMet} that nothing evaluated.
     * <p>
     * <strong>With bounds, an attempt that failed to reach the server is a retry.</strong> It counts
     * against {@code maxAttempts} like any other and does not evaluate the condition, because there is
     * no response to evaluate against — the archetypal poll waits for something to come up, so its
     * early attempts are expected to fail. What stopped the loop is what gets reported: the condition
     * holding is {@link RequestStatus.Completed}; running out with a response in hand is
     * {@link RequestStatus.GaveUp}; running out with the final attempt errored is
     * {@link RequestStatus.Errored}, because {@code messageOnFail} describes a condition that never
     * came true and would describe the wrong event over a connection failure.
     * <p>
     * <strong>The wait falls between attempts, not after the last one.</strong> A loop of {@code n}
     * attempts waits {@code n - 1} times: pausing after the final attempt only delays a give-up that
     * has already been decided.
     * <p>
     * <strong>A condition that cannot be evaluated ends the loop at once</strong> as
     * {@link RequestStatus.Errored}. It is an authoring error rather than a transient one, and
     * spending the whole budget with waits in between cannot make it start working.
     *
     * @param definition the interpolated request to perform; never {@code null}
     * @param bounds the loop's bounds, or {@link Optional#empty()} for a request that runs once
     * @param coordinates which request this is, for the attempt events
     * @param runtimeData the namespaces to resolve a loop condition against, and whose
     *        {@code responseVars} each completed attempt replaces
     * @return how the attempts ended; never {@code null}
     * @throws BratException if the thread is interrupted while waiting between attempts, which is
     *         cancellation of the run rather than a failure of this request
     */
    RequestStatus execute(
            HttpRequestDefinition definition,
            Optional<PollBounds> bounds,
            RequestCoordinates coordinates,
            RuntimeData runtimeData) {
        if (bounds.isEmpty()) {
            return attempt(definition, runtimeData, 1);
        }
        return poll(definition, bounds.get(), coordinates, runtimeData);
    }

    private RequestStatus poll(
            HttpRequestDefinition definition,
            PollBounds bounds,
            RequestCoordinates coordinates,
            RuntimeData runtimeData) {
        RequestStatus lastStatus = null;
        for (int attemptNo = 1; attemptNo <= bounds.maxAttempts(); attemptNo++) {
            lastStatus = attempt(definition, runtimeData, attemptNo);
            if (lastStatus instanceof RequestStatus.Completed) {
                boolean conditionMet;
                try {
                    conditionMet = conditionEvaluator
                            .evaluate(bounds.condition(), runtimeData)
                            .holds();
                } catch (Exception e) {
                    var errored = new RequestStatus.Errored(FailureMessages.causeOf(e, "A condition"));
                    attemptFinished(coordinates, attemptNo, bounds.maxAttempts(), errored, false);
                    return errored;
                }
                attemptFinished(coordinates, attemptNo, bounds.maxAttempts(), lastStatus, conditionMet);
                if (conditionMet) {
                    return lastStatus;
                }
            } else {
                attemptFinished(coordinates, attemptNo, bounds.maxAttempts(), lastStatus, false);
            }
            if (attemptNo < bounds.maxAttempts()) {
                sleep(bounds.waitBetweenAttempts());
            }
        }
        return exhausted(lastStatus, bounds.messageOnFail());
    }

    /**
     * One attempt: perform it, and publish its response so the loop condition and the response actions
     * can read it.
     */
    private RequestStatus attempt(HttpRequestDefinition definition, RuntimeData runtimeData, int attemptNo) {
        try {
            long start = System.currentTimeMillis();
            var response = requestHandler.performRequest(definition);
            long roundTripTimeMs = System.currentTimeMillis() - start;
            var responseVars = HttpResponseVars.of(response);
            runtimeData.setResponseVars(responseVars);
            return new RequestStatus.Completed(responseVars, attemptNo, roundTripTimeMs);
        } catch (Exception e) {
            return new RequestStatus.Errored(FailureMessages.causeOf(e, "A request"));
        }
    }

    /**
     * What an exhausted loop reports, which is decided by the attempt it ended on.
     */
    private static RequestStatus exhausted(RequestStatus lastStatus, String messageOnFail) {
        // Every permit is listed rather than defaulted: a fifth RequestStatus would then fail to
        // compile here instead of falling silently into a default arm. A null cannot reach this - a
        // positive maxAttempts means the loop always assigned one - so it is not handled either.
        return switch (lastStatus) {
            case RequestStatus.Errored _ -> lastStatus;
            case RequestStatus.Completed completed -> new RequestStatus.GaveUp(completed, messageOnFail);
            case RequestStatus.Skipped _, RequestStatus.GaveUp _ ->
                new RequestStatus.Errored("The last performed request has a disallowed status: "
                        + lastStatus.getClass().getSimpleName());
        };
    }

    private void attemptFinished(
            RequestCoordinates coordinates,
            int attemptNo,
            int maxAttempts,
            RequestStatus status,
            boolean conditionMet) {
        long roundTripTimeMs = status instanceof RequestStatus.Completed completed ? completed.roundTripTimeMs() : 0;
        var error = status instanceof RequestStatus.Errored errored ? errored.message() : null;
        attemptListener.accept(
                new AttemptFinished(coordinates, attemptNo, maxAttempts, roundTripTimeMs, conditionMet, error));
    }

    /**
     * Pauses between two attempts.
     * <p>
     * An interrupt is <strong>cancellation of the run, not a failure of this request</strong>, so the
     * flag is restored and the exception propagates rather than becoming a status. This is the seam
     * that matters when cancellation becomes interrupt-based inside a request; swallowing it here is
     * what would make such a cancellation do nothing.
     *
     * @throws BratException if the thread is interrupted while waiting
     */
    private static void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BratException("Interrupted while waiting between attempts", e);
        }
    }
}
