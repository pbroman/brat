package dev.pbroman.brat.core.resolver.condition.rules;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

import static dev.pbroman.brat.core.util.Constants.BLANK;
import static dev.pbroman.brat.core.util.Constants.CONTAINS;
import static dev.pbroman.brat.core.util.Constants.EMPTY;
import static dev.pbroman.brat.core.util.Constants.ENDS_WITH;
import static dev.pbroman.brat.core.util.Constants.EQUALS;
import static dev.pbroman.brat.core.util.Constants.EQUALS_IGNORE_CASE;
import static dev.pbroman.brat.core.util.Constants.MATCHES;
import static dev.pbroman.brat.core.util.Constants.NULL;
import static dev.pbroman.brat.core.util.Constants.STARTS_WITH;
import static dev.pbroman.brat.core.util.Constants.STRING_CONDITION;

/**
 * Core resolver for string conditions.
 */
public final class StringConditionResolverRule extends AbstractConditionResolverRule {

    public StringConditionResolverRule() {
        super(predicates());
    }

    private static Map<String, BiPredicate<Object, Object>> predicates() {
        var predicates = new HashMap<String, BiPredicate<Object, Object>>();
        predicates.put(NULL, (a, b) -> NULL.equals(parse(a)));
        predicates.put(EQUALS, (a, b) -> parse(a).equals(parse(b)));
        predicates.put(EQUALS_IGNORE_CASE, (a, b) -> parse(a).equalsIgnoreCase(parse(b)));
        predicates.put(BLANK, (a, b) -> parse(a).isBlank());
        predicates.put(EMPTY, (a, b) -> parse(a).isEmpty());
        predicates.put(CONTAINS, (a, b) -> parse(a).contains(parse(b)));
        predicates.put(STARTS_WITH, (a, b) -> parse(a).startsWith(parse(b)));
        predicates.put(ENDS_WITH, (a, b) -> parse(a).endsWith(parse(b)));
        predicates.put(MATCHES, (a, b) -> parse(a).matches(parse(b)));
        return predicates;
    }

    private static String parse(Object value) {
        return String.valueOf(value);
    }

    @Override
    public String category() {
        return STRING_CONDITION;
    }

    @Override
    protected List<String> ignoreBNullCheck() {
        return List.of(NULL, EMPTY, BLANK);
    }

}
