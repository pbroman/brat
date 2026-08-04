package dev.pbroman.brat.core.api.resolver;

import dev.pbroman.brat.core.data.Condition;
import dev.pbroman.brat.core.exception.BratException;

/**
 * Resolves a {@link Condition} to a verdict.
 * <p>
 * Deliberately <em>not</em> the same contract as {@link ConditionResolverRule}: a rule may decline a
 * condition that is not its to answer, while a resolver is whatever ultimately produces the verdict
 * and therefore has nowhere to pass it on to. It answers or it throws.
 */
public interface ConditionResolver {

    /**
     * Resolves a {@link Condition}, testing whether {@code a func b} holds.
     *
     * @param condition the condition to resolve; never {@code null}
     * @return whether the condition holds
     * @throws BratException if {@code condition} is {@code null}, if nothing can resolve the func
     *         for these operands, or if resolving it fails
     */
    boolean resolve(Condition condition);

}
