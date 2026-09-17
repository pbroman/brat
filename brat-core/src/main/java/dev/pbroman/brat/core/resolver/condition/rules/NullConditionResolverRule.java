package dev.pbroman.brat.core.resolver.condition.rules;

import java.util.Optional;

import dev.pbroman.brat.core.api.resolver.ConditionResolverRule;
import dev.pbroman.brat.core.data.Condition;

import static dev.pbroman.brat.core.util.Constants.IS_PREFIX;
import static dev.pbroman.brat.core.util.Constants.NULL;
import static dev.pbroman.brat.core.util.Constants.NULL_CONDITION;
import static dev.pbroman.brat.core.util.Require.nonNull;

/**
 * Core resolver for null conditions.
 * <p>
 * This resolver has the exceptional priority of {@code Integer.MAX_VALUE - 100}, since it must have priority over all
 * other resolvers but still should be possible to override.
 */
public final class NullConditionResolverRule implements ConditionResolverRule {

    @Override
    public int priority() {
        return Integer.MAX_VALUE - 100;
    }

    @Override
    public String category() {
        return NULL_CONDITION;
    }

    /**
     * If {@code a} is null, this resolver returns true if func is either {@code null} or
     * {@code isNull}, matched ignoring case, and false for every other func — including one that
     * merely starts the same way, such as {@code is}.
     * <p>
     * Claiming every func rather than declining the ones it does not know is deliberate: with
     * {@code a} null there is nothing for a later rule to compare, so {@code isEqualTo} against a
     * null is a failed assertion rather than an unresolvable one.
     *
     * @param condition the {@link Condition}
     * @return the result of the null check of {@code a}, or empty if {@code a} is not null
     */
    @Override
    public Optional<Boolean> resolve(Condition condition) {
        nonNull(condition, "The condition may not be null");
        if (condition.getA() == null) {
            var func = condition.getFunc();
            return Optional.of(NULL.equalsIgnoreCase(func) || (IS_PREFIX + NULL).equalsIgnoreCase(func));
        }
        return Optional.empty();
    }
}
