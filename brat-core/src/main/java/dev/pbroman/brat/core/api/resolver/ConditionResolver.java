package dev.pbroman.brat.core.api.resolver;

import dev.pbroman.brat.core.data.Condition;
import dev.pbroman.brat.core.exception.BratException;

/**
 * Main interface for all condition resolvers.
 */
public interface ConditionResolver {

    /**
     * Resolves a {@link Condition}, testing whether {@code a func b} holds.
     * <p>
     * <strong>Func names are normalised before matching</strong>, so one predicate answers to several
     * spellings: matching is case-insensitive, a leading {@code is} is optional, and a leading
     * {@code not} or {@code !} negates the result. The {@code is} prefix is stripped <em>before</em>
     * negation is matched, so {@code isNotEqualTo} and {@code notEqualTo} both reach the
     * {@code equalTo} predicate negated.
     * <p>
     * <strong>An implementation declines by returning {@code null}</strong>, in either of two cases:
     * it does not know the func at all, or it knows the func but the operands do not belong to its
     * category — {@code isEqualTo} on two values that are not numbers is not the number rule's to
     * answer. Declining lets the dispatcher try the next rule, which is what makes one AssertJ-style
     * name usable across categories. The type-tagged spellings ({@code =} numeric, {@code equals}
     * string, {@code equal} date) stay available for an author who needs to force one comparison.
     *
     * @param condition the condition to resolve; never {@code null}
     * @return {@code true} or {@code false} if this implementation resolved the condition,
     *         {@code null} if it declined it
     * @throws BratException if {@code condition} is {@code null}, or if the func is one this
     *         implementation owns and resolving it fails for a reason other than the operands'
     *         category
     */
    Boolean resolve(Condition condition);

}