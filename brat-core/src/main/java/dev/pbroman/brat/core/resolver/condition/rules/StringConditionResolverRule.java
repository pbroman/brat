package dev.pbroman.brat.core.resolver.condition.rules;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.pbroman.brat.core.api.resolver.ConditionPredicate;
import dev.pbroman.brat.core.exception.BratException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

import static dev.pbroman.brat.core.util.Constants.BLANK;
import static dev.pbroman.brat.core.util.Constants.CONTAINS;
import static dev.pbroman.brat.core.util.Constants.CONTAINS_IGNORING_CASE;
import static dev.pbroman.brat.core.util.Constants.EMPTY;
import static dev.pbroman.brat.core.util.Constants.ENDS_WITH;
import static dev.pbroman.brat.core.util.Constants.EQUALS;
import static dev.pbroman.brat.core.util.Constants.EQUALS_IGNORE_CASE;
import static dev.pbroman.brat.core.util.Constants.EQUAL_TO;
import static dev.pbroman.brat.core.util.Constants.EQUAL_TO_IGNORING_CASE;
import static dev.pbroman.brat.core.util.Constants.MATCHES;
import static dev.pbroman.brat.core.util.Constants.MEDIA_TYPE;
import static dev.pbroman.brat.core.util.Constants.NULL;
import static dev.pbroman.brat.core.util.Constants.STARTS_WITH;
import static dev.pbroman.brat.core.util.Constants.STRING_CONDITION;

/**
 * Core resolver for string conditions: it compares the text of its operands.
 * <p>
 * <strong>It answers only for a scalar {@code a}.</strong> For an {@code a} that is a {@link List} or a
 * {@link Map}, every func here throws a {@link BratException} naming the func and whether {@code a}
 * was a sequence or a mapping, never its content. The structural funcs that share a
 * name with a string func ({@code isEqualTo}, {@code contains}, {@code isEmpty}) are answered for a
 * structure by the JSON rule, which is offered a condition first; what reaches this rule with a
 * structure is a func only text has.
 * <p>
 * {@code isNull} is the one exception: a structure is never null, so it answers {@code false}, and
 * {@code isNotNull} {@code true}.
 * <p>
 * {@code b} is not checked: it is read as text whatever it is.
 */
public final class StringConditionResolverRule extends AbstractConditionResolverRule {

    /**
     * Constructs the string condition rule with its predicates.
     */
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

        predicates.replaceAll(
                (function, predicate) -> NULL.equals(function) ? predicate : textOnly(function, predicate));
        return predicates;
    }

    /**
     * Wraps a predicate so that it refuses a structured {@code a} before reading it as text.
     *
     * @param function the normalised func, named in the error
     * @param predicate the predicate to guard
     * @return the guarded predicate
     */
    private static ConditionPredicate textOnly(String function, ConditionPredicate predicate) {
        return (a, b, args) -> {
            requireText(a, function);
            return predicate.test(a, b, args);
        };
    }

    /**
     * Throws if {@code a} is a sequence or a mapping, naming {@code function} and which of the two it
     * was — never its content, which may hold a secret.
     *
     * @param a the subject of the condition
     * @param function the normalised func
     * @throws BratException if {@code a} is a {@link List} or a {@link Map}
     */
    private static void requireText(Object a, String function) {
        if (a instanceof List || a instanceof Map) {
            var kind = a instanceof List ? "a sequence" : "a mapping";
            throw new BratException(
                    StringUtils.capitalize(subject(STRING_CONDITION, function)) + " needs text, but a was " + kind
                            + "; a JSON array or object is checked with the structural funcs");
        }
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
    protected String category() {
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
