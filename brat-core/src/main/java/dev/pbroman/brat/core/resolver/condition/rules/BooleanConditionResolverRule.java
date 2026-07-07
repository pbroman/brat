package dev.pbroman.brat.core.resolver.condition.rules;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

import static dev.pbroman.brat.core.util.Constants.BOOLEAN_CONDITION;
import static org.apache.commons.lang3.BooleanUtils.FALSE;
import static org.apache.commons.lang3.BooleanUtils.TRUE;

/**
 * Core resolver for boolean conditions.
 */
public final class BooleanConditionResolverRule extends AbstractConditionResolverRule {

    public BooleanConditionResolverRule() {
        super(predicates());
    }

    private static Map<String, BiPredicate<Object, Object>> predicates() {
        var predicates = new HashMap<String, BiPredicate<Object, Object>>();
        predicates.put(TRUE, (a, b) -> parse(a));
        predicates.put(FALSE, (a, b) -> !parse(a));
        return predicates;
    }

    private static Boolean parse(Object value) {
        return Boolean.valueOf(String.valueOf(value));
    }

    @Override
    public String category() {
        return BOOLEAN_CONDITION;
    }

    @Override
    protected List<String> ignoreBNullCheck() {
        return List.of(TRUE, FALSE);
    }

}
