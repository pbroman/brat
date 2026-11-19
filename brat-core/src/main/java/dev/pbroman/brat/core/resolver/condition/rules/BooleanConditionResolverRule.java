package dev.pbroman.brat.core.resolver.condition.rules;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.pbroman.brat.core.api.resolver.ConditionPredicate;
import dev.pbroman.brat.core.exception.BratException;
import org.apache.commons.lang3.StringUtils;

import static dev.pbroman.brat.core.util.Constants.BOOLEAN_CONDITION;
import static dev.pbroman.brat.core.util.Constants.IS_FALSE;
import static dev.pbroman.brat.core.util.Constants.IS_TRUE;

/**
 * Core resolver for boolean conditions: {@code isTrue} and {@code isFalse}.
 * <p>
 * {@code a} must be a boolean: a {@link Boolean}, or the text {@code true} or {@code false} in any
 * case, which is what an interpolated token yields. Anything else is an error rather than a
 * {@code false} — {@code yes}, {@code 1}, {@code ""}, a number, a sequence or a mapping — because no
 * other rule answers these funcs, so declining would only end in a vaguer failure. The message names
 * the func and, for a structure, whether it was a sequence or a mapping; it never quotes {@code a}.
 */
public final class BooleanConditionResolverRule extends AbstractConditionResolverRule {

    /**
     * Constructs the boolean condition rule with its predicates.
     */
    public BooleanConditionResolverRule() {
        super(predicates());
    }

    private static Map<String, ConditionPredicate> predicates() {
        var predicates = new HashMap<String, ConditionPredicate>();
        predicates.put(IS_TRUE, (a, b, args) -> parse(a, IS_TRUE));
        predicates.put(IS_FALSE, (a, b, args) -> !parse(a, IS_FALSE));
        return predicates;
    }

    /**
     * Reads {@code value} as a boolean, as the class documents it.
     *
     * @param value {@code a}, never {@code null}
     * @param func the func's canonical name, for the message
     * @return the boolean
     * @throws BratException if {@code value} is not a boolean
     */
    private static boolean parse(Object value, String func) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            if (Boolean.TRUE.toString().equalsIgnoreCase(text)) {
                return true;
            }
            if (Boolean.FALSE.toString().equalsIgnoreCase(text)) {
                return false;
            }
        }
        throw new BratException(StringUtils.capitalize(subject(BOOLEAN_CONDITION, func))
                + " needs a boolean, true or false, but a was " + kindOf(value));
    }

    /** What {@code value} is, for a message that must not quote it. */
    private static String kindOf(Object value) {
        return switch (value) {
            case List<?> list -> "a sequence";
            case Map<?, ?> map -> "a mapping";
            case String text -> "other text";
            default -> "a " + value.getClass().getSimpleName();
        };
    }

    @Override
    protected String category() {
        return BOOLEAN_CONDITION;
    }

    @Override
    protected List<String> ignoreBNullCheck() {
        return List.of(IS_TRUE, IS_FALSE);
    }
}
