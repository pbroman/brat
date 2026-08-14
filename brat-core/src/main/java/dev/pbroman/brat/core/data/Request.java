package dev.pbroman.brat.core.data;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import dev.pbroman.brat.core.api.data.RequestDefinition;

/**
 * One request in a suite: what to send, what to check about the response, and how to pace it.
 * <p>
 * A structural container, deliberately <em>not</em> a {@link ConfigData}: its interpolatable content
 * <em>is</em> other types, interpolated at different moments during the run, so there is no instant
 * at which "the request, interpolated" is a meaningful object.
 *
 * @param name names the node in reports and forms the last segment of its path. The loader requires
 *        it, requires it to be unique among its siblings, and rejects a {@code /} in it — this type
 *        enforces none of that
 * @param description optional prose, shown in reports and read by nothing
 * @param id optional, author-supplied, never generated. Means only "this node survives a rename" —
 *        baselines and trend history key on it and fall back to the path without one. The loader
 *        rejects a duplicate id within one document
 * @param skipCondition skip this request when it holds, or {@code null} to always run it
 * @param phase when this request runs relative to its siblings; never {@code null}, defaulting to
 *        {@link Phase#MAIN}
 * @param requestHandlers which handler executes this request, keyed by protocol; never {@code null}.
 *        Merges per key with what it inherits, rather than replacing wholesale
 * @param requestDefinition what to send. Typed to the interface rather than to
 *        {@code HttpRequestDefinition} so that a protocol plugin's definition needs no change here
 * @param responseActions what to check and capture from the response, or {@code null} for neither
 * @param flowControl how to pace the request, or {@code null} to run it once with no wait
 */
public record Request(
        String name,
        String description,
        String id,
        Condition skipCondition,
        Phase phase,
        Map<String, String> requestHandlers,
        RequestDefinition requestDefinition,
        ResponseActions responseActions,
        FlowControl flowControl) {

    /**
     * Normalises the two fields with defaults and copies the mutable one.
     * <p>
     * {@code phase} and {@code requestHandlers} are never {@code null} on a constructed instance, so
     * the walk needs no null checks for either. Nothing else is defaulted: an absent
     * {@code requestDefinition} is a load error rather than something to substitute for, and the
     * loader is where that is caught.
     */
    public Request {
        phase = phase == null ? Phase.MAIN : phase;
        requestHandlers =
                requestHandlers == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(requestHandlers));
    }
}
