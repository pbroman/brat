package dev.pbroman.brat.core.runner;

import java.util.function.Consumer;

import dev.pbroman.brat.core.api.data.RequestDefinition;
import dev.pbroman.brat.core.api.handler.HttpRequestHandler;
import dev.pbroman.brat.core.api.handler.ResponseHandler;
import dev.pbroman.brat.core.api.interpolation.ConfigDataInterpolator;
import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.api.listener.AttemptFinished;
import dev.pbroman.brat.core.api.resolver.ConditionResolver;
import dev.pbroman.brat.core.data.Condition;
import dev.pbroman.brat.core.data.FlowControl;
import dev.pbroman.brat.core.data.HttpRequestDefinition;
import dev.pbroman.brat.core.data.RepeatUntil;
import dev.pbroman.brat.core.data.Request;
import dev.pbroman.brat.core.data.ResponseActions;
import dev.pbroman.brat.core.data.result.RequestCoordinates;
import dev.pbroman.brat.core.data.result.RequestResult;
import dev.pbroman.brat.core.data.result.RequestStatus;
import dev.pbroman.brat.core.data.result.ResponseActionsResult;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.handler.HttpResponseVars;
import dev.pbroman.brat.core.util.FailureMessages;
import dev.pbroman.brat.core.util.Require;
import lombok.extern.slf4j.Slf4j;

import static dev.pbroman.brat.core.util.Constants.DEFAULT_MAX_ATTEMPTS;

/**
 * Runs one {@link Request} and returns what happened: interpolate it, decide whether to skip it,
 * perform it, hand the response to the response actions, and record the lot as a
 * {@link RequestResult}.
 * <p>
 * <strong>Nothing escapes a request.</strong> Every failure between accepting a {@code Request} and
 * returning its result becomes part of that result — a definition that cannot be interpolated and a
 * call that cannot be made are both {@link RequestStatus.Errored}, and a failing assertion or capture
 * is data on the {@link dev.pbroman.brat.core.data.result.ResponseActionsResult}. A
 * {@link BratException} leaves this class only for a <em>structural</em> failure that is not about
 * this request at all: a {@code null} argument, or a missing collaborator.
 * <p>
 * ⚠ <strong>Every request is treated as HTTP, and that is temporary.</strong>
 * {@link Request#requestDefinition()} is typed to the interface, but no protocol selection exists
 * yet: the definition is cast to {@link HttpRequestDefinition} and handed to the one
 * {@link HttpRequestHandler} this was constructed with, and a definition of any other type is a
 * structural error. What replaces it is a lookup — {@link RequestDefinition} carrying the protocol it
 * is, and {@link Request#requestHandlers()} naming which handler serves that protocol — so this field
 * becomes a registry and the cast goes. Note the selection is two-part by design: several handlers
 * for one protocol can be live at once, because a proxied or client-certificate suite is a
 * differently configured handler rather than a different implementation.
 */
@Slf4j
public class RequestProcessor {

    private final Interpolation interpolation;
    private final ConfigDataInterpolator<HttpRequestDefinition> requestDefinitionInterpolator;
    private final ConfigDataInterpolator<Condition> conditionInterpolator;
    private final ConditionResolver conditionResolver;
    private final HttpRequestHandler requestHandler;
    private final ResponseHandler responseHandler;
    private final ConfigDataInterpolator<FlowControl> flowControlInterpolator;
    private final Consumer<AttemptFinished> attemptListener;

    /**
     * Constructs a processor over the collaborators it delegates to.
     *
     * @param interpolation resolves the tokens in a definition, a skip condition and a capture
     * @param requestDefinitionInterpolator produces the interpolated copy of the request definition
     * @param conditionInterpolator produces the interpolated copy of a skip condition
     * @param conditionResolver answers a skip condition once it is interpolated
     * @param requestHandler performs the request
     * @param responseHandler runs the response actions against the response
     * @param flowControlInterpolator produces the interpolated copy of the flow control, whose
     *        {@code maxAttempts} and {@code waitBetweenAttempts} may themselves be tokens
     * @param attemptListener receives one {@link AttemptFinished} per attempt of a polling
     *        request. Deliberately a {@link Consumer} of that one event rather than the run's
     *        event emitter, so that no processor can emit a run-level event it has no standing to
     *        report
     */
    public RequestProcessor(
            Interpolation interpolation,
            ConfigDataInterpolator<HttpRequestDefinition> requestDefinitionInterpolator,
            ConfigDataInterpolator<Condition> conditionInterpolator,
            ConditionResolver conditionResolver,
            HttpRequestHandler requestHandler,
            ResponseHandler responseHandler,
            ConfigDataInterpolator<FlowControl> flowControlInterpolator,
            Consumer<AttemptFinished> attemptListener) {
        this.interpolation = interpolation;
        this.requestDefinitionInterpolator = requestDefinitionInterpolator;
        this.conditionInterpolator = conditionInterpolator;
        this.conditionResolver = conditionResolver;
        this.requestHandler = requestHandler;
        this.responseHandler = responseHandler;
        this.flowControlInterpolator = flowControlInterpolator;
        this.attemptListener = attemptListener;
    }

