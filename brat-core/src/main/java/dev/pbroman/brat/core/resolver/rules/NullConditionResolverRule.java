package dev.pbroman.brat.core.resolver.rules;

import static dev.pbroman.brat.core.util.Constants.IS_PREFIX;
import static dev.pbroman.brat.core.util.Constants.NULL;

import org.apache.commons.lang3.StringUtils;

import dev.pbroman.brat.core.api.resolver.ConditionResolverRule;
import dev.pbroman.brat.core.data.Condition;

public class NullConditionResolverRule implements ConditionResolverRule {

    @Override
    public int priority() {
        return Integer.MAX_VALUE - 100;
    }

    /**
     * If a is null, this resolver returns true if func is either 'null' or 'isNull', otherwise false.
     *
     * @param condition the {@link Condition}
     * @return the result of the null check of a, or null, if a is not null.
     */
    @Override
    public Boolean resolve(Condition condition) {
        if (condition.getA() == null) {
            return NULL.equals(condition.getFunc())
                    || StringUtils.equalsIgnoreCase(IS_PREFIX + NULL, condition.getFunc());
        }
        return null;
    }
}
