package dev.pbroman.brat.core.runner;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import dev.pbroman.brat.core.data.Request;
import dev.pbroman.brat.core.data.RequestOptions;
import dev.pbroman.brat.core.data.TestSuite;
import org.apache.commons.lang3.ObjectUtils;

/**
 * The orchestration defaults a request inherits from the suites above it, carried down the walk.
 * <p>
 * The walk folds each suite's declarations in as it enters it, and resolves the result against each
 * request. It is immutable: folding a suite in yields a new instance, so what one subSuite declares
 * never reaches its siblings.
 * <p>
 * <strong>A {@code null} means "not declared", and only a {@code null}.</strong> Any other value is a
 * declaration and overrides what was inherited — a {@code timeout} of {@code default} or blank
 * included, both of which read as the default.
 *
 * @param timeout the nearest declared timeout, in text form and not yet interpolated, or {@code null}
 *        if no enclosing suite declared one
 * @param requestHandlers the handler names declared by the enclosing suites, merged per protocol with
 *        the nearest suite winning; never {@code null}
 */
record InheritedDefaults(String timeout, Map<String, String> requestHandlers) {

    /** What the root suite inherits: nothing. */
    static final InheritedDefaults NONE = new InheritedDefaults(null, Map.of());

    /**
     * Defaults {@code requestHandlers} to empty and copies it.
     *
     * @param timeout the nearest declared timeout, or {@code null}
     * @param requestHandlers the merged handler names, or {@code null} for none
     */
    InheritedDefaults {
        requestHandlers =
                requestHandlers == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(requestHandlers));
    }

    /**
     * These defaults with {@code suite}'s own declarations folded in, as seen by everything beneath
     * it.
     * <ul>
     *   <li>{@code timeout}: the suite's, if it declares one; otherwise the inherited one.</li>
     *   <li>{@code requestHandlers}: the inherited names with the suite's laid over them per
     *       protocol — the suite's entry wins where both name one, and an inherited protocol the suite
     *       does not mention is kept.</li>
     * </ul>
     *
     * @param suite the suite being entered; never {@code null}
     * @return a new instance; this one is unchanged
     */
    InheritedDefaults with(TestSuite suite) {
        return new InheritedDefaults(
                ObjectUtils.firstNonNull(suite.timeout(), timeout), overlaid(requestHandlers, suite.requestHandlers()));
    }

    /**
     * The options {@code request} runs with, as authored: its own {@code timeout} if it declares one,
     * otherwise the inherited one.
     *
     * @param request the request about to run; never {@code null}
     * @return a new, not yet interpolated {@link RequestOptions}. Its {@code timeout} is {@code null}
     *         when neither the request nor any enclosing suite declared one, leaving the default to be
     *         applied when the value is read
     */
    RequestOptions options(Request request) {
        return new RequestOptions(ObjectUtils.firstNonNull(request.timeout(), timeout));
    }

    /**
     * The handler names {@code request} is resolved with: the inherited names with the request's own
     * laid over them per protocol, the request's entry winning where both name one.
     *
     * @param request the request about to run; never {@code null}
     * @return a new map; never {@code null}, possibly empty. These defaults are unchanged
     */
    Map<String, String> handlerNames(Request request) {
        return overlaid(requestHandlers, request.requestHandlers());
    }

    /**
     * {@code inherited} with {@code nearer} laid over it per protocol: the nearer entry wins where
     * both name a protocol, and an inherited protocol {@code nearer} does not mention is kept.
     *
     * @param inherited the names declared further out
     * @param nearer the names declared closer to the request
     * @return a new mutable map, inherited entries first in their order, then any the nearer map adds;
     *         neither argument is changed
     */
    private static Map<String, String> overlaid(Map<String, String> inherited, Map<String, String> nearer) {
        var names = new LinkedHashMap<>(inherited);
        names.putAll(nearer);
        return names;
    }
}
