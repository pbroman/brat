package dev.pbroman.brat.core.api.rendering;

import dev.pbroman.brat.core.exception.BratException;

/**
 * Renders one object's interpolation outcomes as text.
 * <p>
 * This is the display side of interpolation — what a request's fields resolved to, with secrets
 * masked — not a report of a test run.
 */
public interface OutcomeRenderer {

    /**
     * Renders {@code target} in the shape identified by {@code kind}.
     *
     * @param kind the render kind (e.g. {@code "console"}, {@code "verbose-cli"}, {@code "log"})
     * @param target what to render: the named outcomes, and an optional label naming them
     * @return the rendered text, or {@code null} if this implementation does not recognize
     *         {@code kind}
     * @throws BratException if this implementation recognizes {@code kind} but cannot produce a
     *         rendering — declining by returning {@code null} is reserved for a {@code kind} that
     *         belongs to some other implementation, so a failure must be raised rather than
     *         returned
     */
    String render(String kind, RenderTarget target);
}
