package dev.pbroman.brat.core.api;

import java.util.List;

import dev.pbroman.brat.core.data.ResponseActions;
import dev.pbroman.brat.core.data.result.AssertionResult;
import dev.pbroman.brat.core.data.runtime.RuntimeData;


public interface ResponseHandler {

    /**
     * Handles a response according to the defined actions for this response.
     *
     * @param responseActions the actions
     * @param runtimeData the runtime data
     * @return a list of {@link AssertionResult}s.
     */
    List<AssertionResult> handleResponse(ResponseActions responseActions, RuntimeData runtimeData);
}
