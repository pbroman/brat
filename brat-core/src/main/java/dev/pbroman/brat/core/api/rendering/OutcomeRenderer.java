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
     * <p>
     * Every outcome is shown by its
     * {@link dev.pbroman.brat.core.api.interpolation.InterpolationOutcome#reportingString()
     * reportingString}, never by its value, so a secret appears masked.
     *
     * @param kind the render kind (e.g. {@code "console"}, {@code "verbose-cli"}, {@code "log"});
     *        must not be {@code null}
     * @param target what to render: the named outcomes, and an optional label naming them; must not
     *        be {@code null}
     * @return the rendered text; never {@code null}
     * @throws BratException if {@code kind} or {@code target} is {@code null}, if no rendering is
     *         known for {@code kind}, or if producing it fails
     */
    String render(String kind, RenderTarget target);
}
