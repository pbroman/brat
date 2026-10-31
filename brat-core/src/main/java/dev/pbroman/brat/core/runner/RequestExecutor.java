package dev.pbroman.brat.core.runner;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import dev.pbroman.brat.core.api.data.RequestDefinition;
import dev.pbroman.brat.core.api.handler.RequestHandler;
import dev.pbroman.brat.core.api.listener.AttemptFinished;
import dev.pbroman.brat.core.data.Phase;
import dev.pbroman.brat.core.data.RequestOptions;
import dev.pbroman.brat.core.data.result.RequestCoordinates;
import dev.pbroman.brat.core.data.result.RequestStatus;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.util.FailureMessages;

/**
 * Performs a request's attempts and says how they ended.
 * <p>
 * <strong>Its whole output is a {@link RequestStatus} and the definition last sent</strong>, which is
 * what makes it separable: it knows nothing of coordinates beyond reporting them on an event, nothing
 * of elapsed time, and nothing of response actions. Every way a request can end at this level already has a status —
 * {@link RequestStatus.Completed} when it worked, {@link RequestStatus.GaveUp} when a poll ran out of
 * attempts, {@link RequestStatus.Errored} when it could not be performed or its loop condition could
 * not be evaluated — so nothing has to be invented to carry the answer back.
 */
class RequestExecutor {

    private static final String STOPPED_WHILE_WAITING = "The run was stopped while waiting for the condition";

    private final ConditionEvaluator conditionEvaluator;
    private final Consumer<AttemptFinished> attemptListener;
    private final Predicate<Phase> stopRequested;

