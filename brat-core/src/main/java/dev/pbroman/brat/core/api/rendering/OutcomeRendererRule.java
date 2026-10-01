package dev.pbroman.brat.core.api.rendering;

import java.util.Optional;

import dev.pbroman.brat.core.exception.BratException;

/**
 * One pluggable rule of the outcome-rendering chain: it renders the kinds it knows, and declines
 * every other.
 * <p>
 * It does not extend {@link OutcomeRenderer}, because the two contracts differ: a rule may decline,
 * and whatever finally produces the rendering may not.
 */
public interface OutcomeRendererRule {

    /**
     * Renders {@code target} if {@code kind} is this rule's to render.
     * <p>
     * <strong>An implementation declines by returning an empty {@link Optional}</strong> when it does
     * not know {@code kind}, which lets the dispatcher try the next rule. Declining is not error
     * handling: when the kind is this rule's and rendering fails, throw, or the failure is silently
     * passed to the next rule.
     * <p>
     * Every outcome is shown by its
     * {@link dev.pbroman.brat.core.api.interpolation.InterpolationOutcome#reportingString()
     * reportingString}, never by its value, so a secret appears masked.
     *
     * @param kind the render kind; never {@code null}
     * @param target what to render; never {@code null}
     * @return the rendered text, or {@link Optional#empty()} if this rule declined {@code kind}
     * @throws BratException if {@code kind} is this rule's and rendering {@code target} fails
     */
    Optional<String> render(String kind, RenderTarget target);

    /**
     * Returns the priority of the rule. Rules with higher priority are executed before ones with
     * lower.
     * <p>
     * Priorities 0-100 are reserved for the core code; a rule above 100 recognizing a core kind
     * replaces the core rendering of it.
     *
     * @return the priority
     */
    default int priority() {
        return 0;
    }
}
