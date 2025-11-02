package dev.pbroman.brat.core.resolver.condition.rules;

import static dev.pbroman.brat.core.util.CheckUtils.checkCondition;
import static dev.pbroman.brat.core.util.Constants.IS_PREFIX;
import static dev.pbroman.brat.core.util.Constants.NEGATION_PATTERN;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import org.apache.commons.lang3.StringUtils;

import dev.pbroman.brat.core.api.resolver.ConditionResolverRule;
import dev.pbroman.brat.core.data.Condition;
import dev.pbroman.brat.core.data.result.ValidationType;
import dev.pbroman.brat.core.exception.ValidationException;

public abstract class AbstractConditionResolverRule implements ConditionResolverRule {

    protected Map<String, BiFunction<Object, Object, Boolean>> functionMap;

    protected boolean negate = false;

    protected String function;

    /**
     * Constructor initializing the function map.
     */
    protected AbstractConditionResolverRule() {
        functionMap = new HashMap<>();
        initFunctionMap();
        extendFunctionMap();
    }

    @Override
    public Boolean resolve(Condition condition) throws ValidationException {
        checkCondition(condition);
        prepare(condition);
        if (functionMap.containsKey(function)) {
            nullCheckB(condition);
            return negate != functionMap.get(function)
                    .apply(condition.getA(), condition.getB());
        }
        return null;
    }

    /**
     * Implement this to initialize the function map.
     */
    protected abstract void initFunctionMap();

    /**
     * Override this method to extend the function map.
     */
    protected void extendFunctionMap() {
    }

    /**
     * Override this to return a list of functions that does not need the b argument.
     * @return a list of functions ignored by this method
     */
    protected List<String> ignoreBNullCheck() {
        return List.of();
    }

    protected void prepare(Condition condition) {
        function = condition.getFunc().toLowerCase().trim();
        var matches = NEGATION_PATTERN.matcher(function);
        if (matches.find()) {
            function = matches.group(2);
            negate = true;
        }
        if (StringUtils.startsWithIgnoreCase(function, IS_PREFIX)) {
            function = StringUtils.substring(function, IS_PREFIX.length());
        }
    }

    protected void nullCheckB(Condition condition) throws ValidationException {
        if (condition.getB() == null && !ignoreBNullCheck().contains(function)) {
            throw new ValidationException(String.format("b may not be null for %s function '%s'", category(), function),
                    ValidationType.FAIL);
        }
    }
}
