package dev.pbroman.brat.core.resolver.condition.rules;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.pbroman.brat.core.api.resolver.ConditionPredicate;

import static dev.pbroman.brat.core.util.Constants.BOOLEAN_CONDITION;
import static dev.pbroman.brat.core.util.Constants.IS_FALSE;
import static dev.pbroman.brat.core.util.Constants.IS_TRUE;

/**
 * Core resolver for boolean conditions.
 */
public final class BooleanConditionResolverRule extends AbstractConditionResolverRule {

    /**
     * Constructs the boolean condition rule with its predicates.
     */
    public BooleanConditionResolverRule() {
        super(predicates());
    }

    private static Map<String, ConditionPredicate> predicates() {
        var predicates = new HashMap<String, ConditionPredicate>();
        predicates.put(IS_TRUE, (a, b, args) -> parse(a));
        predicates.put(IS_FALSE, (a, b, args) -> !parse(a));
        return predicates;
    }

    private static Boolean parse(Object value) {
        return Boolean.valueOf(String.valueOf(value));
    }

    @Override
    protected String category() {
        return BOOLEAN_CONDITION;
    }

    @Override
    protected List<String> ignoreBNullCheck() {
        return List.of(IS_TRUE, IS_FALSE);
    }
}