    /**
     * Runs {@code request} and returns its result.
     *
     * <p>
     * <strong>What happens, in order:</strong>
     * <ol>
     *   <li><strong>The skip condition</strong>, when the request declares one, is interpolated and
     *       resolved, before the definition is — there is no point interpolating a request that is
     *       not going to run. Holding means {@link RequestStatus.Skipped} and nothing else runs — no
     *       call, no response actions. A skipped request has <em>not</em> failed.
     *       <p>
     *       <strong>A skip condition that cannot be evaluated</strong> — it fails to interpolate, or
     *       names a func nothing resolves — is {@link RequestStatus.Errored}, and the message says
     *       the skip condition is what failed, so a reader is not sent looking at the URL. The
     *       request carries its <em>authored</em> definition, since the definition was never reached.
     *       <p>
     *       This is the one condition whose failure modes are not symmetric, which is why it errors
     *       rather than defaulting either way. A skip condition is a <strong>guard</strong>: running
     *       the request anyway would perform something the author wrote the guard to prevent, and
     *       recording it as {@link RequestStatus.Skipped} would be green, so nobody would look. Both
     *       hide an authoring error that erroring reports.</li>
     *   <li><strong>The definition is interpolated.</strong> Failing here is
     *       {@link RequestStatus.Errored}, and the result carries the <em>authored</em> definition,
     *       since no interpolated copy exists — {@code ConfigData.isInterpolated()} is how a reader
     *       tells which one arrived.</li>
     *   <li><strong>The flow control is interpolated</strong>, when the request declares one, since
     *       {@code maxAttempts} and {@code waitBetweenAttempts} may be tokens. Failing here is
     *       {@link RequestStatus.Errored}: the loop's bounds are not knowable, and guessing them is
     *       how a suite spins.</li>
     *   <li><strong>The request is performed, once or repeatedly.</strong> With no
     *       {@code repeatUntil} it runs exactly once. With one, it runs until the condition holds or
     *       the attempts run out — see below. Failing to reach the server is
     *       {@link RequestStatus.Errored} for a request that does not poll. Note what is <em>not</em>
     *       a failure: every status code the server answered with, including 4xx and 5xx, is a
     *       response.</li>
     *   <li><strong>The response is flattened</strong> into the {@code responseVars} namespace, and
     *       <strong>removed again when this method returns</strong>, on every path. {@code ${response.*}}
     *       therefore means "the response this request just received" and nothing else: no later
     *       request can read it, because by then it is gone. A value needed afterwards is captured
     *       with {@code setVars}, which is what that exists for.</li>
     *   <li><strong>The response actions run</strong>, when the request declares any, and whatever
     *       they produce is carried on the result.</li>
     * </ol>
     *
     * <p>
     * <strong>How the loop ends, and what it reports.</strong> Each attempt performs the request; an
     * attempt that got a response evaluates {@code repeatUntil.condition} against it. An attempt that
     * failed to reach the server <strong>counts against {@code maxAttempts} and is retried</strong> —
     * the archetypal poll waits for something to come up, so its early attempts are expected to fail
     * — and it does not evaluate the condition, because there is no response to evaluate against.
     * <strong>What stopped the loop is what gets reported:</strong>
     * <ul>
     *   <li><strong>the condition held</strong> on any attempt → {@link RequestStatus.Completed},
     *       however many earlier attempts errored;</li>
     *   <li><strong>attempts exhausted, last one got a response</strong> → {@link RequestStatus.GaveUp},
     *       carrying that attempt and the author's {@code messageOnFail};</li>
     *   <li><strong>attempts exhausted, last one errored</strong> → {@link RequestStatus.Errored}
     *       naming the attempt count and the underlying failure. Not {@code GaveUp}:
     *       {@code messageOnFail} describes a condition that never came true and would describe the
     *       wrong event over a connection failure.</li>
     * </ul>
     * <strong>A condition that cannot be evaluated is not retried.</strong> A {@code repeatUntil}
     * condition that fails to interpolate or names a func nothing resolves is
     * {@link RequestStatus.Errored} at once — it is an authoring error, not a transient one, and
     * spending the whole attempt budget with waits in between cannot make it start working. This is
     * the same treatment the skip condition gets, for the same reason.
     * <p>
     * {@code maxAttempts} defaults to {@link dev.pbroman.brat.core.util.Constants#DEFAULT_MAX_ATTEMPTS}
     * so that a loop bails whether or not the author thought about bailing;
     * {@code waitBetweenAttempts} defaults to no wait, since a wait the author did not ask for is not
     * this class's to invent. Either being unparseable is {@link RequestStatus.Errored}.
     * <p>
     * <strong>Response actions run on {@code Completed} and on {@code GaveUp}</strong>, because in
     * both cases a response exists and the author asked for those checks — "it gave up <em>and</em>
     * the last status was 500" says more than the give-up alone. They do not run on
     * {@code Errored} or {@code Skipped}, where there is no response to run them against.
     * <p>
     * <strong>What the result's numbers mean.</strong>
     * {@code elapsedMs} covers this whole method: interpolation, every attempt, every wait between
     * them, and the response actions. {@code roundTripTimeMs} covers <em>one</em> protocol call — the
     * final attempt's — which is why an aggregate over response times reads that one and not
     * {@code elapsedMs}: a five-attempt poll has a round trip of milliseconds and an elapsed of
     * seconds. {@code numAttempts} is how many attempts were spent, {@code 1} for a request that does
     * not poll.
     * <p>
     * <strong>What it mutates.</strong>
     * {@code runtimeData} only. Its {@code currentPath} and {@code currentRequestNo} are set from
     * {@code coordinates} first, which is what lets code called further down record where it was
     * without the identity being threaded through every signature — a capture tombstone is the one
     * that needs it. {@code responseVars} is filled when a response arrives and emptied in a
     * {@code finally}, so the namespace cannot outlive the request whatever ends it. {@code vars}
     * gains every successful capture and a tombstone for every failed one, and those <em>do</em>
     * survive: they are the mechanism for keeping something.
     *
     * @param request the request to run; never {@code null}, and its {@code requestDefinition} must
     *        be an {@link HttpRequestDefinition}
     * @param coordinates where this request sits, which the caller computes — the path is built from
     *        the names from the root down, and only the walk knows the ancestors. Passed rather than
     *        read off {@code runtimeData} so that it cannot be forgotten
     * @param runtimeData the namespaces to resolve against; its {@code currentPath} and
     *        {@code currentRequestNo} are <strong>set</strong> from {@code coordinates} before
     *        anything else runs
     * @return what happened; never {@code null}. Ask {@link RequestResult#failed()} for the verdict,
     *         which the status alone does not give
     * @throws BratException if {@code request}, {@code coordinates} or {@code runtimeData} is
     *         {@code null}, if the request declares no {@code requestDefinition}, or if it is not an
     *         {@link HttpRequestDefinition}. These are structural — a suite that could not be run
     *         rather than a request that failed. A failure belonging to <em>this</em> request never
     *         throws: it is {@link RequestStatus.Errored} on the returned result
     */
    public RequestResult process(Request request, RequestCoordinates coordinates, RuntimeData runtimeData) {
        Require.nonNull(request, "The request must not be null");
        Require.nonNull(coordinates, "The coordinates must not be null");
        Require.nonNull(runtimeData, "The runtimeData must not be null");
        Require.nonNull(request.requestDefinition(), "The request definition must not be null");
        if (!(request.requestDefinition() instanceof HttpRequestDefinition requestDef)) {
            throw new BratException("The request definition is not a HttpRequestDefinition");
        }

        long methodStart = System.currentTimeMillis();
        runtimeData.setCurrentPath(coordinates.path());
        runtimeData.setCurrentRequestNo(coordinates.requestNo());

        if (request.skipCondition() != null) {
            try {
                var condition = conditionInterpolator.interpolated(request.skipCondition(), interpolation, runtimeData);
                if (conditionResolver.resolve(condition)) {
                    return new RequestResult(
                            coordinates,
                            requestDef,
                            new RequestStatus.Skipped("Skipped due to condition " + condition),
                            elapsedMs(methodStart),
                            null);
                }
            } catch (Exception e) {
                var message = FailureMessages.causeOf(e, "A condition");
                return errored(coordinates, requestDef, "The skip condition failed: " + message, methodStart);
            }
        }

        HttpRequestDefinition interpolatedRequestDef;
        try {
            interpolatedRequestDef = requestDefinitionInterpolator.interpolated(requestDef, interpolation, runtimeData);
        } catch (Exception e) {
            var message = FailureMessages.causeOf(e, "Request definition interpolation");
            return errored(coordinates, requestDef, message, methodStart);
        }

        RepeatUntil repeatUntil = null;
        if (request.flowControl() != null) {
            try {
                repeatUntil = flowControlInterpolator
                        .interpolated(request.flowControl(), interpolation, runtimeData)
                        .getRepeatUntil();
            } catch (Exception e) {
                var message = FailureMessages.causeOf(e, "Flow control interpolation");
                return errored(coordinates, interpolatedRequestDef, message, methodStart);
            }
        }

        try {
            if (repeatUntil == null) {
                return performRequestAndResponseActions(
                        interpolatedRequestDef, runtimeData, request.responseActions(), coordinates, methodStart);
            }

            int attempt = 1;
            int maxAttempts = DEFAULT_MAX_ATTEMPTS;
            if (repeatUntil.getMaxAttempts() != null) {
                try {
                    maxAttempts = Integer.parseInt(repeatUntil.getMaxAttempts());
                    if (maxAttempts <= 0) {
                        return errored(
                                coordinates,
                                interpolatedRequestDef,
                                "maxAttempts has a non-positive value: " + maxAttempts,
                                methodStart);
                    }
                } catch (Exception e) {
                    var message = FailureMessages.causeOf(e, "Parsing maxAttempts");
                    return errored(coordinates, interpolatedRequestDef, message, methodStart);
                }
            }

            long waitBetweenAttempts = 0;
            if (repeatUntil.getWaitBetweenAttempts() != null) {
                try {
                    waitBetweenAttempts = Long.parseLong(repeatUntil.getWaitBetweenAttempts());
                } catch (Exception e) {
                    var message = FailureMessages.causeOf(e, "Parsing waitBetweenAttempts");
                    return errored(coordinates, interpolatedRequestDef, message, methodStart);
                }
                if (waitBetweenAttempts < 0) {
                    return errored(
                            coordinates,
                            interpolatedRequestDef,
                            "waitBetweenAttempts has a negative value: " + waitBetweenAttempts,
                            methodStart);
                }
            }

            RequestStatus requestStatus = null;

            while (attempt <= maxAttempts) {
                requestStatus = performRequest(interpolatedRequestDef, runtimeData, attempt);
                // Only if the request is completed, the repeatUntil condition is evaluated. If errored, it's retried.
                if (requestStatus instanceof RequestStatus.Completed) {
                    try {
                        var interpolatedCondition = conditionInterpolator.interpolated(
                                repeatUntil.getCondition(), interpolation, runtimeData);
                        if (conditionResolver.resolve(interpolatedCondition)) {
                            attemptFinished(coordinates, attempt, maxAttempts, requestStatus, true);
                            ResponseActionsResult responseActionsResult = null;
                            if (request.responseActions() != null) {
                                responseActionsResult =
                                        responseHandler.handleResponse(request.responseActions(), runtimeData);
                            }
                            return new RequestResult(
                                    coordinates,
                                    interpolatedRequestDef,
                                    requestStatus,
                                    elapsedMs(methodStart),
                                    responseActionsResult);

                        } else {
                            attemptFinished(coordinates, attempt, maxAttempts, requestStatus, false);
                        }
                    } catch (Exception e) {
                        var message = FailureMessages.causeOf(e, "A condition");
                        var erroredStatus = new RequestStatus.Errored(message);
                        attemptFinished(coordinates, attempt, maxAttempts, erroredStatus, false);
                        return new RequestResult(
                                coordinates, interpolatedRequestDef, erroredStatus, elapsedMs(methodStart), null);
                    }
                } else {
                    attemptFinished(coordinates, attempt, maxAttempts, requestStatus, false);
                }
                sleep(waitBetweenAttempts);
                attempt++;
            }

            // Max attempts exhausted, create request result
            var exhausted = createExhaustedRequestStatus(requestStatus, repeatUntil.getMessageOnFail());
            ResponseActionsResult responseActionsResult = null;
            try {
                if (request.responseActions() != null) {
                    responseActionsResult = responseHandler.handleResponse(request.responseActions(), runtimeData);
                }
            } catch (Exception e) {
                var message = FailureMessages.causeOf(e, "A request");
                return errored(coordinates, interpolatedRequestDef, message, methodStart);
            }
            return new RequestResult(
                    coordinates, interpolatedRequestDef, exhausted, elapsedMs(methodStart), responseActionsResult);
        } finally {
            // The response belongs to this request and dies with it, on every path including a
            // structural throw. This is the whole lifecycle: nothing else clears, nothing else must
            // remember to.
            runtimeData.clearResponseVars();
        }
    }

