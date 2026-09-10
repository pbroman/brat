package dev.pbroman.brat.core.api.handler;

import dev.pbroman.brat.core.data.ResponseActions;
import dev.pbroman.brat.core.data.result.AssertionResult;
import dev.pbroman.brat.core.data.result.CaptureFailure;
import dev.pbroman.brat.core.data.result.ResponseActionsResult;
import dev.pbroman.brat.core.data.runtime.CaptureTombstone;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;

/**
 * Interface for handling an already-executed request's response using pre-defined
 * {@link ResponseActions} and the {@link RuntimeData}.
 */
public interface ResponseHandler {

    /**
     * Handles a response according to the defined actions for this response.
     * <p>
     * <strong>Inside a request nothing escapes.</strong> Every failure an action can produce becomes
     * data on the returned result rather than an exception: an assertion that cannot be interpolated
     * or resolved is a failed {@link AssertionResult}, and a capture that cannot be resolved is a
     * {@link CaptureFailure}. Every remaining assertion and capture still runs, so one broken entry
     * does not hide the rest.
     * <p>
     * <strong>A failed capture also marks its variable.</strong> The key is removed from {@code vars}
     * and given a {@link CaptureTombstone}, so a later request reading it fails with the cause instead
     * of resolving to the empty string. A capture that succeeds clears any tombstone the key carried.
     *
     * @param responseActions the actions to perform; never {@code null}
     * @param runtimeData the runtime data, mutated by every successful capture; never {@code null}
     * @return what the actions produced, never {@code null} — empty lists rather than {@code null}
     *         where there was nothing to do
     * @throws BratException only for a structural failure that is nobody's action — a {@code null}
     *         argument, or a missing collaborator. Never for a failing assertion or capture, which
     *         are the data this returns
     */
    ResponseActionsResult handleResponse(ResponseActions responseActions, RuntimeData runtimeData);
}
