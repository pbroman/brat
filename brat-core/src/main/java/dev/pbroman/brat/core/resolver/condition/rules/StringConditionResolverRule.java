package dev.pbroman.brat.core.resolver.condition.rules;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import dev.pbroman.brat.core.api.resolver.ConditionPredicate;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

import static dev.pbroman.brat.core.util.Constants.BLANK;
import static dev.pbroman.brat.core.util.Constants.CONTAINS;
import static dev.pbroman.brat.core.util.Constants.CONTAINS_IGNORING_CASE;
import static dev.pbroman.brat.core.util.Constants.EMPTY;
import static dev.pbroman.brat.core.util.Constants.ENDS_WITH;
import static dev.pbroman.brat.core.util.Constants.EQUALS;
import static dev.pbroman.brat.core.util.Constants.EQUAL_TO;
import static dev.pbroman.brat.core.util.Constants.EQUAL_TO_IGNORING_CASE;
import static dev.pbroman.brat.core.util.Constants.EQUALS_IGNORE_CASE;
import static dev.pbroman.brat.core.util.Constants.MATCHES;
import static dev.pbroman.brat.core.util.Constants.MEDIA_TYPE;
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

    private static Map<String, ConditionPredicate> predicates() {
        var predicates = new HashMap<String, ConditionPredicate>();
        predicates.put(NULL, (a, b, args) -> NULL.equals(parse(a)));
        ConditionPredicate equalTo = (a, b, args) -> parse(a).equals(parse(b));
        ConditionPredicate equalToIgnoringCase = (a, b, args) -> parse(a).equalsIgnoreCase(parse(b));
        predicates.put(EQUAL_TO, equalTo);
        predicates.put(EQUALS, equalTo);
        predicates.put(EQUAL_TO_IGNORING_CASE, equalToIgnoringCase);
        predicates.put(EQUALS_IGNORE_CASE, equalToIgnoringCase);
        predicates.put(BLANK, (a, b, args) -> parse(a).isBlank());
        predicates.put(EMPTY, (a, b, args) -> parse(a).isEmpty());
        predicates.put(CONTAINS, (a, b, args) -> parse(a).contains(parse(b)));
        predicates.put(CONTAINS_IGNORING_CASE, (a, b, args) -> Strings.CI.contains(parse(a), parse(b)));
        predicates.put(MEDIA_TYPE, (a, b, args) -> mediaTypeOf(a).equalsIgnoreCase(mediaTypeOf(b)));
        predicates.put(STARTS_WITH, (a, b, args) -> parse(a).startsWith(parse(b)));
        predicates.put(ENDS_WITH, (a, b, args) -> parse(a).endsWith(parse(b)));
        predicates.put(MATCHES, (a, b, args) -> parse(a).matches(parse(b)));
        return predicates;
    }

    private static String parse(Object value) {
        return String.valueOf(value);
    }

    /**
     * The media type without its parameters, so {@code application/json; charset=utf-8} compares
     * equal to {@code application/json}. Applied to both operands, so either may carry parameters.
     */
    private static String mediaTypeOf(Object value) {
        return StringUtils.substringBefore(parse(value), ";").trim();
    }

    @Override
    public String category() {
        return STRING_CONDITION;
    }

    /**
     * The lowest core priority: every value has a string form, so this rule never declines and must
     * be the last of the categories to be offered a condition.
     */
    @Override
    public int priority() {
        return 10;
    }

    @Override
    protected List<String> ignoreBNullCheck() {
        return List.of(NULL, EMPTY, BLANK);
    }

}
