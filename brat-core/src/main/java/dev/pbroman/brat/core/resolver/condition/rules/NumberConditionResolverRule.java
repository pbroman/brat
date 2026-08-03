package dev.pbroman.brat.core.resolver.condition.rules;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiPredicate;

import dev.pbroman.brat.core.data.Condition;

import static dev.pbroman.brat.core.util.Constants.EQUAL_TO;
import static dev.pbroman.brat.core.util.Constants.GREATER_THAN;
import static dev.pbroman.brat.core.util.Constants.GREATER_THAN_OR_EQUAL_TO;
import static dev.pbroman.brat.core.util.Constants.LESS_THAN;
import static dev.pbroman.brat.core.util.Constants.LESS_THAN_OR_EQUAL_TO;
import static dev.pbroman.brat.core.util.Constants.NUMBER_CONDITION;
import static dev.pbroman.brat.core.util.Constants.SYMBOL_EQUAL_TO;
import static dev.pbroman.brat.core.util.Constants.SYMBOL_GREATER_THAN;
import static dev.pbroman.brat.core.util.Constants.SYMBOL_GREATER_THAN_OR_EQUAL_TO;
import static dev.pbroman.brat.core.util.Constants.SYMBOL_LESS_THAN;
import static dev.pbroman.brat.core.util.Constants.SYMBOL_LESS_THAN_OR_EQUAL_TO;

/**
 * Core resolver for number conditions.
 */
public final class NumberConditionResolverRule extends AbstractConditionResolverRule {

    public NumberConditionResolverRule() {
        super(predicates());
    }

    private static Map<String, BiPredicate<Object, Object>> predicates() {
        var predicates = new HashMap<String, BiPredicate<Object, Object>>();
        BiPredicate<Object, Object> equalTo = (a, b) -> parse(a).equals(parse(b));
        BiPredicate<Object, Object> greaterThan = (a, b) -> parse(a).compareTo(parse(b)) > 0;
        BiPredicate<Object, Object> lessThan = (a, b) -> parse(a).compareTo(parse(b)) < 0;
        BiPredicate<Object, Object> greaterOrEqual = (a, b) -> parse(a).compareTo(parse(b)) >= 0;
        BiPredicate<Object, Object> lessOrEqual = (a, b) -> parse(a).compareTo(parse(b)) <= 0;
        predicates.put(EQUAL_TO, equalTo);
        predicates.put(SYMBOL_EQUAL_TO, equalTo);
        predicates.put(GREATER_THAN, greaterThan);
        predicates.put(SYMBOL_GREATER_THAN, greaterThan);
        predicates.put(LESS_THAN, lessThan);
        predicates.put(SYMBOL_LESS_THAN, lessThan);
        predicates.put(GREATER_THAN_OR_EQUAL_TO, greaterOrEqual);
        predicates.put(SYMBOL_GREATER_THAN_OR_EQUAL_TO, greaterOrEqual);
        predicates.put(LESS_THAN_OR_EQUAL_TO, lessOrEqual);
        predicates.put(SYMBOL_LESS_THAN_OR_EQUAL_TO, lessOrEqual);
        return predicates;
    }

    private static Double parse(Object value) {
        return Double.valueOf(String.valueOf(value));
    }

    @Override
    public String category() {
        return NUMBER_CONDITION;
    }

    @Override
    public int priority() {
        return 20;
    }

    @Override
    protected boolean accepts(Condition condition) {
        return isNumber(condition.getA()) && (condition.getB() == null || isNumber(condition.getB()));
    }

    private static boolean isNumber(Object value) {
        try {
            parse(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

}
