package dev.pbroman.brat.core.handler;

import java.util.ArrayList;

import dev.pbroman.brat.core.api.handler.ResponseHandler;
import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.api.resolver.AssertionResolver;
import dev.pbroman.brat.core.data.ResponseActions;
import dev.pbroman.brat.core.data.result.AssertionResult;
import dev.pbroman.brat.core.data.result.CaptureFailure;
import dev.pbroman.brat.core.data.result.ResponseActionsResult;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.util.FailureMessages;
import dev.pbroman.brat.core.util.Require;

/**
 * Executes the {@link ResponseActions} declared for a response: resolves every assertion and applies
 * every {@code setVars} entry to the {@link RuntimeData}.
 * <p>
 * This is the one deliberate conversion boundary in {@code brat-core}: a failure inside an action
 * becomes data rather than an escaping {@link dev.pbroman.brat.core.exception.BratException}, so a
 * suite runs to completion and reports every failure instead of aborting on the first. An assertion
 * that cannot be interpolated or resolved becomes a failed {@link AssertionResult}; a capture that
 * cannot be resolved becomes a {@link dev.pbroman.brat.core.data.result.CaptureFailure} and leaves a
 * {@link dev.pbroman.brat.core.data.runtime.CaptureTombstone} on its variable.
 */
public class ResponseActionsHandler implements ResponseHandler {

    private final Interpolation interpolation;
    private final AssertionResolver assertionResolver;

    /**
     * Constructs a handler over the collaborators it delegates to.
     *
     * @param interpolation the interpolation used for {@code setVars} values
     * @param assertionResolver the resolver every declared assertion is passed to
     */
    public ResponseActionsHandler(Interpolation interpolation, AssertionResolver assertionResolver) {
        this.interpolation = interpolation;
        this.assertionResolver = assertionResolver;
    }

    @Override
    public ResponseActionsResult handleResponse(ResponseActions responseActions, RuntimeData runtimeData) {
        Require.nonNull(responseActions, "The responseActions must not be null");
        Require.nonNull(runtimeData, "The runtimeData must not be null");

        var assertionResults = new ArrayList<AssertionResult>();
        for (var assertion : responseActions.getAssertions()) {
            assertionResults.addAll(assertionResolver.resolve(assertion, runtimeData));
        }

        var captureFailures = new ArrayList<CaptureFailure>();
        for (var setVar : responseActions.getSetVars().entrySet()) {
            try {
                var interpolated = interpolation.interpolate(setVar.getValue(), runtimeData);
                runtimeData.captureVar(setVar.getKey(), interpolated);
            } catch (Exception e) {
                var message = FailureMessages.causeOf(e, "The capture");
                runtimeData.captureFailed(setVar.getKey(), message);
                captureFailures.add(new CaptureFailure(setVar.getKey(), setVar.getValue(), message));
            }
        }
        return new ResponseActionsResult(assertionResults, captureFailures);
    }
}
