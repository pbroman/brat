package dev.pbroman.brat.core.api.resolver;

import java.util.Optional;

import dev.pbroman.brat.core.data.Condition;
import dev.pbroman.brat.core.exception.BratException;

/**
 * One pluggable rule of the condition-resolving chain: it answers the funcs it knows, for the
 * operands it can handle, and declines everything else.
 * <p>
 * Unlike the other rule interfaces in BRAT, this does not extend the capability interface it serves
 * ({@link ConditionResolver}), because the two contracts genuinely differ: a rule may decline, and
 * whatever finally produces a verdict may not.
 */
public interface ConditionResolverRule {

    /**
     * Resolves a {@link Condition} if it is this rule's to resolve.
     * <p>
     * <strong>Func names are normalised before matching</strong>, so one predicate answers to several
     * spellings: matching is case-insensitive, a leading {@code is} is optional, and a leading
     * {@code not} or {@code !} negates the result. The {@code is} prefix is stripped on either side
     * of the negation, so {@code isNotEqualTo} and {@code notIsEqualTo} both reach the
     * {@code equalTo} predicate negated.
     * <p>
     * <strong>An implementation declines by returning an empty {@link Optional}</strong>, in either
     * of two cases: it does not know the func at all, or it knows the func but the operands do not
     * belong to its category — {@code isEqualTo} on two values that are not numbers is not the
     * number rule's to answer. Declining lets the dispatcher try the next rule, which is what makes
     * one AssertJ-style name usable across categories. The type-tagged spellings ({@code =} numeric,
     * {@code equals} string, {@code equal} date) stay available for an author who needs to force one
     * comparison.
     * <p>
     * Declining is not error handling: throw when the func is one this rule owns and resolving it
     * genuinely failed, or the failure will be silently passed to the next rule.
     *
     * @param condition the condition to resolve; never {@code null}
     * @return the verdict, or {@link Optional#empty()} if this rule declined the condition
     * @throws BratException if {@code condition} is {@code null}, or if the func is one this rule
     *         owns and resolving it fails for a reason other than the operands' category
     */
    Optional<Boolean> resolve(Condition condition);

    /**
     * Returns the priority of the resolver. Resolvers with higher priority are executed before ones with lower.
     * <p>
     * Priorities 0-100 are reserved for the core code.
     * <p>
     * Priority also decides which rule gets first refusal on a func that several categories answer
     * to, such as {@code isEqualTo}. The core order is null, date, number, string — string last,
     * since every value has a string form and it would otherwise never decline.
     *
     * @return the priority
     */
    default int priority() {
        return 0;
    }

    /**
     * A placeholder string for the condition function category (e.g. Number, String ...).
     *
     * @return the placeholder
     */
    String category();

}
