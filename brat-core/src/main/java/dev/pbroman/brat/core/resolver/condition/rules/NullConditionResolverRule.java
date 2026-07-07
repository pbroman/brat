package dev.pbroman.brat.core.resolver.condition.rules;

import dev.pbroman.brat.core.api.resolver.ConditionResolverRule;
import dev.pbroman.brat.core.data.Condition;
import org.apache.commons.lang3.Strings;

import static dev.pbroman.brat.core.util.Constants.IS_PREFIX;
import static dev.pbroman.brat.core.util.Constants.NULL;
import static dev.pbroman.brat.core.util.Constants.NULL_CONDITION;

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
     * If {@code a} is null, this resolver returns true if func is either 'null' or 'isNull', otherwise false.
     *
     * @param condition the {@link Condition}
     * @return the result of the null check of {@code a}, or null, if {@code a} is not null.
     */
    @Override
    public Boolean resolve(Condition condition) {
        if (condition.getA() == null) {
            return NULL.equals(condition.getFunc())
                    || Strings.CI.startsWith(IS_PREFIX + NULL, condition.getFunc());
        }
        return null;
    }
}
