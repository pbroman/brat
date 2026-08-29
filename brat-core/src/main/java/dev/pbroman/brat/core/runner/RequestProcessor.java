package dev.pbroman.brat.core.runner;

import dev.pbroman.brat.core.api.data.RequestDefinition;
import dev.pbroman.brat.core.api.handler.HttpRequestHandler;
import dev.pbroman.brat.core.api.handler.ResponseHandler;
import dev.pbroman.brat.core.api.interpolation.ConfigDataInterpolator;
import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.api.resolver.ConditionResolver;
import dev.pbroman.brat.core.data.Condition;
import dev.pbroman.brat.core.data.HttpRequestDefinition;
import dev.pbroman.brat.core.data.Request;
import dev.pbroman.brat.core.data.result.HttpResponse;
import dev.pbroman.brat.core.data.result.RequestCoordinates;
import dev.pbroman.brat.core.data.result.RequestResult;
import dev.pbroman.brat.core.data.result.RequestStatus;
import dev.pbroman.brat.core.data.result.ResponseActionsResult;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.handler.HttpResponseVars;
import dev.pbroman.brat.core.util.FailureMessages;
import dev.pbroman.brat.core.util.Require;

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
public class RequestProcessor {

    private final Interpolation interpolation;
    private final ConfigDataInterpolator<HttpRequestDefinition> requestDefinitionInterpolator;
    private final ConfigDataInterpolator<Condition> conditionInterpolator;
    private final ConditionResolver conditionResolver;
    private final HttpRequestHandler requestHandler;
    private final ResponseHandler responseHandler;

    /**
     * Constructs a processor over the collaborators it delegates to.
     *
     * @param interpolation resolves the tokens in a definition, a skip condition and a capture
     * @param requestDefinitionInterpolator produces the interpolated copy of the request definition
     * @param conditionInterpolator produces the interpolated copy of a skip condition
     * @param conditionResolver answers a skip condition once it is interpolated
     * @param requestHandler performs the request
     * @param responseHandler runs the response actions against the response
     */
    public RequestProcessor(
            Interpolation interpolation,
            ConfigDataInterpolator<HttpRequestDefinition> requestDefinitionInterpolator,
            ConfigDataInterpolator<Condition> conditionInterpolator,
            ConditionResolver conditionResolver,
            HttpRequestHandler requestHandler,
            ResponseHandler responseHandler) {
        this.interpolation = interpolation;
        this.requestDefinitionInterpolator = requestDefinitionInterpolator;
        this.conditionInterpolator = conditionInterpolator;
        this.conditionResolver = conditionResolver;
        this.requestHandler = requestHandler;
        this.responseHandler = responseHandler;
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
     *   <li><strong>The request is performed.</strong> Failing here is {@link RequestStatus.Errored}
     *       too, and the result carries the interpolated definition. Note what is <em>not</em> a
     *       failure: every status code the server answered with, including 4xx and 5xx, is a
     *       response.</li>
     *   <li><strong>The response is flattened</strong> into the {@code responseVars} namespace,
     *       replacing whatever the previous request left there. A request that <em>errored or was
     *       skipped</em> <strong>clears</strong> it instead, so there is no previous response rather
     *       than an old one — which is what makes {@code ${response.*}} mean "the request immediately
     *       before this one" and makes it fail rather than quietly answering from further back. An
     *       assertion that passes against some earlier request's response is worse than one that
     *       stops and says why.</li>
     *   <li><strong>The response actions run</strong>, when the request declares any, and whatever
     *       they produce is carried on the result.</li>
     * </ol>
     *
     * <p>
     * <strong>What the result's numbers mean.</strong>
     * {@code elapsedMs} covers this whole method: interpolation, the call, and the response actions.
     * {@code roundTripTimeMs} on {@link RequestStatus.Completed} covers the call alone, which is why
     * an aggregate over response times reads that one. {@code numAttempts} is always {@code 1} — this
     * performs a request once and does not poll.
     *
     * <p>
     * <strong>What it mutates.</strong>
     * {@code runtimeData} only. Its {@code currentPath} and {@code currentRequestNo} are set from
     * {@code coordinates} first, which is what lets code called further down record where it was
     * without the identity being threaded through every signature — a capture tombstone is the one
     * that needs it. Then {@code responseVars} is replaced on a completed request and <strong>cleared
     * on a skipped or errored one</strong>, and {@code vars} gains every successful capture and a
     * tombstone for every failed one.
     * <p>
     * {@code responseVars} is only touched <em>after</em> the skip condition and the definition have
     * been interpolated, because both may legitimately read {@code ${response.*}} from the request
     * before this one — a URL like {@code /orders/${response.json.$.id}} is the ordinary case.
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
                    runtimeData.clearResponseVars();
                    return new RequestResult(
                            coordinates,
                            requestDef,
                            new RequestStatus.Skipped("Skipped due to condition " + condition),
                            elapsedMs(methodStart),
                            null);
                }
            } catch (Exception e) {
                runtimeData.clearResponseVars();
                var message = FailureMessages.causeOf(e, "A condition");
                return new RequestResult(
                        coordinates,
                        requestDef,
                        new RequestStatus.Errored("The skip condition failed: " + message),
                        elapsedMs(methodStart),
                        null);
            }
        }

        HttpRequestDefinition interpolated;
        try {
            interpolated = requestDefinitionInterpolator.interpolated(requestDef, interpolation, runtimeData);
        } catch (Exception e) {
            runtimeData.clearResponseVars();
            var message = FailureMessages.causeOf(e, "Request definition interpolation");
            return new RequestResult(
                    coordinates, requestDef, new RequestStatus.Errored(message), elapsedMs(methodStart), null);
        }

        runtimeData.clearResponseVars();

        HttpResponse response;
        try {
            long requestStart = System.currentTimeMillis();
            response = requestHandler.performRequest(interpolated);
            long rtt = elapsedMs(requestStart);
            var responseVars = HttpResponseVars.of(response);
            runtimeData.setResponseVars(responseVars);
            ResponseActionsResult responseActionsResult = null;
            if (request.responseActions() != null) {
                responseActionsResult = responseHandler.handleResponse(request.responseActions(), runtimeData);
            }
            return new RequestResult(
                    coordinates,
                    interpolated,
                    new RequestStatus.Completed(responseVars, 1, rtt),
                    elapsedMs(methodStart),
                    responseActionsResult);
        } catch (Exception e) {
            var message = FailureMessages.causeOf(e, "A request");
            return new RequestResult(
                    coordinates, interpolated, new RequestStatus.Errored(message), elapsedMs(methodStart), null);
        }
    }

    private static long elapsedMs(long start) {
        return System.currentTimeMillis() - start;
    }
}
