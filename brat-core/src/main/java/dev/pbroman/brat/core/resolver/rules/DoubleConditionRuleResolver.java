package dev.pbroman.brat.core.resolver.rules;

import static dev.pbroman.brat.core.util.Constants.EQUAL_TO;
import static dev.pbroman.brat.core.util.Constants.GREATER_THAN;
import static dev.pbroman.brat.core.util.Constants.GREATER_THAN_OR_EQUAL;
import static dev.pbroman.brat.core.util.Constants.LESS_THAN;
import static dev.pbroman.brat.core.util.Constants.LESS_THAN_OR_EQUAL;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

import dev.pbroman.brat.core.data.Condition;

public class DoubleConditionRuleResolver extends AbstractConditionResolverRule {

    protected Map<String, BiFunction<Double, Double, Boolean>> functionMap;

    public DoubleConditionRuleResolver() {
        initFunctionMap();
    }

    @SuppressWarnings("rawtypes")
    private void initFunctionMap() {
        functionMap = new HashMap<>();
        functionMap.put(EQUAL_TO, Double::equals);
        functionMap.put(GREATER_THAN, (a, b) -> a.compareTo(b) > 0);
        functionMap.put(LESS_THAN, (a, b) -> a.compareTo(b) < 0);
        functionMap.put(GREATER_THAN_OR_EQUAL, (a, b) -> a.compareTo(b) >= 0);
        functionMap.put(LESS_THAN_OR_EQUAL, (a, b) -> a.compareTo(b) <= 0);
        extendFunctionMap();
    }

    /**
     * Override this method to extend the function map.
     */
    protected void extendFunctionMap() {
    }

    @Override
    public Boolean resolve(Condition condition) {
        prepare(condition);
        if (functionMap.containsKey(function)) {
            return negate != functionMap.get(function)
                    .apply(convertToDouble(condition.getA()), convertToDouble(condition.getB()));
        }
        return null;
    }

    private Double convertToDouble(Object value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case Double d -> d;
            case String s -> Double.parseDouble(s);
            case Integer i -> Double.valueOf(i);
            default -> throw new IllegalArgumentException("Unsupported number a: " + value);
        };
    }

}