    /**
     * Constructs an executor over the collaborators it delegates to.
     * <p>
     * The handler is not among them: which one performs a request is decided per request by whatever
     * walks the suite, so it arrives as an argument rather than as wiring.
     *
     * @param conditionEvaluator interpolates and answers the loop condition
     * @param attemptListener receives one {@link AttemptFinished} per attempt of a polling request
     * @param stopRequested whether the run wants the work of a phase to stop now, asked with the
     *        polling request's own phase while it waits between attempts
     */
    RequestExecutor(
            ConditionEvaluator conditionEvaluator,
            Consumer<AttemptFinished> attemptListener,
            Predicate<Phase> stopRequested) {
        this.conditionEvaluator = conditionEvaluator;
        this.attemptListener = attemptListener;
        this.stopRequested = stopRequested;
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
     * <strong>A stop ends the loop between attempts</strong>, as {@link RequestStatus.Errored} with the
     * message {@code "The run was stopped while waiting for the condition"}. The executor asks the
     * {@code stopRequested} it was constructed with, passing {@code coordinates}' phase, before each wait and
     * within every {@value PauseUtils#SLICE_MILLIS} ms of it, so a stop ends a long wait promptly; with a
     * wait of {@code 0} it still asks once between two attempts. It never asks during an attempt — an
     * attempt under way finishes, and if its condition holds the request is
     * {@link RequestStatus.Completed} — nor after the last attempt, where nothing is left to stop and the loop
     * ends as it would have. A request without bounds never asks: whether it starts is the walk's
     * decision. The result carries what the last attempt sent.
     * <p>
     * <strong>A condition that cannot be evaluated ends the loop at once</strong> as
     * {@link RequestStatus.Errored}. It is an authoring error rather than a transient one, and
     * spending the whole budget with waits in between cannot make it start working.
     * <p>
     * <strong>Each attempt sends what {@code prepare} makes of {@code definition}</strong>, called
     * afresh per attempt and always on {@code definition} itself, never on what an earlier attempt
     * sent. <strong>{@code prepare} throwing ends the request at once</strong> as
     * {@link RequestStatus.Errored}, before anything is sent, with or without bounds: like a condition
     * that cannot be evaluated, what it refuses fails the same way on every attempt, so a retry would
     * only spend the budget. Within bounds that attempt is still reported, as errored.
     *
     * @param definition the interpolated request to perform; must not be {@code null}
     * @param prepare turns {@code definition} into what one attempt sends, such as adding its
     *        credentials; {@link UnaryOperator#identity()} when there is nothing to add. Must not be
     *        {@code null}
     * @param options the interpolated options it is performed with, handed to the handler on every
     *        attempt; never {@code null}
     * @param handler the handler that performs it, already selected for this request; never
     *        {@code null}
     * @param <T> the definition's own type, which the handler executes
     * @param <R> the handler's response type, which only it and its {@code responseVars} ever see
     * @param bounds the loop's bounds, or {@link Optional#empty()} for a request that runs once
     * @param coordinates which request this is, for the attempt events
     * @param runtimeData the namespaces to resolve a loop condition against, and whose
     *        {@code responseVars} each completed attempt replaces
     * @return how the attempts ended, and what the last attempt sent — {@code definition} itself where
     *         {@code prepare} failed on that attempt; never {@code null}
     * @throws BratException if the thread is interrupted while waiting between attempts, which ends the
     *         run rather than failing this request
     */
    <T extends RequestDefinition, R> Executed<T> execute(
            T definition,
            UnaryOperator<T> prepare,
            RequestOptions options,
            RequestHandler<T, R> handler,
            Optional<PollBounds> bounds,
            RequestCoordinates coordinates,
            RuntimeData runtimeData) {
        if (bounds.isEmpty()) {
            T sent;
            try {
                sent = prepare.apply(definition);
            } catch (Exception e) {
                return unprepared(definition, e);
            }
            return attempt(sent, options, handler, runtimeData, 1);
        }
        return poll(definition, prepare, options, handler, bounds.get(), coordinates, runtimeData);
    }

    private <T extends RequestDefinition, R> Executed<T> poll(
            T definition,
            UnaryOperator<T> prepare,
            RequestOptions options,
            RequestHandler<T, R> handler,
            PollBounds bounds,
            RequestCoordinates coordinates,
            RuntimeData runtimeData) {
        Executed<T> last = null;
        for (int attemptNo = 1; attemptNo <= bounds.maxAttempts(); attemptNo++) {
            T sent;
            try {
                sent = prepare.apply(definition);
            } catch (Exception e) {
                var unprepared = unprepared(definition, e);
                attemptFinished(coordinates, attemptNo, bounds.maxAttempts(), unprepared.status(), false);
                return unprepared;
            }
            last = attempt(sent, options, handler, runtimeData, attemptNo);
            var lastStatus = last.status();
            if (lastStatus instanceof RequestStatus.Completed) {
                boolean conditionMet;
                try {
                    conditionMet = conditionEvaluator
                            .evaluate(bounds.condition(), runtimeData)
                            .holds();
                } catch (Exception e) {
                    var errored = new RequestStatus.Errored(FailureMessages.causeOf(e, "A condition"));
                    attemptFinished(coordinates, attemptNo, bounds.maxAttempts(), errored, false);
                    return new Executed<>(errored, last.sent());
                }
                attemptFinished(coordinates, attemptNo, bounds.maxAttempts(), lastStatus, conditionMet);
                if (conditionMet) {
                    return last;
                }
            } else {
                attemptFinished(coordinates, attemptNo, bounds.maxAttempts(), lastStatus, false);
            }
            if (attemptNo < bounds.maxAttempts() && !waitBetweenAttempts(bounds, coordinates.phase())) {
                return new Executed<>(new RequestStatus.Errored(STOPPED_WHILE_WAITING), last.sent());
            }
        }
        return new Executed<>(exhausted(last.status(), bounds.messageOnFail()), last.sent());
    }

    /**
     * One attempt: perform {@code sent}, and publish its response so the loop condition and the
     * response actions can read it.
     */
    private <T extends RequestDefinition, R> Executed<T> attempt(
            T sent, RequestOptions options, RequestHandler<T, R> handler, RuntimeData runtimeData, int attemptNo) {
        try {
            long start = System.currentTimeMillis();
            var response = handler.performRequest(sent, options);
            long roundTripTimeMs = System.currentTimeMillis() - start;
            var responseVars = handler.responseVars(response);
            runtimeData.setResponseVars(responseVars);
            return new Executed<>(new RequestStatus.Completed(responseVars, attemptNo, roundTripTimeMs), sent);
        } catch (Exception e) {
            return new Executed<>(new RequestStatus.Errored(FailureMessages.causeOf(e, "A request")), sent);
        }
    }

    /**
     * What a request reports when preparing it failed: errored, carrying {@code definition} as given,
     * since nothing else was made.
     */
    private static <T extends RequestDefinition> Executed<T> unprepared(T definition, Exception e) {
        return new Executed<>(
                new RequestStatus.Errored(FailureMessages.causeOf(e, "Preparing the request")), definition);
    }

    /**
     * What an exhausted loop reports, which is decided by the attempt it ended on.
     */
    private static RequestStatus exhausted(RequestStatus lastStatus, String messageOnFail) {
        // Every permit is listed rather than defaulted: a fifth RequestStatus would then fail to
        // compile here instead of falling silently into a default arm. A null cannot reach this - a
        // positive maxAttempts means the loop always assigned one - so it is not handled either.
        return switch (lastStatus) {
            case RequestStatus.Errored errored -> lastStatus;
            case RequestStatus.Completed completed -> new RequestStatus.GaveUp(completed, messageOnFail);
            case RequestStatus.Skipped skipped -> disallowed(lastStatus);
            case RequestStatus.GaveUp gaveUp -> disallowed(lastStatus);
        };
    }

    /**
     * The status an exhausted loop reports when its last attempt ended in a status no attempt may end
     * in.
     *
     * @param lastStatus the status the last attempt ended in
     * @return an error naming that status
     */
    private static RequestStatus disallowed(RequestStatus lastStatus) {
        return new RequestStatus.Errored("The last performed request has a disallowed status: "
                + lastStatus.getClass().getSimpleName());
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
     * Waits between two attempts, ending early when the run wants work of {@code phase} to stop.
     * <p>
     * An interrupt <strong>ends the run rather than failing this request</strong>, so the flag is
     * restored and the exception propagates rather than becoming a status; swallowing it here is what
     * would make an interrupt do nothing.
     *
     * @param bounds the loop's bounds, holding the wait
     * @param phase the polling request's phase
     * @return {@code true} if the whole wait passed; {@code false} if a stop ended it or came first
     * @throws BratException if the thread is interrupted while waiting
     */
    private boolean waitBetweenAttempts(PollBounds bounds, Phase phase) {
        return PauseUtils.pause(
                bounds.waitBetweenAttempts(),
                () -> stopRequested.test(phase),
                "Interrupted while waiting between attempts");
    }

    /**
     * How a request's attempts ended, and what the last of them sent.
     *
     * @param status how the attempts ended; never {@code null}
     * @param sent the definition the last attempt sent, or the definition as given where preparing it
     *        is what failed; never {@code null}
     * @param <T> the definition's type
     */
    record Executed<T extends RequestDefinition>(RequestStatus status, T sent) {}
}
