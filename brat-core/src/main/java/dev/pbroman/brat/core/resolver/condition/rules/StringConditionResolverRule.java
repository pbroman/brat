package dev.pbroman.brat.core.resolver.condition.rules;

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

import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

public class StringConditionResolverRule extends AbstractConditionResolverRule {

    public StringConditionResolverRule() {
        super();
    }

    @Override
    public String category() {
        return STRING_CONDITION;
    }

    @Override
    protected void initPredicateMap(Map<String, BiPredicate<Object, Object>> predicateMap) {
        predicateMap.put(NULL, (a, b) -> NULL.equals(parse(a)));
        predicateMap.put(EQUALS, (a, b) -> parse(a).equals(parse(b)));
        predicateMap.put(EQUALS_IGNORE_CASE, (a, b) -> parse(a).equalsIgnoreCase(parse(b)));
        predicateMap.put(BLANK, (a, b) -> parse(a).isBlank());
        predicateMap.put(EMPTY, (a, b) -> parse(a).isEmpty());
        predicateMap.put(CONTAINS, (a, b) -> parse(a).contains(parse(b)));
        predicateMap.put(STARTS_WITH, (a, b) -> parse(a).startsWith(parse(b)));
        predicateMap.put(ENDS_WITH, (a, b) -> parse(a).endsWith(parse(b)));
        predicateMap.put(MATCHES, (a, b) -> parse(a).matches(parse(b)));
    }

    @Override
    protected List<String> ignoreBNullCheck() {
        return List.of(NULL, EMPTY, BLANK);
    }

    private String parse(Object value) {
        return String.valueOf(value);
    }

}
