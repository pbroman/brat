package dev.pbroman.brat.core.resolver.condition.rules;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import dev.pbroman.brat.core.api.resolver.ConditionPredicate;

import dev.pbroman.brat.core.data.Condition;

import static dev.pbroman.brat.core.util.Constants.BETWEEN;
import static dev.pbroman.brat.core.util.Constants.CLOSE_TO;
import static dev.pbroman.brat.core.util.Constants.EQUAL_TO;
import static dev.pbroman.brat.core.util.Constants.ARG_MAX;
import static dev.pbroman.brat.core.util.Constants.ARG_MIN;
import static dev.pbroman.brat.core.util.Constants.ARG_OFFSET;
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

    /**
     * Constructs the number condition rule with its predicates.
     */
    public NumberConditionResolverRule() {
        super(predicates());
    }

    private static Map<String, ConditionPredicate> predicates() {
        var predicates = new HashMap<String, ConditionPredicate>();
        ConditionPredicate equalTo = (a, b, args) -> parse(a).compareTo(parse(b)) == 0;
        ConditionPredicate greaterThan = (a, b, args) -> parse(a).compareTo(parse(b)) > 0;
        ConditionPredicate lessThan = (a, b, args) -> parse(a).compareTo(parse(b)) < 0;
        ConditionPredicate greaterOrEqual = (a, b, args) -> parse(a).compareTo(parse(b)) >= 0;
        ConditionPredicate lessOrEqual = (a, b, args) -> parse(a).compareTo(parse(b)) <= 0;
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
        predicates.put(BETWEEN, (a, b, args) -> {
            rejectUnknownArgs(args, ARG_MIN, ARG_MAX);
            var value = parse(a);
            return value.compareTo(parse(requiredArg(args, ARG_MIN))) >= 0
                    && value.compareTo(parse(requiredArg(args, ARG_MAX))) <= 0;
        });
        predicates.put(CLOSE_TO, (a, b, args) -> {
            rejectUnknownArgs(args, ARG_OFFSET);
            return parse(a).subtract(parse(b)).abs().compareTo(parse(requiredArg(args, ARG_OFFSET))) <= 0;
        });
        return predicates;
    }

    /**
     * Parses as an exact decimal rather than a binary double. Every value reaches BRAT as text, so
     * there is no precision to recover by going through {@code double} — and plenty to lose: as
     * doubles {@code |0.51 - 0.5|} is {@code 0.010000000000000009}, which would make
     * {@code isCloseTo} with a {@code 0.01} offset reject the very comparison it was written for.
     * <p>
     * Comparison is always {@link BigDecimal#compareTo}, never {@link BigDecimal#equals}, so
     * {@code 1.50} and {@code 1.5} are equal despite differing in scale.
     * <p>
     * Stricter than {@code Double.valueOf} in one way that matters: {@code NaN} and {@code Infinity}
     * are not decimals, so a condition over them is declined and falls through to the string rule.
     */
    private static BigDecimal parse(Object value) {
        return new BigDecimal(String.valueOf(value).trim());
    }

    @Override
    public String category() {
        return NUMBER_CONDITION;
    }

    @Override
    public int priority() {
        return 20;
    }

    /**
     * {@code isBetween} carries its bounds in {@code params}, so it has no {@code b} to check.
     */
    @Override
    protected List<String> ignoreBNullCheck() {
        return List.of(BETWEEN);
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
