package dev.pbroman.brat.core.resolver.rules;

import static dev.pbroman.brat.core.util.Constants.BLANK;
import static dev.pbroman.brat.core.util.Constants.CONTAINS;
import static dev.pbroman.brat.core.util.Constants.EMPTY;
import static dev.pbroman.brat.core.util.Constants.ENDS_WITH;
import static dev.pbroman.brat.core.util.Constants.EQUALS;
import static dev.pbroman.brat.core.util.Constants.EQUALS_IGNORE_CASE;
import static dev.pbroman.brat.core.util.Constants.MATCHES;
import static dev.pbroman.brat.core.util.Constants.NULL;
import static dev.pbroman.brat.core.util.Constants.STARTS_WITH;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;

import dev.pbroman.brat.core.data.Condition;

public class StringConditionRuleResolver extends AbstractConditionResolverRule {

    protected Map<String, BiFunction<String, String, Boolean>> functionMap;

    public StringConditionRuleResolver() {
        initFunctionMap();
    }

    @SuppressWarnings("rawtypes")
    private void initFunctionMap() {
        functionMap = new HashMap<>();
        functionMap.put(NULL, (a, b) -> false);
        functionMap.put(EQUALS, Object::equals);
        functionMap.put(EQUALS_IGNORE_CASE, String::equalsIgnoreCase);
        functionMap.put(BLANK, (a, b) -> a.isBlank());
        functionMap.put(EMPTY, (a, b) -> a.isEmpty());
        functionMap.put(CONTAINS, (a, b) -> a.contains(Objects.requireNonNull(b)));
        functionMap.put(STARTS_WITH, (a, b) -> a.startsWith(Objects.requireNonNull(b)));
        functionMap.put(ENDS_WITH, (a, b) -> a.endsWith(Objects.requireNonNull(b)));
        functionMap.put(MATCHES, (a, b) -> a.matches(Objects.requireNonNull(b)));
        extendFunctionMap();
    }

    @Override
    public Boolean resolve(Condition condition) {
        prepare(condition);

        if (functionMap.containsKey(function)) {
            nullCheckB(condition, List.of(NULL, EMPTY, BLANK));
            return negate != functionMap.get(function)
                    .apply(String.valueOf(condition.getA()), String.valueOf(condition.getB()));
        }
        return null;
    }

}
