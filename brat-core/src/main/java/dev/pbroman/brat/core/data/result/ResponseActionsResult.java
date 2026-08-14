package dev.pbroman.brat.core.data.result;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Everything the response actions produced for one request: the assertions that were resolved, and
 * the captures that failed.
 * <p>
 * A type rather than two fields on {@link RequestResult} because the two lists are jointly "what the
 * response-actions layer produced", and that layer owns its own result shape — a third kind of
 * response action would grow this record rather than change {@link RequestResult}'s signature.
 *
 * @param assertionResults one result per resolved condition — an assertion's own plus one per chain
 *        link — in that order; never {@code null}
 * @param captureFailures the captures that could not be resolved, in declaration order; never
 *        {@code null}. Successful captures leave nothing here: they are in {@code vars}
 */
public record ResponseActionsResult(List<AssertionResult> assertionResults, List<CaptureFailure> captureFailures) {

    /** An empty result, for a request whose response actions never ran. */
    public static final ResponseActionsResult NONE = new ResponseActionsResult(List.of(), List.of());

    /**
     * Defaults both lists to empty and copies them.
     */
    public ResponseActionsResult {
        assertionResults =
                assertionResults == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(assertionResults));
        captureFailures =
                captureFailures == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(captureFailures));
    }
}
