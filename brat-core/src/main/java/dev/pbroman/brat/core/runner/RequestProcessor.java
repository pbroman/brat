package dev.pbroman.brat.core.runner;

import java.util.Optional;

import dev.pbroman.brat.core.api.data.RequestDefinition;
import dev.pbroman.brat.core.api.handler.HttpRequestHandler;
import dev.pbroman.brat.core.api.handler.ResponseHandler;
import dev.pbroman.brat.core.api.interpolation.ConfigDataInterpolator;
import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.data.FlowControl;
import dev.pbroman.brat.core.data.HttpRequestDefinition;
import dev.pbroman.brat.core.data.Request;
import dev.pbroman.brat.core.data.result.RequestCoordinates;
import dev.pbroman.brat.core.data.result.RequestResult;
import dev.pbroman.brat.core.data.result.RequestStatus;
import dev.pbroman.brat.core.data.result.ResponseActionsResult;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.util.FailureMessages;
import dev.pbroman.brat.core.util.Require;
import lombok.extern.slf4j.Slf4j;

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
    private final ConditionEvaluator conditionEvaluator;
    private final ResponseHandler responseHandler;
    private final ConfigDataInterpolator<FlowControl> flowControlInterpolator;
    private final RequestExecutor requestExecutor;

    /**
     * Constructs a processor over the collaborators it delegates to.
     *
     * @param interpolation resolves the tokens in a definition, a skip condition and a capture
     * @param requestDefinitionInterpolator produces the interpolated copy of the request definition
     * @param conditionEvaluator interpolates and answers the skip condition
     * @param responseHandler runs the response actions against the response
     * @param flowControlInterpolator produces the interpolated copy of the flow control, whose
     *        {@code maxAttempts} and {@code waitBetweenAttempts} may themselves be tokens; it leaves
     *        the loop condition authored, for the executor to resolve per attempt
     * @param requestExecutor performs the request's attempts and says how they ended
     */
    public RequestProcessor(
            Interpolation interpolation,
            ConfigDataInterpolator<HttpRequestDefinition> requestDefinitionInterpolator,
            ConditionEvaluator conditionEvaluator,
            ResponseHandler responseHandler,
            ConfigDataInterpolator<FlowControl> flowControlInterpolator,
            RequestExecutor requestExecutor) {
        this.interpolation = interpolation;
        this.requestDefinitionInterpolator = requestDefinitionInterpolator;
        this.conditionEvaluator = conditionEvaluator;
        this.responseHandler = responseHandler;
        this.flowControlInterpolator = flowControlInterpolator;
        this.requestExecutor = requestExecutor;
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
     *   <li><strong>The flow control's bounds are interpolated</strong>, when the request declares
     *       one, since {@code maxAttempts} and {@code waitBetweenAttempts} may be tokens. Failing
     *       here is {@link RequestStatus.Errored}: the loop's bounds are not knowable, and guessing
     *       them is how a suite spins. <strong>The loop condition is not interpolated here</strong> —
     *       it reads the response the loop is waiting for, so it is resolved per attempt.</li>
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
     * <strong>What a poll can end as</strong> is {@link RequestExecutor}'s to decide and is documented
     * there: the condition holding, the attempts running out with a response in hand, or the attempts
     * running out with the final one errored. The bounds it runs within — including what is rejected
     * and what is defaulted — belong to {@link PollBounds}. Neither is restated here, so that neither
     * can drift from it.
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
                var evaluation = conditionEvaluator.evaluate(request.skipCondition(), runtimeData);
                if (evaluation.holds()) {
                    return new RequestResult(
                            coordinates,
                            requestDef,
                            new RequestStatus.Skipped("Skipped due to condition " + evaluation.condition()),
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

        Optional<PollBounds> pollBounds;
        try {
            pollBounds = request.flowControl() == null
                    ? Optional.empty()
                    : PollBounds.of(
                            flowControlInterpolator.interpolated(request.flowControl(), interpolation, runtimeData));
        } catch (Exception e) {
            var message = FailureMessages.causeOf(e, "Flow control");
            return errored(coordinates, interpolatedRequestDef, message, methodStart);
        }

        try {
            var status = requestExecutor.execute(interpolatedRequestDef, pollBounds, coordinates, runtimeData);
            ResponseActionsResult responseActionsResult = null;
            if (carriesAResponse(status) && request.responseActions() != null) {
                try {
                    responseActionsResult = responseHandler.handleResponse(request.responseActions(), runtimeData);
                } catch (Exception e) {
                    var message = FailureMessages.causeOf(e, "The response actions");
                    return errored(coordinates, interpolatedRequestDef, message, methodStart);
                }
            }
            return new RequestResult(
                    coordinates, interpolatedRequestDef, status, elapsedMs(methodStart), responseActionsResult);
        } finally {
            // The response belongs to this request and dies with it, on every path including a
            // structural throw. This is the whole lifecycle: nothing else clears, nothing else must
            // remember to.
            runtimeData.clearResponseVars();
        }
    }

    /**
     * Whether {@code status} ended holding a response, and so whether the response actions have
     * anything to run against.
     * <p>
     * The predicate is deliberately one place rather than a decision repeated per execution path: it
     * is also exactly the condition under which {@code responseVars} holds <em>this</em> status's
     * response, since a completed attempt is the only thing that writes it.
     */
    private static boolean carriesAResponse(RequestStatus status) {
        return status instanceof RequestStatus.Completed || status instanceof RequestStatus.GaveUp;
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
