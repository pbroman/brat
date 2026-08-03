package dev.pbroman.brat.core.resolver.condition.rules;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

import dev.pbroman.brat.core.data.Condition;

import static dev.pbroman.brat.core.util.Constants.AFTER;
import static dev.pbroman.brat.core.util.Constants.BEFORE;
import static dev.pbroman.brat.core.util.Constants.DATE_CONDITION;
import static dev.pbroman.brat.core.util.Constants.EQUAL;
import static dev.pbroman.brat.core.util.Constants.EQUAL_TO;
import static dev.pbroman.brat.core.util.Constants.FUTURE;
import static dev.pbroman.brat.core.util.Constants.PAST;

/**
 * Core resolver for date conditions.
 */
public final class DateConditionResolverRule extends AbstractConditionResolverRule {

    private final DateTimeFormatter dateTimeFormatter;

    private static final String DEFAULT_DATE_FORMAT = "[yyyy-MM-dd][dd.MM.yyyy][MM/dd/yyyy]";

    /**
     * Default constructor with a default {@link DateTimeFormatter}.
     */
    public DateConditionResolverRule() {
        this(DateTimeFormatter.ofPattern(DEFAULT_DATE_FORMAT));
    }

    /**
     * Constructor receiving a {@link DateTimeFormatter}.
     */
    public DateConditionResolverRule(DateTimeFormatter dateTimeFormatter) {
        super(predicates(dateTimeFormatter));
        this.dateTimeFormatter = dateTimeFormatter;
    }

    private static Map<String, BiPredicate<Object, Object>> predicates(DateTimeFormatter dateTimeFormatter) {
        var predicates = new HashMap<String, BiPredicate<Object, Object>>();
        predicates.put(BEFORE, (a, b) -> parse(a, dateTimeFormatter).isBefore(parse(b, dateTimeFormatter)));
        predicates.put(AFTER, (a, b) -> parse(a, dateTimeFormatter).isAfter(parse(b, dateTimeFormatter)));
        BiPredicate<Object, Object> equalTo = (a, b) -> parse(a, dateTimeFormatter).isEqual(parse(b, dateTimeFormatter));
        predicates.put(EQUAL, equalTo);
        predicates.put(EQUAL_TO, equalTo);
        predicates.put(PAST, (a, b) -> parse(a, dateTimeFormatter).isBefore(LocalDate.now()));
        predicates.put(FUTURE, (a, b) -> parse(a, dateTimeFormatter).isAfter(LocalDate.now()));
        return predicates;
    }

    private static LocalDate parse(Object value, DateTimeFormatter dateTimeFormatter) {
        return LocalDate.parse(String.valueOf(value), dateTimeFormatter);
    }

    @Override
    public String category() {
        return DATE_CONDITION;
    }

    @Override
    public int priority() {
        return 30;
    }

    @Override
    protected boolean accepts(Condition condition) {
        return isDate(condition.getA()) && (condition.getB() == null || isDate(condition.getB()));
    }

    private boolean isDate(Object value) {
        try {
            parse(value, dateTimeFormatter);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    @Override
    protected List<String> ignoreBNullCheck() {
        return List.of(PAST, FUTURE);
    }

}
