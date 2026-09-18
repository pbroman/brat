package dev.pbroman.brat.core.api.handler;

import java.util.Map;

import dev.pbroman.brat.core.api.data.RequestDefinition;
import dev.pbroman.brat.core.exception.BratException;

/**
 * Executes a {@link RequestDefinition} against its protocol and reports what came back — one
 * implementation per protocol <em>and configuration</em> (see {@link HttpRequestHandler} for HTTP).
 * <p>
 * <strong>A handler carries its own key.</strong> Every registration path except hand-written Java
 * loses a key supplied alongside the object rather than inside it: {@code ServiceLoader} yields bare
 * instances and no names, and erasure has already discarded {@code T}. The key is two-part —
 * {@link #protocol()} says what kind of request this executes, {@link #name()} which of the handlers
 * for that protocol this one is — because several differently configured handlers for one protocol
 * are registered at once and a suite selects between them by name. A proxy, a self-signed
 * certificate or an mTLS client certificate is a differently configured handler, not a different
 * implementation.
 * <p>
 * <strong>Declare a protocol sub-interface rather than implementing this directly.</strong>
 * {@link #protocol()}, {@link #definitionType()} and {@link #responseVars(Object)} are facts about a
 * protocol rather than about one handler, so they belong on one interface per protocol as
 * {@code default} methods — which is what stops two handlers for the same protocol spelling the
 * protocol differently, disagreeing about which definition class is theirs, or reporting a response
 * two ways. An individual handler then supplies only {@link #name()} and
 * {@link #performRequest(RequestDefinition)}.
 *
 * @param <T> the concrete {@link RequestDefinition} type this handler executes
 * @param <R> the response type this handler produces. It never travels past the handler — what
 *        reaches the rest of a run is {@link #responseVars(Object)}'s namespace form — so a protocol
 *        is free to make this whatever suits it
 */
public interface RequestHandler<T extends RequestDefinition, R> {

    /**
     * The kind of request this handler executes, which is half of its registration key and the thing
     * a suite's {@code requestHandlers} map is keyed by.
     *
     * @return the protocol, for example {@code http}; never {@code null} or blank. It must equal the
     *         {@link RequestDefinition#protocol()} of the definitions this handler executes, since
     *         that is what a request is dispatched on
     */
    String protocol();

    /**
     * Which of the handlers for {@link #protocol()} this one is — the name a suite writes to select
     * it.
     *
     * @return the name, for example {@code httpclient5}; never {@code null} or blank. Two registered
     *         handlers sharing a name for one protocol is an overlay rather than an error: the last
     *         registered wins, logged at WARN, which is what lets a consumer replace a built-in
     *         handler while every suite goes on naming it
     */
    String name();

    /**
     * The class an authored {@code requestDefinition:} block binds to for this protocol.
     *
     * @return the definition type; never {@code null}. It is a type token because erasure has
     *         discarded {@code T} before anything sees an instance. Two handlers for one protocol
     *         disagreeing about it is a wiring <em>failure</em> rather than an overlay, since a
     *         document can only bind to one class per protocol
     */
    Class<T> definitionType();

    /**
     * Performs the request described by {@code requestDefinition} and returns the result.
     * <p>
     * <strong>Exactly one protocol call.</strong> A handler does not retry: retrying is the suite's,
     * declared as {@code repeatUntil}, which counts its attempts, waits the interval the author
     * chose and reports what it did. A retry in here is invisible to all of that, makes the recorded
     * attempt count wrong, puts its own waiting inside the measured round trip, and re-sends a write
     * the server may already have processed.
     * <p>
     * <strong>The definition arrives interpolated</strong>, so every value on it is final text.
     *
     * @param requestDefinition the request to perform; never {@code null}
     * @return what the protocol answered; never {@code null}. An answer the caller did not want is
     *         still an answer — for HTTP, every status code including 4xx and 5xx is a response
     * @throws BratException if the request cannot be completed at all, which is the only case that
     *         throws: there is no result to return. The runner records this as an errored request
     *         and the run continues
     */
    R performRequest(T requestDefinition);

    /**
     * Flattens a response into the namespace a suite reads it through, so that
     * {@code ${<protocol vocabulary>}} tokens have something to resolve against.
     * <p>
     * The vocabulary is the protocol's own and should be declared once on the protocol sub-interface
     * — {@link HttpRequestHandler} answers with {@code statusCode}, {@code body}, {@code headers} and
     * {@code json}. The map <strong>replaces</strong> the namespace of the request before it rather
     * than merging into it, so a key this method omits is absent rather than stale.
     *
     * @param response the response to flatten, as {@link #performRequest(RequestDefinition)}
     *        returned it
     * @return the namespace; never {@code null} — an empty map is how a protocol says it publishes
     *         nothing, and is the answer for a protocol whose responses have no readable parts
     */
    Map<String, Object> responseVars(R response);
}
