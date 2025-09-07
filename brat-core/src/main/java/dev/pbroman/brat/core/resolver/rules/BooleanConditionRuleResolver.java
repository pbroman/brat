package dev.pbroman.brat.core.resolver.rules;

import static org.apache.commons.lang3.BooleanUtils.FALSE;
import static org.apache.commons.lang3.BooleanUtils.TRUE;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import dev.pbroman.brat.core.data.Condition;

public class BooleanConditionRuleResolver extends AbstractConditionResolverRule {

    protected Map<String, Function<Boolean, Boolean>> functionMap;

    public BooleanConditionRuleResolver() {
        initFunctionMap();
    }

    @SuppressWarnings("rawtypes")
    private void initFunctionMap() {
        functionMap = new HashMap<>();
        functionMap.put(TRUE, a -> a);
        functionMap.put(FALSE, a -> !a);
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
                    .apply(convertToBoolean(condition.getA()));
        }
        return null;
    }

    private Boolean convertToBoolean(Object value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case Boolean b -> b;
            case String s -> Boolean.valueOf(s);
            default -> null;
        };
    }

}
