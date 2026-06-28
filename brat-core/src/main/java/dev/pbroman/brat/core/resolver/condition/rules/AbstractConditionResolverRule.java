package dev.pbroman.brat.core.resolver.condition.rules;

import static dev.pbroman.brat.core.util.CheckUtils.checkCondition;
import static dev.pbroman.brat.core.util.Constants.IS_PREFIX;
import static dev.pbroman.brat.core.util.Constants.NEGATION_PATTERN;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

import org.apache.commons.lang3.StringUtils;

import dev.pbroman.brat.core.api.resolver.ConditionResolverRule;
import dev.pbroman.brat.core.data.Condition;
import dev.pbroman.brat.core.exception.BratException;
import org.apache.commons.lang3.Strings;

/**
 * Abstract implementation of the {@link ConditionResolverRule} providing basic functionality.
 */
public abstract class AbstractConditionResolverRule implements ConditionResolverRule {

    private final Map<String, BiPredicate<Object, Object>> predicateMap;

    /**
     * Constructor initializing the function map.
     */
    protected AbstractConditionResolverRule() {
        var map = new HashMap<String, BiPredicate<Object, Object>>();
        initPredicateMap(map);
        this.predicateMap = Map.copyOf(map);
    }

    @Override
    public Boolean resolve(Condition condition) {
        checkCondition(condition);
        var prepared = prepare(condition.getFunc());
        if (predicateMap.containsKey(prepared.function())) {
            nullCheckB(condition, prepared.function());
            try {
                return prepared.negate() != predicateMap.get(prepared.function())
                        .test(condition.getA(), condition.getB());
            } catch (RuntimeException re) {
                throw new BratException(String.format("Unable to resolve condition %s", condition), re);
            }
        }
        return null;
    }

    /**
     * Implement this to initialize the function map.
     */
    protected abstract void initPredicateMap(Map<String, BiPredicate<Object, Object>> predicateMap);

    /**
     * Override this to return a list of functions that do not require the b argument.
     * @return a list of function names skipped by the null check
     */
    protected List<String> ignoreBNullCheck() {
        return List.of();
    }

    private PreparedFunction prepare(String func) {
        var f = func.toLowerCase().trim();
        var matches = NEGATION_PATTERN.matcher(f);
        boolean negate = false;
        if (matches.find()) {
            f = matches.group(2);
            negate = true;
        }
        if (Strings.CI.startsWith(f, IS_PREFIX)) {
            f = StringUtils.substring(f, IS_PREFIX.length());
        }
        return new PreparedFunction(f, negate);
    }

    private void nullCheckB(Condition condition, String function) {
        if (condition.getB() == null && !ignoreBNullCheck().contains(function)) {
            throw new BratException(String.format("b may not be null for %s function '%s'", category(), function));
        }
    }

    record PreparedFunction(String function, boolean negate) {}
}
