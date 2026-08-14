package dev.pbroman.brat.core.data.result;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * How a request execution ended — the terminal state of the protocol call, and nothing else.
 * <p>
 * ⚠ <strong>This is not the verdict.</strong> A request can be {@link Completed} and still have
 * failed, through a failed assertion or a failed capture; "did this pass" is derived from the whole
 * {@link RequestResult}, never read off the status. The naming follows the convention that a
 * {@code *Status} is a terminal state while a {@code *Result} is the full record of what happened.
 * <p>
 * Sealed rather than an enum with nullable companions, so that data which exists only for one outcome
 * lives only on that outcome — the reference encoded "skipped" as {@code numAttempt = -1} with a null
 * response, which is the magic-value pattern this replaces.
 */
public sealed interface RequestStatus {

    /**
     * The protocol call completed and returned something.
     *
     * @param responseVars the response flattened into its namespace form by the handler — the same
     *        view assertions read through {@code ${response.*}}. A snapshot: the runtime namespace is
     *        replaced by the next request
     * @param numAttempts how many attempts it took, {@code 1} for a request that does not poll
     * @param roundTripTimeMs one protocol call in milliseconds, and for a polled request the
     *        <em>final</em> attempt's. Deliberately here rather than on {@link RequestResult}, so an
     *        aggregate over response times cannot pick up a connection refusal or a timeout — both
     *        of which measure BRAT's configuration rather than the service
     */
    record Completed(Map<String, Object> responseVars, int numAttempts, long roundTripTimeMs) implements RequestStatus {

        /** Defaults {@code responseVars} to empty and copies it. */
        public Completed {
            responseVars =
                    responseVars == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(responseVars));
        }
    }

    /**
     * The request was not attempted, because its skip condition held.
     *
     * @param reason why it was skipped, for the report
     */
    record Skipped(String reason) implements RequestStatus {}

    /**
     * The request could not be performed — it could not be interpolated, or the call itself failed.
     *
     * @param message what went wrong
     */
    record Errored(String message) implements RequestStatus {}
}
