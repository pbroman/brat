package dev.pbroman.brat.core.api.handler;

import java.util.List;

import dev.pbroman.brat.core.data.ResponseActions;
import dev.pbroman.brat.core.data.result.AssertionResult;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;

/**
 * Interface for handling an already-executed request's response using pre-defined {@link ResponseActions} and the
 * {@link RuntimeData}.
 */
public interface ResponseHandler {

    /**
     * Handles a response according to the defined actions for this response.
     *
     * @param responseActions the actions
     * @param runtimeData the runtime data
     * @return a list of {@link AssertionResult}s
     * @throws BratException if handling fails in a way not itself captured as a failed
     *         {@link AssertionResult}
     */
    List<AssertionResult> handleResponse(ResponseActions responseActions, RuntimeData runtimeData);
}
