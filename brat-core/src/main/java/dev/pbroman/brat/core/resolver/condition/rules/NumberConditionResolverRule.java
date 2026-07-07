package dev.pbroman.brat.core.resolver.condition.rules;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiPredicate;

import static dev.pbroman.brat.core.util.Constants.EQUAL_TO;
import static dev.pbroman.brat.core.util.Constants.GREATER_THAN;
import static dev.pbroman.brat.core.util.Constants.GREATER_THAN_OR_EQUAL;
import static dev.pbroman.brat.core.util.Constants.LESS_THAN;
import static dev.pbroman.brat.core.util.Constants.LESS_THAN_OR_EQUAL;
import static dev.pbroman.brat.core.util.Constants.NUMBER_CONDITION;

/**
 * Core resolver for number conditions.
 */
public final class NumberConditionResolverRule extends AbstractConditionResolverRule {

    public NumberConditionResolverRule() {
        super(predicates());
    }

    private static Map<String, BiPredicate<Object, Object>> predicates() {
        var predicates = new HashMap<String, BiPredicate<Object, Object>>();
        predicates.put(EQUAL_TO, (a, b) -> parse(a).equals(parse(b)));
        predicates.put(GREATER_THAN, (a, b) -> parse(a).compareTo(parse(b)) > 0);
        predicates.put(LESS_THAN, (a, b) -> parse(a).compareTo(parse(b)) < 0);
        predicates.put(GREATER_THAN_OR_EQUAL, (a, b) -> parse(a).compareTo(parse(b)) >= 0);
        predicates.put(LESS_THAN_OR_EQUAL, (a, b) -> parse(a).compareTo(parse(b)) <= 0);
        return predicates;
    }

    private static Double parse(Object value) {
        return Double.valueOf(String.valueOf(value));
    }

    @Override
    public String category() {
        return NUMBER_CONDITION;
    }

}
