package dev.pbroman.brat.core.handler;

import java.util.ArrayList;
import java.util.List;

import dev.pbroman.brat.core.api.handler.ResponseHandler;
import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.api.resolver.AssertionResolver;
import dev.pbroman.brat.core.data.ResponseActions;
import dev.pbroman.brat.core.data.result.AssertionResult;
import dev.pbroman.brat.core.data.runtime.RuntimeData;

/**
 * Executes the {@link ResponseActions} declared for a response: resolves every assertion and applies
 * every {@code setVars} entry to the {@link RuntimeData}.
 * <p>
 * This is the one deliberate conversion boundary in {@code brat-core}: an assertion that cannot even
 * be interpolated becomes a failed {@link AssertionResult} rather than an escaping
 * {@link dev.pbroman.brat.core.exception.BratException}, so a suite runs to completion and reports
 * every failure instead of aborting on the first.
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
    public List<AssertionResult> handleResponse(ResponseActions responseActions, RuntimeData runtimeData) {

        var assertionResults = new ArrayList<AssertionResult>();
        responseActions
                .getAssertions()
                .forEach(assertion -> assertionResults.addAll(assertionResolver.resolve(assertion, runtimeData)));

        responseActions.getSetVars().forEach((key, value) -> {
            runtimeData.getVars().put(key, interpolation.interpolate(value, runtimeData));
        });

        return assertionResults;
    }
}
