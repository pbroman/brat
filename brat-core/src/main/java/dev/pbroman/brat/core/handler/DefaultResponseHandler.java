package dev.pbroman.brat.core.handler;

import java.util.ArrayList;
import java.util.List;

import dev.pbroman.brat.core.api.ResponseHandler;
import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.api.resolver.AssertionResolver;
import dev.pbroman.brat.core.data.ResponseActions;
import dev.pbroman.brat.core.data.result.AssertionResult;
import dev.pbroman.brat.core.data.result.Validation;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.ValidationException;

public class DefaultResponseHandler implements ResponseHandler {

    private final Interpolation interpolation;
    private final AssertionResolver assertionResolver;

    public DefaultResponseHandler(Interpolation interpolation,
                                  AssertionResolver assertionResolver) {
        this.interpolation = interpolation;
        this.assertionResolver = assertionResolver;
    }

    @Override
    public List<AssertionResult> handleResponse(ResponseActions responseActions, RuntimeData runtimeData) {

        var assertionResults = new ArrayList<AssertionResult>();
        responseActions.getAssertions().forEach(assertion ->
            assertionResults.addAll(assertionResolver.resolve(assertion, runtimeData))
        );

        responseActions.getSetVars().forEach((key, value) -> {
            try {
                runtimeData.getVars().put(key, interpolation.interpolate(value, runtimeData));
            } catch (ValidationException e) {
                runtimeData.getValidations().add(
                        new Validation(String.format("Interpolation of %s failed, message: %s", value, e.getMessage()), e.getValidationType())
                );
            }
        });

        return assertionResults;
    }
}
