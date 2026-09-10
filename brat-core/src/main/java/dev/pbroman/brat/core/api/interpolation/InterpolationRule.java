package dev.pbroman.brat.core.api.interpolation;

import java.util.Optional;

import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;

/**
 * A single, priority-dispatched rule resolving one {@code ${...}} token — the pluggable unit
 * {@code InterpolationRuleDispatcher} dispatches tokens to.
 * <p>
 * Deliberately <strong>not</strong> an {@link Interpolation}: a rule may decline a token and an
 * {@link Interpolation} may not, so the two do not share a return type. This mirrors
 * {@code ConditionResolverRule}, which is not a {@code ConditionResolver} for the same reason.
 */
public interface InterpolationRule {

    /**
     * Resolves one token, or declines it.
     * <p>
     * <strong>A rule sees exactly one token</strong> — {@code ${vars.name}}, delimiters included —
     * never the field around it. Splicing a resolved value back into surrounding text belongs to the
     * scanner.
     * <p>
     * <strong>One token is not the same as one token you can parse.</strong> A token is delimited by
     * counting braces, so {@code ${imap.${vars.folder}}} arrives as a single token holding another
     * one. Nesting is resolved for a <em>function call's arguments</em> — the evaluator hands each
     * argument back through interpolation — and nowhere else: a namespace key has no sub-expression
     * position and nothing resolves the inner token first. <strong>The default answer for a token
     * holding another token is therefore to decline it</strong>, so that the field passes through as
     * written rather than being claimed by whichever rule's pattern matched it first.
     * {@code AbstractInterpolationRule} applies that test in {@code claims} for the rules extending
     * it; <strong>implementing this interface directly means applying it yourself</strong>
     * ({@code TokenScanner.holdsNestedToken}), which is part of taking on the claiming
     * decision. A rule wanting a computed key opts in deliberately and owns what one means — core
     * hands it no resolved inner value. The supported alternative is a {@code BratFunction}, whose
     * arguments already recurse.
     * <p>
     * <strong>Declining is explicit.</strong> Return {@link Optional#empty()} for a token that is not
     * this rule's, and the dispatcher tries the next rule. Returning a present outcome claims the
     * token: <strong>the dispatcher stops there and no lower-priority rule is consulted</strong>. A
     * rule must therefore not return a present outcome merely to pass a token through unchanged —
     * that would silence every rule beneath it.
     * <p>
     * <strong>Declining means "someone below should handle this", not "I failed".</strong> Both are
     * legitimate, and which one a missing value calls for depends on whether a rule below yours will
     * plausibly claim the token:
     * <ul>
     *   <li><strong>Delegating is fine.</strong> A rule registered at a higher priority for a
     *       namespace another rule already serves may handle the keys it cares about and decline the
     *       rest, which then reach the rule below. This is how a namespace is partially overridden.</li>
     *   <li><strong>The last rule recognizing a namespace must answer.</strong> If nothing below
     *       claims the token, an unclaimed token is <em>passed through as literal text</em> — the
     *       dispatcher does not treat it as an error, so that a body containing {@code ${HOME}}
     *       survives. A terminal rule that declines therefore does not report a failure, it hides
     *       one: the field silently keeps the token's own text.</li>
     * </ul>
     * This is why the core namespace rules answer a missing key rather than declining it — with a
     * substituted value, an empty string, or a thrown {@link BratException}, whichever that
     * namespace's policy is.
     *
     * @param input the token to resolve, delimiters included; never {@code null}
     * @param runtimeData the namespaces to resolve against
     * @return the outcome, if this rule recognizes {@code input}; {@link Optional#empty()} if it does
     *         not. Never {@code null}
     * @throws BratException if {@code input} is {@code null}, or if this rule recognizes the token
     *         and resolving it failed — a malformed expression, an unreachable service, or a missing
     *         value the namespace treats as an error. Declining is not a way to report a failure: a
     *         rule that declines a token it owns hides the failure, because the dispatcher moves on
     */
    Optional<InterpolationOutcome> outcome(String input, RuntimeData runtimeData);

    /**
     * Returns the priority of the rule. Rules with higher priority are consulted before ones with
     * lower priority.
     * <p>
     * Priority decides <strong>who gets first refusal</strong>, and only matters where two rules
     * recognize the same token: the higher-priority one is asked first, and if it answers, the other
     * is never called. To override a core namespace, recognize its token and return an outcome from a
     * rule with a higher priority. To add a namespace nothing else claims, priority is irrelevant —
     * no other rule recognizes the token, so yours is reached wherever it sits.
     * <p>
     * Priorities 0-100 are reserved for the core code.
     *
     * @return the priority
     */
    default int priority() {
        return 0;
    }
}