    /**
     * Pauses between two attempts.
     * <p>
     * An interrupt is <strong>cancellation of the run, not a failure of this request</strong>, so the
     * flag is restored and the exception propagates rather than becoming a result: it is the
     * structural case this class's {@code @throws} carves out. This is the seam that matters when
     * cancellation becomes interrupt-based inside a request; swallowing it here is what would make
     * such a cancellation do nothing.
     *
     * @param millis how long to pause; zero or less returns at once
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

    private void attemptFinished(
            RequestCoordinates coordinates,
            int attempt,
            int maxAttempts,
            RequestStatus requestStatus,
            boolean conditionMet) {
        long rtt = requestStatus instanceof RequestStatus.Completed completed ? completed.roundTripTimeMs() : 0;
        var error = requestStatus instanceof RequestStatus.Errored(String message) ? message : null;
        attemptListener.accept(new AttemptFinished(coordinates, attempt, maxAttempts, rtt, conditionMet, error));
    }

    private RequestStatus createExhaustedRequestStatus(RequestStatus lastRequestStatus, String messageOnFail) {
        if (messageOnFail == null) {
            messageOnFail = "Max attempts exhausted";
        }
        // Every permit is listed rather than defaulted: a fifth RequestStatus would then fail to
        // compile here instead of falling silently into a default arm. A null cannot reach this - a
        // positive maxAttempts means the loop always assigned one - so it is not handled either.
        return switch (lastRequestStatus) {
            case RequestStatus.Errored _ -> lastRequestStatus;
            case RequestStatus.Completed completed -> new RequestStatus.GaveUp(completed, messageOnFail);
            case RequestStatus.Skipped _, RequestStatus.GaveUp _ ->
                new RequestStatus.Errored("The last performed request has a disallowed status: "
                        + lastRequestStatus.getClass().getSimpleName());
        };
    }

    private RequestResult performRequestAndResponseActions(
            HttpRequestDefinition requestDefinition,
            RuntimeData runtimeData,
            ResponseActions responseActions,
            RequestCoordinates coordinates,
            long methodStart) {
        var status = performRequest(requestDefinition, runtimeData, 1);
        if (status instanceof RequestStatus.Errored) {
            return new RequestResult(coordinates, requestDefinition, status, elapsedMs(methodStart), null);
        }
        try {
            ResponseActionsResult responseActionsResult = null;
            if (responseActions != null) {
                responseActionsResult = responseHandler.handleResponse(responseActions, runtimeData);
            }
            return new RequestResult(
                    coordinates, requestDefinition, status, elapsedMs(methodStart), responseActionsResult);
        } catch (Exception e) {
            var message = FailureMessages.causeOf(e, "A request");
            return errored(coordinates, requestDefinition, message, methodStart);
        }
    }

    private RequestStatus performRequest(
            HttpRequestDefinition requestDefinition, RuntimeData runtimeData, int numAttempts) {
        try {
            long requestStart = System.currentTimeMillis();
            var response = requestHandler.performRequest(requestDefinition);
            long rtt = elapsedMs(requestStart);
            var responseVars = HttpResponseVars.of(response);
            runtimeData.setResponseVars(responseVars);
            return new RequestStatus.Completed(responseVars, numAttempts, rtt);
        } catch (Exception e) {
            return new RequestStatus.Errored(FailureMessages.causeOf(e, "A request"));
        }
    }

    /**
     * An errored result for {@code definition}, timed from {@code methodStart}.
     * <p>
     * Every failure site builds the same five-argument result, and the only thing that varies is
     * <em>which</em> definition it carries — the authored one before interpolation has run, the
     * interpolated copy after. Passing that as an argument here rather than repeating the whole
     * construction is what keeps the two from being confused, which they were.
     *
     * @param coordinates where this request sits
     * @param definition the definition to report: authored where interpolation had not yet run or
     *        was itself what failed, the interpolated copy otherwise
     * @param message why it failed
     * @param methodStart when the request started, for {@code elapsedMs}
     * @return the errored result
     */
    private static RequestResult errored(
            RequestCoordinates coordinates, RequestDefinition definition, String message, long methodStart) {
        return new RequestResult(
                coordinates, definition, new RequestStatus.Errored(message), elapsedMs(methodStart), null);
    }

    private static long elapsedMs(long start) {
        return System.currentTimeMillis() - start;
    }
}
