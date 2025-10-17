package dev.pbroman.brat.core.resolver.condition.rules;

import static dev.pbroman.brat.core.util.Constants.IS_PREFIX;
import static dev.pbroman.brat.core.util.Constants.NEGATION_PATTERN;

import java.util.List;

import org.apache.commons.lang3.StringUtils;

import dev.pbroman.brat.core.api.resolver.ConditionResolverRule;
import dev.pbroman.brat.core.data.Condition;

public abstract class AbstractConditionResolverRule implements ConditionResolverRule {

    protected boolean negate = false;

    protected String function;

    /**
     * Override this method to extend the function map.
     */
    protected void extendFunctionMap() {
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

    protected void nullCheckB(Condition condition, List<String> ignoredFunctions) {
        if (condition.getB() == null && !ignoredFunctions.contains(function)) {
            throw new IllegalArgumentException("b may not be null for func: " + function);
        }
    }
}
