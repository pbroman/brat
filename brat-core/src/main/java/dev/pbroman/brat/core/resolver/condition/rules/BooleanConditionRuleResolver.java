package dev.pbroman.brat.core.resolver.condition.rules;

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

    @Override
    public Boolean resolve(Condition condition) {
        prepare(condition);
        if (functionMap.containsKey(function)) {
            return negate != functionMap.get(function)
                    .apply(Boolean.valueOf(String.valueOf(condition.getA())));
        }
        return null;
    }

}
